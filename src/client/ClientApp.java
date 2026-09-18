package client;

import client.net.ClientConnection;
import client.ui.GameView;
import client.ui.LobbyView;
import client.ui.LoginView;
import common.message.Message;
import common.message.MessageType;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.io.IOException;

public class ClientApp extends Application {
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 5555;

    private ClientConnection connection;
    private String username;

    private LoginView loginView;
    private LobbyView lobbyView;
    private GameView gameView;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        loginView = new LoginView(this);
        loginView.show();
    }

    public void connectAndLogin(String host, int port, String username, String password) {
        try {
            connection = new ClientConnection(host, port);
            connection.setListener(this::onMessage);
            connection.start();
            this.username = username;
            connection.send(new Message(MessageType.LOGIN).put("username", username).put("password", password));
        } catch (IOException e) {
            loginView.showError("Khong the ket noi toi server: " + e.getMessage());
        }
    }

    public String getUsername() { return username; }
    public ClientConnection getConnection() { return connection; }

    private void onMessage(Message msg) {
        if (msg == null) {
            handleDisconnected();
            return;
        }
        switch (msg.type) {
            case LOGIN_RESULT:
                if (msg.getBool("success")) {
                    loginView.close();
                    lobbyView = new LobbyView(this);
                    lobbyView.updateOnlineList(msg.get("onlineList"));
                    lobbyView.show();
                } else {
                    loginView.showError(msg.getString("message"));
                }
                break;
            case ONLINE_LIST_UPDATE:
                if (lobbyView != null) lobbyView.updateOnlineList(msg.get("onlineList"));
                break;
            case INVITE_RECEIVED:
                if (lobbyView != null) lobbyView.onInviteReceived(msg.getString("fromUsername"));
                break;
            case INVITE_DECLINED:
                if (lobbyView != null) lobbyView.onInviteDeclined(msg.getString("byUsername"));
                break;
            case INVITE_FAILED:
                if (lobbyView != null) lobbyView.onInviteFailed(msg.getString("reason"));
                break;
            case MATCH_START:
                onMatchStart(msg);
                break;
            case MOVE_RESULT:
                if (gameView != null) gameView.onMoveResult(msg);
                break;
            case TURN_TIMEOUT:
                if (gameView != null) gameView.onTurnTimeout(msg);
                break;
            case GAME_OVER:
                if (gameView != null) gameView.onGameOver(msg);
                break;
            case REMATCH_REQUEST:
                break;
            case MATCH_CLOSED:
                if (gameView != null) gameView.forceCloseGameOverDialog();
                onMatchEndedReturnToLobby();
                break;
            case CHAT_MESSAGE:
                if (gameView != null) gameView.onChatMessage(msg.getString("from"), msg.getString("content"));
                break;
            case DISCONNECT_NOTICE:
                if (gameView != null) gameView.onOpponentDisconnected(msg.getString("username"));
                break;
            case RECONNECTED:
                if (gameView != null) gameView.onOpponentReconnected(msg.getString("username"));
                break;
            case HISTORY_RESULT:
                if (lobbyView != null) lobbyView.showHistory(msg.get("historyList"));
                break;
            case RANKING_RESULT:
                if (lobbyView != null) lobbyView.showRanking(msg.get("rankingList"));
                break;
            case ERROR:
                Alert alert = new Alert(Alert.AlertType.ERROR, msg.getString("message"), ButtonType.OK);
                alert.setHeaderText(null);
                alert.showAndWait();
                break;
            default:
                // bo qua
        }
    }

    private void onMatchStart(Message msg) {
        String matchId = msg.getString("matchId");
        String opponent = msg.getString("opponent");
        int symbol = msg.getInt("yourSymbol");
        int firstTurn = msg.getInt("firstTurn");
        Object boardState = msg.get("boardState");

        if (gameView == null) {
            gameView = new GameView(this, matchId, opponent, symbol);
            gameView.show();
            if (lobbyView != null) lobbyView.setVisible(false);
        } else {
            gameView.resetForNewMatch(matchId, opponent, symbol);
        }
        gameView.applyBoardState(boardState, firstTurn);
    }

    public void onMatchEndedReturnToLobby() {
        Platform.runLater(this::doReturnToLobby);
    }

    private void doReturnToLobby() {
        if (gameView != null) {
            gameView.close();
            gameView = null;
        }
        if (lobbyView != null) {
            lobbyView.setVisible(true);
            lobbyView.guardAgainstClickThrough();
            lobbyView.requestOnlineListRefresh();
        }
    }

    private void handleDisconnected() {
        Alert alert = new Alert(Alert.AlertType.WARNING, "Mat ket noi toi server.", ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
        System.exit(0);
    }
}