package common.model;

import java.io.Serializable;

/** Thong tin toi thieu ve mot nguoi choi, dung de hien thi danh sach online / xep hang. */
public class PlayerInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    public String username;
    public int score;
    public int wins;
    public int gamesPlayed;
    public String status; // "ONLINE" (ranh) | "BUSY" (dang choi) | "OFFLINE"

    public PlayerInfo(String username, int score, int wins, int gamesPlayed, String status) {
        this.username = username;
        this.score = score;
        this.wins = wins;
        this.gamesPlayed = gamesPlayed;
        this.status = status;
    }

    @Override
    public String toString() {
        return username + "  |  Diem: " + score + "  |  Thang: " + wins
                + "  |  So tran: " + gamesPlayed + "  |  " + status;
    }
}
