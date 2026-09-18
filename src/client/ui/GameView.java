package client.ui;

import client.ClientApp;
import common.message.Message;
import common.message.MessageType;
import common.model.BoardState;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

public class GameView {
    private final ClientApp app;
    private final Stage stage = new Stage();
    private String matchId;
    private String opponent;
    private int mySymbol;
    private int currentTurn;
    private static final int TURN_SECONDS = 15;

    private final BoardCanvas boardCanvas = new BoardCanvas();
    private final Label turnLabel = new Label();
    private final Label timerLabel = new Label();
    private final ProgressBar timerBar = new ProgressBar(1.0);
    private final Label barricadeLabel = new Label();
    private final TextArea chatArea = new TextArea();
    private final TextField chatInput = new TextField();
    private final ToggleGroup modeGroup = new ToggleGroup();
    private final RadioButton moveRadio = new RadioButton("Di chuyen");
    private final RadioButton hRadio = new RadioButton("Dat barricade ngang (H)");
    private final RadioButton vRadio = new RadioButton("Dat barricade doc (V)");

    private Timeline countdownTimeline;
    private int secondsLeft;

    private static final int REMATCH_WAIT_MS = 16000;
    private Timeline pendingReturnTimer;

    private Alert gameOverAlert;
    private boolean matchForceClosed = false;

    private static final String CSS = "style.css";

    public GameView(ClientApp app, String matchId, String opponent, int mySymbol) {
        this.app = app;
        this.matchId = matchId;
        this.opponent = opponent;
        this.mySymbol = mySymbol;
        buildUI();
        boardCanvas.setOnCellClick(this::onCellClicked);
        boardCanvas.setOnWallClick(this::onWallClicked);
        boardCanvas.setMySymbol(mySymbol);

        stage.setOnCloseRequest(this::onCloseRequest);
    }

    private void onCloseRequest(WindowEvent event) {
        event.consume();
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Dong cua so se tinh la dau hang tran nay. Tiep tuc?", ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        alert.setTitle("Xac nhan thoat");
        styleAlert(alert);
        if (alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            app.getConnection().send(new Message(MessageType.SURRENDER).put("matchId", matchId));
            stage.close();
            System.exit(0);
        }
    }

    private void buildUI() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setPadding(new Insets(10));

        turnLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        timerBar.getStyleClass().add("timer-progress");
        timerBar.setPrefWidth(140);
        VBox timerBox = new VBox(4, timerLabel, timerBar);
        timerBox.setAlignment(Pos.CENTER);

        HBox top = new HBox(24, turnLabel, timerBox, barricadeLabel);
        top.setAlignment(Pos.CENTER);
        root.setTop(top);
        BorderPane.setMargin(top, new Insets(0, 0, 10, 0));

        root.setCenter(boardCanvas);

        VBox right = new VBox(10);
        right.setPrefWidth(260);

        moveRadio.setToggleGroup(modeGroup);
        hRadio.setToggleGroup(modeGroup);
        vRadio.setToggleGroup(modeGroup);
        moveRadio.setSelected(true);
        moveRadio.setOnAction(e -> boardCanvas.setMode("MOVE"));
        hRadio.setOnAction(e -> boardCanvas.setMode("H"));
        vRadio.setOnAction(e -> boardCanvas.setMode("V"));

        Button surrenderBtn = new Button("Dau hang");
        surrenderBtn.setOnAction(e -> surrender());

        TitledPane modePane = new TitledPane("Che do thao tac",
                new VBox(6, moveRadio, hRadio, vRadio, surrenderBtn));
        modePane.setCollapsible(false);

        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatInput.setOnAction(e -> sendChat());
        VBox chatBox = new VBox(6, chatArea, chatInput);
        VBox.setVgrow(chatArea, Priority.ALWAYS);
        TitledPane chatPane = new TitledPane("Chat", chatBox);
        chatPane.setCollapsible(false);
        VBox.setVgrow(chatPane, Priority.ALWAYS);

        right.getChildren().addAll(modePane, chatPane);
        root.setRight(right);
        BorderPane.setMargin(right, new Insets(0, 0, 0, 10));

        Label hint = new Label("Che do 'Di chuyen': click o ke ben canh quan de di. Che do Barricade: ren chuot de xem preview, click de dat.");
        root.setBottom(hint);
        BorderPane.setMargin(hint, new Insets(10, 0, 0, 0));

        Scene scene = new Scene(root, 760, 640);
        scene.getStylesheets().add(getClass().getResource(CSS).toExternalForm());
        stage.setScene(scene);
        stage.setTitle("Barricade Game - Dau voi " + opponent);
    }

    public void resetForNewMatch(String matchId, String opponent, int mySymbol) {
        this.matchId = matchId;
        this.opponent = opponent;
        this.mySymbol = mySymbol;
        boardCanvas.setMySymbol(mySymbol);
        stage.setTitle("Barricade Game - Dau voi " + opponent);
        chatArea.clear();
        resetToMoveMode();
        stage.show();
    }

    public void applyBoardState(Object boardStateObj, int firstTurn) {
        BoardState state = (BoardState) boardStateObj;
        boardCanvas.updateState(state);
        currentTurn = firstTurn;
        updateBarricadeLabel(state);
        updateTurnLabel();
        restartCountdown();
        resetToMoveMode();
    }

    private void onCellClicked(int row, int col) {
        if (currentTurn != mySymbol) {
            info("Chua den luot cua ban.");
            return;
        }
        app.getConnection().send(new Message(MessageType.MOVE_PAWN)
                .put("matchId", matchId).put("row", row).put("col", col));
    }

    private void onWallClicked(int row, int col) {
        if (currentTurn != mySymbol) {
            info("Chua den luot cua ban.");
            return;
        }
        String orientation = hRadio.isSelected() ? "H" : "V";
        app.getConnection().send(new Message(MessageType.PLACE_BARRICADE)
                .put("matchId", matchId).put("row", row).put("col", col).put("orientation", orientation));
    }

    private void surrender() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Ban chac chan muon dau hang?", ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        alert.setTitle("Xac nhan");
        styleAlert(alert);
        if (alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            app.getConnection().send(new Message(MessageType.SURRENDER).put("matchId", matchId));
        }
    }

    private void sendChat() {
        String text = chatInput.getText().trim();
        if (text.isEmpty()) return;
        app.getConnection().send(new Message(MessageType.CHAT_MESSAGE).put("matchId", matchId).put("content", text));
        chatArea.appendText("Ban: " + text + "\n");
        chatInput.clear();
    }

    public void onChatMessage(String from, String content) {
        chatArea.appendText(from + ": " + content + "\n");
    }

    public void onMoveResult(Message msg) {
        boolean valid = msg.getBool("valid");
        BoardState state = msg.get("boardState");
        if (state != null) {
            boardCanvas.updateState(state);
            updateBarricadeLabel(state);
        }
        currentTurn = msg.getInt("nextTurn");
        updateTurnLabel();
        restartCountdown();
        resetToMoveMode();

        if (!valid) {
            shakeBoard();
            Alert alert = new Alert(Alert.AlertType.WARNING, msg.getString("reason"), ButtonType.OK);
            alert.setHeaderText(null);
            alert.setTitle("Nuoc di khong hop le");
            styleAlert(alert);
            alert.showAndWait();
        }
    }

    /** Hieu ung rung nhe ban co khi nuoc di / dat barricade khong hop le. */
    private void shakeBoard() {
        TranslateTransition tt = new TranslateTransition(Duration.millis(55), boardCanvas);
        tt.setFromX(0);
        tt.setByX(10);
        tt.setCycleCount(6);
        tt.setAutoReverse(true);
        tt.setOnFinished(e -> boardCanvas.setTranslateX(0));
        tt.play();
    }

    public void onTurnTimeout(Message msg) {
        int skipped = msg.getInt("skippedPlayer");
        chatArea.appendText("[He thong] Nguoi choi " + (skipped == mySymbol ? "ban" : opponent) + " het gio, bi bo luot.\n");
        BoardState state = msg.get("boardState");
        boardCanvas.updateState(state);
        currentTurn = msg.getInt("nextTurn");
        updateTurnLabel();
        restartCountdown();
        resetToMoveMode();
    }

    public void onGameOver(Message msg) {
        stopCountdown();
        boardCanvas.setMyTurn(false);
        String winner = msg.getString("winner");
        boolean iWon = winner.equals(app.getUsername());
        BoardState state = msg.get("boardState");
        if (state != null) boardCanvas.updateState(state);
        resetToMoveMode();

        matchForceClosed = false;
        String resultText = iWon ? "Chuc mung, ban da thang!" : (winner + " da thang tran nay.");
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                resultText + "\nBan co muon choi tiep khong?", ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        alert.setTitle("Ket thuc tran dau");
        styleAlert(alert);
        animateDialogEntrance(alert);
        gameOverAlert = alert;
        boolean accept = alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
        gameOverAlert = null;

        if (matchForceClosed) {
            // Doi thu da tu choi/het gio truoc, server da dong tran roi -> khong gui gi them,
            // de ClientApp tu dua ve sanh.
            return;
        }
        app.getConnection().send(new Message(MessageType.REMATCH_RESPONSE).put("accept", accept));

        if (!accept) {
            turnLabel.setText("Dang quay ve sanh...");
            turnLabel.setTextFill(Color.GRAY);
            timerLabel.setText("");
            return;
        }

        turnLabel.setText("Dang cho doi thu...");
        turnLabel.setTextFill(Color.GRAY);
        timerLabel.setText("");

        pendingReturnTimer = new Timeline(new KeyFrame(Duration.millis(REMATCH_WAIT_MS), e -> app.onMatchEndedReturnToLobby()));
        pendingReturnTimer.setCycleCount(1);
        pendingReturnTimer.play();
    }

    /** Goi tu ClientApp khi nhan MATCH_CLOSED: dong ngay hop thoai rematch dang treo (neu co). */
    public void forceCloseGameOverDialog() {
        matchForceClosed = true;
        if (gameOverAlert != null) {
            gameOverAlert.close();
        }
    }

    /** Hieu ung fade + scale khi hop thoai thang/thua xuat hien. */
    private void animateDialogEntrance(Alert alert) {
        alert.setOnShown(e -> {
            var pane = alert.getDialogPane();
            pane.setOpacity(0);

            FadeTransition ft = new FadeTransition(Duration.millis(260), pane);
            ft.setFromValue(0);
            ft.setToValue(1);

            ScaleTransition st = new ScaleTransition(Duration.millis(260), pane);
            st.setFromX(0.85);
            st.setFromY(0.85);
            st.setToX(1);
            st.setToY(1);

            ft.play();
            st.play();
        });
    }

    private void styleAlert(Alert alert) {
        alert.getDialogPane().getStylesheets().add(getClass().getResource(CSS).toExternalForm());
    }

    public void onOpponentDisconnected(String username) {
        chatArea.appendText("[He thong] " + username + " bi mat ket noi, dang cho ket noi lai (15s)...\n");
    }

    public void onOpponentReconnected(String username) {
        chatArea.appendText("[He thong] " + username + " da ket noi lai.\n");
    }

    private void updateBarricadeLabel(BoardState state) {
        int mine = mySymbol == 1 ? state.p1BarricadesLeft : state.p2BarricadesLeft;
        int theirs = mySymbol == 1 ? state.p2BarricadesLeft : state.p1BarricadesLeft;
        barricadeLabel.setText("Barricade con lai - Ban: " + mine + " | " + opponent + ": " + theirs);
    }

    private void resetToMoveMode() {
        moveRadio.setSelected(true);
        boardCanvas.setMode("MOVE");
    }

    private void updateTurnLabel() {
        if (pendingReturnTimer != null) {
            pendingReturnTimer.stop();
            pendingReturnTimer = null;
        }
        turnLabel.setText(currentTurn == mySymbol ? "LUOT CUA BAN" : "Luot cua " + opponent);
        turnLabel.setTextFill(currentTurn == mySymbol ? Color.rgb(0, 130, 0) : Color.DARKGRAY);
        boardCanvas.setMyTurn(currentTurn == mySymbol);
    }

    private void restartCountdown() {
        stopCountdown();
        secondsLeft = TURN_SECONDS;
        timerLabel.setText("Con lai: " + secondsLeft + "s");
        timerBar.setProgress(1.0);
        updateTimerBarStyle(1.0);
        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            secondsLeft--;
            if (secondsLeft < 0) secondsLeft = 0;
            timerLabel.setText("Con lai: " + secondsLeft + "s");
            double ratio = secondsLeft / (double) TURN_SECONDS;
            timerBar.setProgress(ratio);
            updateTimerBarStyle(ratio);
        }));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    private void updateTimerBarStyle(double ratio) {
        timerBar.getStyleClass().removeAll("timer-progress", "timer-progress-warn", "timer-progress-danger");
        if (ratio > 0.4) {
            timerBar.getStyleClass().add("timer-progress");
        } else if (ratio > 0.15) {
            timerBar.getStyleClass().add("timer-progress-warn");
        } else {
            timerBar.getStyleClass().add("timer-progress-danger");
        }
    }

    private void stopCountdown() {
        if (countdownTimeline != null) countdownTimeline.stop();
    }

    private void info(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setHeaderText(null);
        styleAlert(alert);
        alert.showAndWait();
    }

    public void show() { stage.show(); }
    public void close() { stage.close(); }
}