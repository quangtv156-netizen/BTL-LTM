package server.service;

import server.model.Player;

import java.sql.*;
import java.util.*;

public class PersistenceService {
    private static final String URL  = "jdbc:mysql://localhost:3306/barricade_game?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String USER = "root";
    private static final String PASS = "Quangtv123@";

    public PersistenceService(String ignored) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        username VARCHAR(50) PRIMARY KEY,
                        password VARCHAR(100) NOT NULL,
                        score INT DEFAULT 0,
                        wins INT DEFAULT 0,
                        games_played INT DEFAULT 0
                    )
                    """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS history (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        played_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        player1 VARCHAR(50) NOT NULL,
                        player2 VARCHAR(50) NOT NULL,
                        winner VARCHAR(50) NOT NULL
                    )
                    """);
            }
        } catch (Exception e) {
            throw new RuntimeException("Khong the ket noi MySQL: " + e.getMessage(), e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    public synchronized Map<String, Player> loadUsers() {
        Map<String, Player> map = new LinkedHashMap<>();
        String sql = "SELECT username, password, score, wins, games_played FROM users";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Player p = new Player(rs.getString("username"), rs.getString("password"));
                p.score = rs.getInt("score");
                p.wins = rs.getInt("wins");
                p.gamesPlayed = rs.getInt("games_played");
                map.put(p.username, p);
            }
        } catch (SQLException e) {
            System.err.println("Loi doc users: " + e.getMessage());
        }
        return map;
    }

    public synchronized void saveAllUsers(Collection<Player> players) {
        String sql = """
            INSERT INTO users (username, password, score, wins, games_played)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                password = VALUES(password),
                score = VALUES(score),
                wins = VALUES(wins),
                games_played = VALUES(games_played)
            """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Player p : players) {
                ps.setString(1, p.username);
                ps.setString(2, p.password);
                ps.setInt(3, p.score);
                ps.setInt(4, p.wins);
                ps.setInt(5, p.gamesPlayed);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            System.err.println("Loi ghi users: " + e.getMessage());
        }
    }

    public synchronized void appendHistory(String player1, String player2, String winner) {
        String sql = "INSERT INTO history (player1, player2, winner) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player1);
            ps.setString(2, player2);
            ps.setString(3, winner);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Loi ghi history: " + e.getMessage());
        }
    }

    public synchronized List<String> loadHistoryFor(String username) {
        List<String> result = new ArrayList<>();
        String sql = """
            SELECT played_at, player1, player2, winner
            FROM history
            WHERE player1 = ? OR player2 = ?
            ORDER BY played_at DESC
            """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, username);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(String.format("[%s] %s vs %s -> Thang: %s",
                            rs.getTimestamp("played_at"),
                            rs.getString("player1"),
                            rs.getString("player2"),
                            rs.getString("winner")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Loi doc history: " + e.getMessage());
        }
        return result;
    }
}