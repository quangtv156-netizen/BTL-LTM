package server.service;

import common.message.Message;
import common.message.MessageType;
import server.model.Board;
import server.model.Match;
import server.model.Player;

import java.util.Map;
import java.util.concurrent.*;

public class MatchManager {
    public static final int TURN_SECONDS = 15;
    public static final int RECONNECT_SECONDS = 15;
    public static final int REMATCH_SECONDS = 15;

    private final Map<String, Match> matches = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final PersistenceService persistence;
    private final AccountService accounts;
    private final Runnable onOnlineListChanged;
    private int matchCounter = 0;

    public MatchManager(PersistenceService persistence, AccountService accounts, Runnable onOnlineListChanged) {
        this.persistence = persistence;
        this.accounts = accounts;
        this.onOnlineListChanged = onOnlineListChanged;
    }

    public Match getMatch(String matchId) {
        return matches.get(matchId);
    }

    public synchronized Match createMatch(Player p1, Player p2) {
        String id = "M" + (++matchCounter) + "-" + System.currentTimeMillis();
        Match match = new Match(id, p1, p2);
        matches.put(id, match);
        p1.status = "BUSY"; p2.status = "BUSY";
        p1.currentMatch = match; p2.currentMatch = match;

        sendMatchStart(match, p1, 1);
        sendMatchStart(match, p2, 2);
        scheduleTurnTimer(match);
        return match;
    }

    private void sendMatchStart(Match match, Player p, int symbol) {
        if (p.handler == null) return;
        Player opp = match.getOpponent(p);
        Message m = new Message(MessageType.MATCH_START)
                .put("matchId", match.matchId)
                .put("opponent", opp.username)
                .put("yourSymbol", symbol)
                .put("firstTurn", match.currentTurn)
                .put("boardState", match.board.toBoardState());
        p.handler.send(m);
    }

    // ---------- TURN TIMER ----------

    private void scheduleTurnTimer(Match match) {
        cancelTurnTimer(match);
        match.turnTimerFuture = scheduler.schedule(() -> onTurnTimeout(match), TURN_SECONDS, TimeUnit.SECONDS);
    }

    private void cancelTurnTimer(Match match) {
        if (match.turnTimerFuture != null) match.turnTimerFuture.cancel(false);
    }

    private synchronized void onTurnTimeout(Match match) {
        if (match.finished) return;
        int skipped = match.currentTurn;
        match.switchTurn();
        broadcast(match, new Message(MessageType.TURN_TIMEOUT)
                .put("skippedPlayer", skipped)
                .put("nextTurn", match.currentTurn)
                .put("boardState", match.board.toBoardState()));
        scheduleTurnTimer(match);
    }

    // ---------- NUOC DI ----------

    public synchronized void handleMovePawn(Player player, String matchId, int row, int col) {
        Match match = matches.get(matchId);
        if (match == null) return;
        if (!validTurn(match, player)) {
            sendInvalid(player, match, "Chua den luot cua ban.");
            return;
        }
        int num = match.getPlayerNumber(player);
        Board b = match.board;
        int fr = num == 1 ? b.p1Row : b.p2Row;
        int fc = num == 1 ? b.p1Col : b.p2Col;

        if (!b.canMovePawn(fr, fc, row, col)) {
            sendInvalid(player, match, "Nuoc di khong hop le");
            return;
        }
        b.movePawn(num, row, col);
        cancelTurnTimer(match);

        if (b.hasReachedGoal(num)) {
            finishMatch(match, player, "REACH_GOAL");
            return;
        }
        match.switchTurn();
        broadcast(match, new Message(MessageType.MOVE_RESULT)
                .put("valid", true)
                .put("boardState", b.toBoardState())
                .put("nextTurn", match.currentTurn));
        scheduleTurnTimer(match);
    }

    public synchronized void handlePlaceBarricade(Player player, String matchId, int row, int col, String orientation) {
        Match match = matches.get(matchId);
        if (match == null) return;
        if (!validTurn(match, player)) {
            sendInvalid(player, match, "Chua den luot cua ban.");
            return;
        }
        int num = match.getPlayerNumber(player);
        Board b = match.board;

        if (!b.isValidWallPlacement(num, row, col, orientation)) {
            sendInvalid(player, match, "Vi tri barricade khong hop le hoac chan het duong di");
            return;
        }
        b.placeWall(num, row, col, orientation);
        cancelTurnTimer(match);
        match.switchTurn();
        broadcast(match, new Message(MessageType.MOVE_RESULT)
                .put("valid", true)
                .put("boardState", b.toBoardState())
                .put("nextTurn", match.currentTurn));
        scheduleTurnTimer(match);
    }

    private boolean validTurn(Match match, Player player) {
        if (match == null || match.finished) return false;
        int num = match.getPlayerNumber(player);
        if (num == -1) return false;
        return match.currentTurn == num;
    }

    private void sendInvalid(Player player, Match match, String reason) {
        if (player.handler != null) {
            player.handler.send(new Message(MessageType.MOVE_RESULT)
                    .put("valid", false)
                    .put("reason", reason)
                    .put("boardState", match.board.toBoardState())
                    .put("nextTurn", match.currentTurn));
        }
    }

    // ---------- DAU HANG ----------

    public synchronized void handleSurrender(Player player, String matchId) {
        Match match = matches.get(matchId);
        if (match == null || match.finished) return;
        Player winner = match.getOpponent(player);
        finishMatch(match, winner, "SURRENDER");
    }

    // ---------- KET THUC TRAN ----------

    private synchronized void finishMatch(Match match, Player winner, String reason) {
        match.finished = true;
        cancelTurnTimer(match);
        Player loser = match.getOpponent(winner);

        winner.score += 1;
        winner.wins += 1;
        winner.gamesPlayed += 1;
        loser.gamesPlayed += 1;
        accounts.persist();
        persistence.appendHistory(match.player1.username, match.player2.username, winner.username);

        broadcast(match, new Message(MessageType.GAME_OVER)
                .put("winner", winner.username)
                .put("reason", reason)
                .put("boardState", match.board.toBoardState()));

        askRematch(match);
    }

    private void askRematch(Match match) {
        broadcast(match, new Message(MessageType.REMATCH_REQUEST));
        match.rematchTimerFuture = scheduler.schedule(() -> onRematchTimeout(match), REMATCH_SECONDS, TimeUnit.SECONDS);
    }

    public synchronized void handleRematchResponse(Player player, boolean accept) {
        Match match = player.currentMatch;
        if (match == null) return;
        int num = match.getPlayerNumber(player);
        if (num == 1) { match.p1Responded = true; match.p1WantsRematch = accept; }
        else { match.p2Responded = true; match.p2WantsRematch = accept; }

        if (!accept) {
            closeMatch(match);
            return;
        }
        if (match.p1Responded && match.p2Responded && match.p1WantsRematch && match.p2WantsRematch) {
            if (match.rematchTimerFuture != null) match.rematchTimerFuture.cancel(false);
            match.resetForRematch();
            sendMatchStart(match, match.player1, 1);
            sendMatchStart(match, match.player2, 2);
            scheduleTurnTimer(match);
        }
    }

    private synchronized void onRematchTimeout(Match match) {
        if (!(match.p1Responded && match.p1WantsRematch && match.p2Responded && match.p2WantsRematch)) {
            closeMatch(match);
        }
    }

    private void closeMatch(Match match) {
        match.player1.status = "ONLINE"; match.player1.currentMatch = null;
        match.player2.status = "ONLINE"; match.player2.currentMatch = null;
        matches.remove(match.matchId);
        if (match.player1.handler != null) match.player1.handler.send(new Message(MessageType.MATCH_CLOSED));
        if (match.player2.handler != null) match.player2.handler.send(new Message(MessageType.MATCH_CLOSED));
        if (onOnlineListChanged != null) onOnlineListChanged.run();
    }

    // ---------- CHAT ----------

    public void handleChat(Player from, String matchId, String content) {
        Match match = matches.get(matchId);
        if (match == null) return;
        Player opp = match.getOpponent(from);
        if (opp.handler != null) {
            opp.handler.send(new Message(MessageType.CHAT_MESSAGE).put("from", from.username).put("content", content));
        }
    }

    // ---------- MAT KET NOI / RECONNECT ----------

    public synchronized void handleDisconnect(Player player) {
        Match match = player.currentMatch;
        player.handler = null;
        if (match == null || match.finished) {
            player.status = "OFFLINE";
            return;
        }
        int num = match.getPlayerNumber(player);
        if (num == 1) match.player1Disconnected = true; else match.player2Disconnected = true;
        Player opp = match.getOpponent(player);
        if (opp.handler != null) {
            opp.handler.send(new Message(MessageType.DISCONNECT_NOTICE).put("username", player.username));
        }
        match.reconnectTimerFuture = scheduler.schedule(() -> onReconnectTimeout(match, num),
                RECONNECT_SECONDS, TimeUnit.SECONDS);
    }

    private synchronized void onReconnectTimeout(Match match, int disconnectedNum) {
        boolean stillDisconnected = disconnectedNum == 1 ? match.player1Disconnected : match.player2Disconnected;
        if (match.finished || !stillDisconnected) return;
        Player loser = match.getPlayerByNumber(disconnectedNum);
        Player winner = match.getOpponent(loser);
        finishMatch(match, winner, "DISCONNECT_TIMEOUT");
    }

    public synchronized void handleReconnect(Player player) {
        Match match = player.currentMatch;
        if (match == null || match.finished) return;
        int num = match.getPlayerNumber(player);
        if (num == 1) match.player1Disconnected = false; else match.player2Disconnected = false;
        if (match.reconnectTimerFuture != null) match.reconnectTimerFuture.cancel(false);

        player.status = "BUSY";
        sendMatchStart(match, player, num);
        Player opp = match.getOpponent(player);
        if (opp.handler != null) {
            opp.handler.send(new Message(MessageType.RECONNECTED).put("username", player.username));
        }
    }

    // ---------- TIEN ICH ----------

    private void broadcast(Match match, Message m) {
        if (match.player1.handler != null) match.player1.handler.send(m);
        if (match.player2.handler != null) match.player2.handler.send(m);
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}