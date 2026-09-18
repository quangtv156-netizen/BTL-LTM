package client.ui;

import client.ClientApp;
import common.message.Message;
import common.message.MessageType;
import common.model.PlayerInfo;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LobbyView {
    private static final String CSS = "style.css";
    private static final Pattern HISTORY_PATTERN =
            Pattern.compile("^\\[(.+?)\\]\\s*(.+?)\\s+vs\\s+(.+?)\\s*->\\s*Thang:\\s*(.+)$");

    private final ClientApp app;
    private final Stage stage = new Stage();
    private final ListView<PlayerInfo> onlineList = new ListView<>();
    private final Label onlineCountLabel = new Label();
    private final Button inviteBtn = new Button("Thach dau");
    private final Button rankingBtn = new Button("Bang xep hang");
    private final Button historyBtn = new Button("Lich su dau");
    private final Button logoutBtn = new Button("Dang xuat");

    public LobbyView(ClientApp app) {
        this.app = app;
        buildUI();
    }

    private void buildUI() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setPadding(new Insets(16));

        VBox header = new VBox(4);
        Label title = new Label("BARRICADE GAME");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("Xin chao, " + app.getUsername() + "!");
        subtitle.getStyleClass().add("app-subtitle");
        onlineCountLabel.getStyleClass().add("online-count");
        Region divider = new Region();
        divider.getStyleClass().add("section-divider");
        header.getChildren().addAll(title, subtitle, onlineCountLabel, divider);
        VBox.setMargin(divider, new Insets(10, 0, 0, 0));
        root.setTop(header);
        BorderPane.setMargin(header, new Insets(0, 0, 14, 0));

        onlineList.setCellFactory(list -> createPlayerCell());
        root.setCenter(onlineList);
        BorderPane.setMargin(onlineList, new Insets(0, 0, 14, 0));

        inviteBtn.setOnAction(e -> sendInvite());
        rankingBtn.setOnAction(e -> app.getConnection().send(new Message(MessageType.GET_RANKING)));
        historyBtn.setOnAction(e -> app.getConnection().send(new Message(MessageType.GET_HISTORY)));
        logoutBtn.setOnAction(e -> {
            app.getConnection().send(new Message(MessageType.LOGOUT));
            System.exit(0);
        });

        HBox buttons = new HBox(8, inviteBtn, rankingBtn, historyBtn, logoutBtn);
        root.setBottom(buttons);

        Scene scene = new Scene(root, 500, 560);
        scene.getStylesheets().add(getClass().getResource(CSS).toExternalForm());
        stage.setTitle("Barricade Game - San choi (" + app.getUsername() + ")");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> System.exit(0));
    }

    private ListCell<PlayerInfo> createPlayerCell() {
        return new ListCell<>() {
            private final Circle dot = new Circle(5);
            private final Label nameLabel = new Label();
            private final Label statsLabel = new Label();
            private final Region spacer = new Region();
            private final HBox box = new HBox(10, dot, nameLabel, spacer, statsLabel);

            {
                box.setAlignment(Pos.CENTER_LEFT);
                nameLabel.getStyleClass().add("player-name");
                statsLabel.getStyleClass().add("player-stats");
                HBox.setHgrow(spacer, Priority.ALWAYS);
            }

            @Override
            protected void updateItem(PlayerInfo p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) {
                    setGraphic(null);
                    return;
                }
                boolean online = "ONLINE".equals(p.status);
                dot.setFill(online ? Color.web("#6fcf6f") : Color.web("#8a8272"));
                nameLabel.setText(p.username);
                statsLabel.setText("Diem " + p.score + "   |   Thang " + p.wins + "   |   " + p.gamesPlayed + " tran   |   "
                        + (online ? "San sang" : "Dang choi"));
                setGraphic(box);
            }
        };
    }

    private void sendInvite() {
        PlayerInfo selected = onlineList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        if (selected.username.equals(app.getUsername())) {
            info("Ban khong the tu thach dau chinh minh.");
            return;
        }
        if (!"ONLINE".equals(selected.status)) {
            info("Nguoi choi nay dang ban.");
            return;
        }
        app.getConnection().send(new Message(MessageType.INVITE).put("targetUsername", selected.username));
    }

    private void info(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setHeaderText(null);
        styleAlert(alert);
        alert.showAndWait();
    }

    @SuppressWarnings("unchecked")
    public void updateOnlineList(Object onlineListObj) {
        List<PlayerInfo> list = (List<PlayerInfo>) onlineListObj;
        onlineList.getItems().setAll(list);
        onlineCountLabel.setText(list.size() + " nguoi dang truc tuyen");
    }

    public void requestOnlineListRefresh() {
        // server tu broadcast
    }

    public void onInviteReceived(String fromUsername) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                fromUsername + " muon thach dau ban. Chap nhan?", ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        alert.setTitle("Loi moi thach dau");
        styleAlert(alert);
        boolean accept = alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
        app.getConnection().send(new Message(MessageType.INVITE_RESPONSE)
                .put("fromUsername", fromUsername).put("accept", accept));
    }

    public void onInviteDeclined(String byUsername) {
        info(byUsername + " da tu choi loi thach dau.");
    }

    public void onInviteFailed(String reason) {
        Alert alert = new Alert(Alert.AlertType.WARNING, "Khong the thach dau: " + reason, ButtonType.OK);
        alert.setHeaderText(null);
        styleAlert(alert);
        alert.showAndWait();
    }

    // ---------- BANG XEP HANG (TableView) ----------

    @SuppressWarnings("unchecked")
    public void showRanking(Object rankingListObj) {
        List<PlayerInfo> list = (List<PlayerInfo>) rankingListObj;

        TableView<PlayerInfo> table = new TableView<>();
        table.setItems(FXCollections.observableArrayList(list));
        table.setSelectionModel(null);

        TableColumn<PlayerInfo, Integer> rankCol = new TableColumn<>("Hang");
        rankCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(table.getItems().indexOf(cd.getValue()) + 1));
        rankCol.setSortable(false);
        rankCol.setPrefWidth(60);

        TableColumn<PlayerInfo, String> nameCol = new TableColumn<>("Nguoi choi");
        nameCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().username));
        nameCol.setSortable(false);
        nameCol.setPrefWidth(150);

        TableColumn<PlayerInfo, Integer> scoreCol = new TableColumn<>("Diem");
        scoreCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().score));
        scoreCol.setSortable(false);
        scoreCol.setPrefWidth(80);

        TableColumn<PlayerInfo, Integer> winsCol = new TableColumn<>("Thang");
        winsCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().wins));
        winsCol.setSortable(false);
        winsCol.setPrefWidth(80);

        TableColumn<PlayerInfo, Integer> gamesCol = new TableColumn<>("So tran");
        gamesCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().gamesPlayed));
        gamesCol.setSortable(false);
        gamesCol.setPrefWidth(80);

        table.getColumns().addAll(rankCol, nameCol, scoreCol, winsCol, gamesCol);
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(PlayerInfo item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("rank-gold", "rank-silver", "rank-bronze");
                if (!empty) {
                    int idx = getIndex();
                    if (idx == 0) getStyleClass().add("rank-gold");
                    else if (idx == 1) getStyleClass().add("rank-silver");
                    else if (idx == 2) getStyleClass().add("rank-bronze");
                }
            }
        });

        VBox content = new VBox(10);
        content.getStyleClass().add("root");
        content.setPadding(new Insets(16));
        Label header = new Label("BANG XEP HANG");
        header.getStyleClass().add("app-title");
        content.getChildren().add(header);

        if (list.isEmpty()) {
            Label empty = new Label("Chua co du lieu.");
            empty.getStyleClass().add("empty-hint");
            content.getChildren().add(empty);
        } else {
            content.getChildren().add(table);
            VBox.setVgrow(table, Priority.ALWAYS);
        }

        showInfoWindow("Bang xep hang", content, 460, 480);
    }

    // ---------- LICH SU DAU (ListView styled) ----------

    @SuppressWarnings("unchecked")
    public void showHistory(Object historyListObj) {
        List<String> list = (List<String>) historyListObj;

        VBox content = new VBox(10);
        content.getStyleClass().add("root");
        content.setPadding(new Insets(16));
        Label header = new Label("LICH SU DAU");
        header.getStyleClass().add("app-title");
        content.getChildren().add(header);

        if (list.isEmpty()) {
            Label empty = new Label("Ban chua co tran dau nao.");
            empty.getStyleClass().add("empty-hint");
            content.getChildren().add(empty);
        } else {
            ListView<String> listView = new ListView<>(FXCollections.observableArrayList(list));
            listView.setCellFactory(lv -> createHistoryCell());
            content.getChildren().add(listView);
            VBox.setVgrow(listView, Priority.ALWAYS);
        }

        showInfoWindow("Lich su dau", content, 460, 480);
    }

    private ListCell<String> createHistoryCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String line, boolean empty) {
                super.updateItem(line, empty);
                if (empty || line == null) {
                    setGraphic(null);
                    return;
                }
                Matcher m = HISTORY_PATTERN.matcher(line);
                if (!m.matches()) {
                    Label raw = new Label(line);
                    setGraphic(raw);
                    return;
                }
                String time = m.group(1);
                String p1 = m.group(2);
                String p2 = m.group(3);
                String winner = m.group(4);

                String me = app.getUsername();
                String opponent = p1.equals(me) ? p2 : p1;
                boolean won = winner.equals(me);

                Label opponentLabel = new Label("vs " + opponent);
                opponentLabel.getStyleClass().add("history-opponent");

                Label badge = new Label(won ? "THANG" : "THUA");
                badge.getStyleClass().addAll("history-badge", won ? "history-win" : "history-lose");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox topRow = new HBox(8, opponentLabel, spacer, badge);
                topRow.setAlignment(Pos.CENTER_LEFT);

                Label timeLabel = new Label(time);
                timeLabel.getStyleClass().add("history-time");

                VBox box = new VBox(3, topRow, timeLabel);
                setGraphic(box);
            }
        };
    }

    // ---------- CUA SO PHU (dung chung cho Ranking / History) ----------

    private void showInfoWindow(String title, VBox content, double width, double height) {
        Stage popup = new Stage(StageStyle.UTILITY);
        popup.initOwner(stage);
        popup.setTitle(title);

        Button closeBtn = new Button("Dong");
        closeBtn.setOnAction(e -> popup.close());
        HBox footer = new HBox(closeBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(10, 0, 0, 0));
        content.getChildren().add(footer);

        Scene scene = new Scene(content, width, height);
        scene.getStylesheets().add(getClass().getResource(CSS).toExternalForm());
        popup.setScene(scene);
        popup.show();
    }

    private void styleAlert(Alert alert) {
        alert.getDialogPane().getStylesheets().add(getClass().getResource(CSS).toExternalForm());
    }

    public void show() { stage.show(); }
    public void setVisible(boolean visible) {
        if (visible) stage.show(); else stage.hide();
    }
    public void guardAgainstClickThrough() {
        // JavaFX khong bi hien tuong click-through giua cac Stage nhu Swing,
        // giu method rong de tuong thich voi ClientApp goi toi.
    }
}