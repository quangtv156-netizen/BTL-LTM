package server.net;

import common.message.Message;
import common.message.MessageType;
import server.GameServer;
import server.model.Player;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final GameServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Player player; // gan sau khi login thanh cong

    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            Message msg;
            while ((msg = (Message) in.readObject()) != null) {
                try {
                    handle(msg);
                } catch (RuntimeException ex) {
                    // Loi khi xu ly 1 tin nhan khong duoc lam sap ca ket noi - in ra de debug va tiep tuc vong lap.
                    System.err.println("[ClientHandler] Loi xu ly message " + msg.type + " tu "
                            + (player != null ? player.username : "?") + ":");
                    ex.printStackTrace();
                }
            }
        } catch (EOFException | java.net.SocketException e) {
            // client dong ket noi binh thuong hoac dot ngot
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Loi ket noi client: " + e.getMessage());
        } finally {
            onDisconnect();
        }
    }

    private void handle(Message msg) {
        switch (msg.type) {
            case LOGIN:
                server.handleLogin(this, msg.getString("username"), msg.getString("password"));
                break;
            case LOGOUT:
                server.handleLogout(this);
                break;
            case INVITE:
                server.handleInvite(player, msg.getString("targetUsername"));
                break;
            case INVITE_RESPONSE:
                server.handleInviteResponse(player, msg.getString("fromUsername"), msg.getBool("accept"));
                break;
            case MOVE_PAWN:
                server.getMatchManager().handleMovePawn(player, msg.getString("matchId"), msg.getInt("row"), msg.getInt("col"));
                break;
            case PLACE_BARRICADE:
                server.getMatchManager().handlePlaceBarricade(player, msg.getString("matchId"),
                        msg.getInt("row"), msg.getInt("col"), msg.getString("orientation"));
                break;
            case SURRENDER:
                server.getMatchManager().handleSurrender(player, msg.getString("matchId"));
                break;
            case REMATCH_RESPONSE:
                server.getMatchManager().handleRematchResponse(player, msg.getBool("accept"));
                break;
            case CHAT_MESSAGE:
                server.getMatchManager().handleChat(player, msg.getString("matchId"), msg.getString("content"));
                break;
            case GET_HISTORY:
                server.handleGetHistory(player);
                break;
            case GET_RANKING:
                server.handleGetRanking(this);
                break;
            default:
                System.err.println("Message khong duoc xu ly: " + msg.type);
        }
    }

    public void setPlayer(Player p) {
        this.player = p;
    }

    public synchronized void send(Message m) {
        try {
            out.reset(); // tranh cache doi tuong cu cua ObjectOutputStream
            out.writeObject(m);
            out.flush();
        } catch (IOException e) {
            System.err.println("Khong gui duoc message toi " + (player != null ? player.username : "?") + ": " + e.getMessage());
        }
    }

    private void onDisconnect() {
        server.handleDisconnect(this);
        try { socket.close(); } catch (IOException ignored) {}
    }
}