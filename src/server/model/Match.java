package server.model;

import java.util.concurrent.ScheduledFuture;

public class Match {
    public final String matchId;
    public final Player player1; // di truoc
    public final Player player2;
    public final Board board = new Board();
    public int currentTurn = 1; // 1 hoac 2
    public boolean finished = false;

    public volatile boolean player1Disconnected = false;
    public volatile boolean player2Disconnected = false;

    public transient ScheduledFuture<?> turnTimerFuture;
    public transient ScheduledFuture<?> reconnectTimerFuture;
    public transient ScheduledFuture<?> rematchTimerFuture;
    public volatile boolean p1WantsRematch = false;
    public volatile boolean p2WantsRematch = false;
    public volatile boolean p1Responded = false;
    public volatile boolean p2Responded = false;

    public Match(String matchId, Player player1, Player player2) {
        this.matchId = matchId;
        this.player1 = player1;
        this.player2 = player2;
    }

    public Player getPlayerByNumber(int n) {
        return n == 1 ? player1 : player2;
    }

    public int getPlayerNumber(Player p) {
        if (p == player1) return 1;
        if (p == player2) return 2;
        return -1;
    }

    public Player getOpponent(Player p) {
        return p == player1 ? player2 : player1;
    }

    public void switchTurn() {
        currentTurn = currentTurn == 1 ? 2 : 1;
    }

    public void resetForRematch() {
        board.p1Row = 0; board.p1Col = 4;
        board.p2Row = 8; board.p2Col = 4;
        board.p1BarricadesLeft = Board.START_BARRICADES;
        board.p2BarricadesLeft = Board.START_BARRICADES;
        board.clearWalls();
        currentTurn = 1;
        finished = false;
        p1WantsRematch = false; p2WantsRematch = false;
        p1Responded = false; p2Responded = false;
    }
}