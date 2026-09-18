package server.model;

import common.model.PlayerInfo;
import server.net.ClientHandler;

public class Player {
    public String username;
    public String password;
    public int score;
    public int wins;
    public int gamesPlayed;
    public volatile String status = "ONLINE"; // ONLINE | BUSY | OFFLINE
    public transient ClientHandler handler; // null neu dang offline (cho reconnect)
    public transient Match currentMatch;

    public Player(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public PlayerInfo toInfo() {
        return new PlayerInfo(username, score, wins, gamesPlayed, status);
    }
}
