package org.example;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebServer {
    private final List<String> validPaths;
    private final ExecutorService threadPool;
    private final int port;


    public WebServer(List<String> validPaths, int port) {
        this.validPaths = validPaths;
        this.port = port;
        threadPool = Executors.newFixedThreadPool(64);

    }

    public void serverOn() {
        try (final var serverSocket = new ServerSocket(port)) {
            while (true) {

                final var socket = serverSocket.accept();
                final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                final var out = new BufferedOutputStream(socket.getOutputStream());

                threadPool.submit(new Client(socket, in, out));

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private class Client extends Thread {
        final Socket clientSocket;
        final BufferedReader in;
        final BufferedOutputStream out;

        public Client(Socket clientSocket, BufferedReader in, BufferedOutputStream out) {
            this.clientSocket = clientSocket;
            this.in = in;
            this.out = out;
        }

        public void notFound() throws IOException {
            out.write((
                    "HTTP/1.1 404 Not Found\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.flush();
        }

        public void classicHtml(Path filePath, String mimeType) throws IOException {

            final var template = Files.readString(filePath);
            final var content = template.replace(
                    "{time}",
                    LocalDateTime.now().toString()
            ).getBytes();
            out.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + content.length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.write(content);
            out.flush();
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
                // read only request line for simplicity
                // must be in form GET /path HTTP/1.1
                final var requestLine = in.readLine();
                final var parts = requestLine.split(" ");

                if (parts.length == 3) {
                    final var path = parts[1];

                    if (!validPaths.contains(path)) {
                        notFound();
                    } else {
                        final var filePath = Path.of(".", "public", path);
                        final var mimeType = Files.probeContentType(filePath);

                        // special case for classic
                        if (path.equals("/classic.html")) {
                            classicHtml(filePath, mimeType);
                        } else {
                            defaultCase(filePath, mimeType);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
