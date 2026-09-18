package server;

import common.message.Message;
import common.message.MessageType;
import common.model.PlayerInfo;
import server.model.Player;
import server.net.ClientHandler;
import server.service.AccountService;
import server.service.MatchManager;
import server.service.PersistenceService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameServer {
    public static final int PORT = 5555;

    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final PersistenceService persistence = new PersistenceService("data");
    private final AccountService accounts = new AccountService(persistence);
    private final MatchManager matchManager = new MatchManager(persistence, accounts, this::broadcastOnlineList);

    private final Map<String, String> pendingInvites = new HashMap<>();

    public MatchManager getMatchManager() { return matchManager; }

    public static void main(String[] args) throws IOException {
        new GameServer().start();
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("=== Barricade Game Server dang chay tai cong " + PORT + " ===");
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket, this);
                pool.execute(handler);
            }
        }
    }

    // ---------- LOGIN / LOGOUT ----------

    public synchronized void handleLogin(ClientHandler handler, String username, String password) {
        Player[] box = new Player[1];
        AccountService.LoginResult result = accounts.login(username, password, box);
        Message reply = new Message(MessageType.LOGIN_RESULT);
        switch (result) {
            case WRONG_PASSWORD:
                reply.put("success", Boolean.FALSE).put("message", "Sai mat khau.");
                handler.send(reply);
                return;
            case ALREADY_ONLINE:
                reply.put("success", Boolean.FALSE).put("message", "Tai khoan dang online o noi khac.");
                handler.send(reply);
                return;
            case OK:
                Player player = box[0];
                player.handler = handler;
                handler.setPlayer(player);
                reply.put("success", Boolean.TRUE).put("message", "Dang nhap thanh cong")
                        .put("onlineList", buildOnlineList());
                handler.send(reply);

                if (player.currentMatch != null) {
                    matchManager.handleReconnect(player);
                }
                broadcastOnlineList();
        }
    }

    public synchronized void handleLogout(ClientHandler handler) {
        Player p = handler.getPlayer();
        if (p == null) return;
        p.status = "OFFLINE";
        p.handler = null;
        p.currentMatch = null;
        broadcastOnlineList();
    }

    public synchronized void handleDisconnect(ClientHandler handler) {
        Player p = handler.getPlayer();
        if (p == null) return;
        matchManager.handleDisconnect(p);
        broadcastOnlineList();
    }

    private List<PlayerInfo> buildOnlineList() {
        List<PlayerInfo> list = new ArrayList<>();
        for (Player p : accounts.all().values()) {
            if (p.handler != null) list.add(p.toInfo());
        }
        return list;
    }

    private void broadcastOnlineList() {
        Message m = new Message(MessageType.ONLINE_LIST_UPDATE).put("onlineList", buildOnlineList());
        for (Player p : accounts.all().values()) {
            if (p.handler != null) p.handler.send(m);
        }
    }

    // ---------- MOI DAU ----------

    public synchronized void handleInvite(Player from, String targetUsername) {
        Player target = accounts.get(targetUsername);
        if (target == null || target.handler == null || !"ONLINE".equals(target.status)) {
            if (from.handler != null) {
                from.handler.send(new Message(MessageType.INVITE_FAILED).put("reason", "Nguoi choi khong san sang."));
            }
            return;
        }
        pendingInvites.put(target.username, from.username);
        target.handler.send(new Message(MessageType.INVITE_RECEIVED).put("fromUsername", from.username));
    }

    public synchronized void handleInviteResponse(Player target, String fromUsername, boolean accept) {
        String expected = pendingInvites.remove(target.username);
        if (expected == null || !expected.equals(fromUsername)) return;

        Player from = accounts.get(fromUsername);
        if (from == null || from.handler == null) return;

        if (!accept) {
            from.handler.send(new Message(MessageType.INVITE_DECLINED).put("byUsername", target.username));
            return;
        }
        if (!"ONLINE".equals(from.status) || !"ONLINE".equals(target.status)) {
            from.handler.send(new Message(MessageType.INVITE_FAILED).put("reason", "Mot trong hai nguoi da vao tran khac."));
            return;
        }
        matchManager.createMatch(from, target);
        broadcastOnlineList();
    }

    // ---------- LICH SU / XEP HANG ----------

    public void handleGetHistory(Player player) {
        if (player == null || player.handler == null) return;
        List<String> history = persistence.loadHistoryFor(player.username);
        player.handler.send(new Message(MessageType.HISTORY_RESULT).put("historyList", history));
    }

    public void handleGetRanking(ClientHandler handler) {
        List<Player> all = new ArrayList<>(accounts.all().values());
        all.sort((a, b) -> {
            if (b.score != a.score) return b.score - a.score;
            if (b.wins != a.wins) return b.wins - a.wins;
            return b.gamesPlayed - a.gamesPlayed;
        });
        List<PlayerInfo> ranking = new ArrayList<>();
        for (Player p : all) ranking.add(p.toInfo());
        handler.send(new Message(MessageType.RANKING_RESULT).put("rankingList", ranking));
    }
}