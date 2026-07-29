package chat.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatClient {

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 12345;

        try (
                Socket socket = new Socket(host, port);
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                Scanner console = new Scanner(System.in, StandardCharsets.UTF_8)
        ) {
            System.out.println("Подключено к серверу " + host + ":" + port);
            System.out.print("Введите ваш ник: ");
            out.println(console.nextLine().trim());

            String reply = in.readLine();
            if (reply == null) {
                System.out.println("Сервер закрыл соединение.");
                return;
            }
            System.out.println(strip(reply));
            if (reply.startsWith("ERROR:")) {
                return; 
            }

            AtomicBoolean quitting = new AtomicBoolean(false);

      
            Thread listener = new Thread(() -> listenForMessages(in, quitting));
            listener.start();

            System.out.println("Команды: /list — кто в сети, /w ник текст — личное сообщение, /quit — выход\n");

            String line;
            while (console.hasNextLine()) {
                line = console.nextLine();
                if (line.equals("/quit")) {
                    break;
                }
                out.println(line);
            }
            quitting.set(true);
            out.println("/quit");

        } catch (IOException e) {
            System.err.println("Не удалось подключиться: " + e.getMessage());
        }
    }

    private static void listenForMessages(BufferedReader in, AtomicBoolean quitting) {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println(strip(line));
            }
        } catch (IOException e) {
                System.out.println("Соединение с сервером потеряно.");
        }
    }

    private static String strip(String message) {
        if (message.startsWith("OK:") || message.startsWith("ERROR:")) {
            return message.substring(message.indexOf(':') + 1);
        }
        return message;
    }
}
