package org.example;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class WebServer {
    // Список разрешённых путей
    private static final List<String> validPaths = App.validPaths;
    // Список разрешенных методов
    private static final List<String> allowedMethods = App.allowedMethods;

    private final HashMap<String, HashMap<String, Handler>> HANDLERS = App.handlers;


    // Map обработчиков, включает key(метод):value(Map key(путь):value(обработчик))
    private final HashMap<String, HashMap<String, Handler>> handlers;
    private final ExecutorService threadPool;

    public WebServer() {
        threadPool = Executors.newFixedThreadPool(64);
        handlers = HANDLERS;

    }


    @SuppressWarnings("InfiniteLoopStatement")
    public void serverOn(int port) {
        try (final var serverSocket = new ServerSocket(port)) {
            while (true) {
                final var socket = serverSocket.accept();
                threadPool.submit(new Client(socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addHandler(String method, String path, Handler handler) {
        handlers
                .computeIfAbsent(method, k -> new HashMap<>())
                .computeIfAbsent(path, k -> handler);
    }

    private class Client extends Thread {
        final Socket clientSocket;
        final InputStream in;
        final BufferedOutputStream out;

        public Client(Socket clientSocket) throws IOException {
            this.clientSocket = clientSocket;
            this.in = clientSocket.getInputStream();
            this.out = new BufferedOutputStream(clientSocket.getOutputStream());
        }


        private void badRequest(Request request, BufferedOutputStream out) throws IOException {
            try {

                final var filePath = Path.of(".", "public", "/bad-request.html");
                final var mimeType = "html";

                final var template = Files.readString(filePath);
                final var contentReplaceTime = template.replace(
                        "{time}",
                        LocalDateTime.now().toString()
                );

                final var content = contentReplaceTime
                        .replace("{response}", request.getPath())
                        .getBytes(StandardCharsets.UTF_8);
                out.write((
                        "HTTP/1.1 404 Bad request\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + content.length + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                out.write(content);
                out.flush();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void defaultCase(Path filePath, String mimeType) throws IOException {
            final var length = Files.size(filePath);
            out.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            Files.copy(filePath, out);
            out.flush();
        }

        @Override
        public void run() {
            try {

                Request request = Request.requestFromInputStream(in);
                System.out.println(request.getMethod() + " - МЕТОД");
                System.out.println(request + "\n");


                if (App.handlers.getOrDefault(request.getMethod(), null)
                        .getOrDefault(request.getCleanPath(), null) != null) {

                    App.handlers.get(request.getMethod()).get(request.getCleanPath())
                            .handle(request, out);
                } else if (App.validPaths.contains(request.getCleanPath())){
                    final var filePath = Path.of(".", "public", request.getPath());
                    final var mimeType = Files.probeContentType(filePath);
                    defaultCase(filePath, mimeType);

                } else badRequest(request, out);


                clientSocket.close();
                in.close();
                out.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
