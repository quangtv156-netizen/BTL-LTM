package client.net;

import common.message.Message;
import javafx.application.Platform;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class ClientConnection {
    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;
    private volatile Consumer<Message> listener;
    private volatile boolean running = true;

    public ClientConnection(String host, int port) throws IOException {
        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
    }

    /** Dat callback nhan message; callback duoc goi tren JavaFX Application Thread. */
    public void setListener(Consumer<Message> listener) {
        this.listener = listener;
    }

    public void start() {
        Thread t = new Thread(this::listenLoop, "client-listener");
        t.setDaemon(true);
        t.start();
    }

    private void listenLoop() {
        try {
            while (running) {
                Message msg = (Message) in.readObject();
                Consumer<Message> l = listener;
                if (l != null) {
                    Platform.runLater(() -> l.accept(msg));
                }
            }
        } catch (Exception e) {
            if (running) {
                System.err.println("[ClientConnection] Loi doc du lieu tu server, coi nhu mat ket noi:");
                e.printStackTrace();
                Consumer<Message> l = listener;
                if (l != null) Platform.runLater(() -> l.accept(null)); // null = mat ket noi
            }
        }
    }

    public synchronized void send(Message m) {
        try {
            out.reset();
            out.writeObject(m);
            out.flush();
        } catch (IOException e) {
            System.err.println("Gui message that bai: " + e.getMessage());
        }
    }

    public void close() {
        running = false;
        try { socket.close(); } catch (IOException ignored) {}
    }
}