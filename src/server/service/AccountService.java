package server.service;

import server.model.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quan ly tai khoan trong bo nho, dong bo xuong file qua PersistenceService.
 * De don gian cho do an mon hoc: neu username chua ton tai thi tu dong dang ky
 * voi mat khau nhap vao; neu da ton tai thi phai dung mat khau.
 */
public class AccountService {
    private final PersistenceService persistence;
    private final Map<String, Player> accounts; // username -> Player (tat ca tai khoan, ke ca offline)

    public AccountService(PersistenceService persistence) {
        this.persistence = persistence;
        this.accounts = new ConcurrentHashMap<>(persistence.loadUsers());
    }

    public enum LoginResult { OK, WRONG_PASSWORD, ALREADY_ONLINE }

    public synchronized LoginResult login(String username, String password, Player[] outPlayer) {
        Player p = accounts.get(username);
        if (p == null) {
            p = new Player(username, password);
            accounts.put(username, p);
            persistence.saveAllUsers(accounts.values());
        } else {
            if (!p.password.equals(password)) return LoginResult.WRONG_PASSWORD;

            // Chỉ coi là "đang online" khi còn handler thật sự
            // (tránh lỗi status còn ONLINE/BUSY dù đã đăng xuất)
            if (p.handler != null) {
                return LoginResult.ALREADY_ONLINE;
            }
        }
        p.status = "ONLINE";
        outPlayer[0] = p;
        return LoginResult.OK;
    }

    public void persist() {
        persistence.saveAllUsers(accounts.values());
    }

    public Player get(String username) {
        return accounts.get(username);
    }

    public Map<String, Player> all() {
        return accounts;
    }
}
