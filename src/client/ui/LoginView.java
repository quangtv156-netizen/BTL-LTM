package client.ui;

import client.ClientApp;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class LoginView {
    private final ClientApp app;
    private final Stage stage = new Stage();

    private final TextField hostField = new TextField(ClientApp.DEFAULT_HOST);
    private final TextField portField = new TextField(String.valueOf(ClientApp.DEFAULT_PORT));
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Label errorLabel = new Label();
    private final Button connectBtn = new Button("Đăng nhập");

    public LoginView(ClientApp app) {
        this.app = app;
        buildUI();
    }

    private void buildUI() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("root");
        grid.setPadding(new Insets(20));
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setAlignment(Pos.CENTER);

        grid.add(new Label("Server:"), 0, 0);
        grid.add(hostField, 1, 0);
        grid.add(new Label("Cổng:"), 0, 1);
        grid.add(portField, 1, 1);
        grid.add(new Label("Tên đăng nhập:"), 0, 2);
        grid.add(usernameField, 1, 2);
        grid.add(new Label("Mật khẩu:"), 0, 3);
        grid.add(passwordField, 1, 3);

        errorLabel.getStyleClass().add("error-label");
        grid.add(errorLabel, 0, 4, 2, 1);

        connectBtn.setDefaultButton(true);
        connectBtn.setOnAction(e -> doConnect());
        grid.add(connectBtn, 0, 5, 2, 1);

        Scene scene = new Scene(grid, 360, 260);
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        stage.setTitle("Barricade Game - Đăng nhập");
        stage.setScene(scene);
        stage.setResizable(false);
    }

    private void doConnect() {
        errorLabel.setText("");
        String host = hostField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            errorLabel.setText("Cổng không hợp lệ.");
            return;
        }
        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Vui lòng nhập đầy đủ thông tin.");
            return;
        }
        connectBtn.setDisable(true);
        app.connectAndLogin(host, port, username, password);
    }

    public void show() { stage.show(); }
    public void close() { stage.close(); }

    public void showError(String message) {
        errorLabel.setText(message);
        connectBtn.setDisable(false);
    }
}