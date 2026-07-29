package chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class ChatServer {

    private final int port;
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile ServerSocket serverSocket;

    public ChatServer(int port) {
        this.port = port;
    }

    public void start() {
        System.out.println("Запуск чат-сервера на порту " + port);

        //  остановка серверного порта по Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        try (ServerSocket ss = new ServerSocket(port)) {
            this.serverSocket = ss;

            while (!ss.isClosed()) {
                Socket socket = ss.accept();
                System.out.println("Новое подключение: " + socket.getRemoteSocketAddress());
                pool.execute(new ClientHandler(socket, clients, this));
            }
        } catch (IOException e) {
            System.out.println("Сервер остановлен: " + e.getMessage());
        } finally {
            pool.shutdown();
        }
    }

    private void shutdown() {
        System.out.println("\nЗавершение работы сервера...");
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {

        }
        pool.shutdownNow();
    }

    public void broadcast(String message) {
        for (ClientHandler client : clients.values()) {
            client.send(message);
        }
    }

    public static void main(String[] args) {
        int port = 12345;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        new ChatServer(port).start();
    }
}
