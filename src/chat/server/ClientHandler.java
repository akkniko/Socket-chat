package chat.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

class ClientHandler implements Runnable {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Socket socket;
    private final Map<String, ClientHandler> clients;
    private final ChatServer server;

    private PrintWriter out;
    private String nickname;

    ClientHandler(Socket socket, Map<String, ClientHandler> clients, ChatServer server) {
        this.socket = socket;
        this.clients = clients;
        this.server = server;
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
        ) {
            this.out = writer;

            if (!registerNickname(in)) {
                return; 
            }

            server.broadcast(system(nickname + " присоединился к чату"));
            sendOnlineList();

            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.equals("/quit")) {
                    send("OK:До встречи!");
                    break;
                }
                handleLine(line);
            }

        } catch (IOException e) {
            //потеря соединения с клиентом
            System.out.println("Соединение с " + safeName() + " прервано: " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    private boolean registerNickname(BufferedReader in) throws IOException {
        String requested = in.readLine();

        if (requested == null || requested.isBlank()) {
            send("ERROR:Ник не может быть пустым. Переподключитесь.");
            return false;
        }
        requested = requested.trim();
        if (requested.contains(" ") || requested.startsWith("/")) {
            send("ERROR:Ник не должен содержать пробелов или начинаться с '/'.");
            return false;
        }
        if (clients.putIfAbsent(requested, this) != null) {
            send("ERROR:Ник '" + requested + "' уже занят. Переподключитесь с другим.");
            return false;
        }

        this.nickname = requested;
        send("OK:Добро пожаловать, " + nickname + "!");
        return true;
    }

    private void handleLine(String line) {
        if (line.isEmpty()) {
            return;
        }
        if (line.equals("/list")) {
            sendOnlineList();
            return;
        }
        if (line.startsWith("/w ")) {
            handleWhisper(line.substring(3));
            return;
        }

        server.broadcast(nickname + ": " + line);
    }

    private void handleWhisper(String rest) {
        int spaceIdx = rest.indexOf(' ');
        if (spaceIdx < 0) {
            send("ERROR:Формат: /w <ник> <сообщение>");
            return;
        }
        String targetNick = rest.substring(0, spaceIdx);
        String message = rest.substring(spaceIdx + 1);

        ClientHandler target = clients.get(targetNick);
        if (target == null) {
            send("ERROR:Пользователь '" + targetNick + "' не в сети");
            return;
        }
        target.send("[личное от " + nickname + "]: " + message);
        send("[личное для " + targetNick + "]: " + message);
    }

    private void sendOnlineList() {
        send(system("Онлайн (" + clients.size() + "): " + String.join(", ", clients.keySet())));
    }

    void send(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    private void disconnect() {
        if (nickname != null) {
            clients.remove(nickname);
            server.broadcast(system(nickname + " покинул чат"));
            System.out.println(nickname + " отключился");
        }
        closeSocket();
    }

    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private String system(String text) {
        return "* [" + LocalTime.now().format(TIME_FORMAT) + "] " + text + " *";
    }

    private String safeName() {
        return nickname != null ? nickname : String.valueOf(socket.getRemoteSocketAddress());
    }
}
