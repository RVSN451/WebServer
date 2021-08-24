package org.example;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class WebServer {
    // Список разрешённых путей
    private static final List<String> validPaths = App.validPaths;
    // Список разрешенных методов
    private static final List<String> allowedMethods = App.allowedMethods;


    // Map обработчиков, включает key(метод):value(Map key(путь):value(обработчик))
    private final HashMap<String, HashMap<String, Handler>> handlers;
    private final ExecutorService threadPool;

    public WebServer(HashMap<String, HashMap<String, Handler>> handlers) {

        threadPool = Executors.newFixedThreadPool(64);
        this.handlers = handlers;

    }

    public void addHandler(String method, String path, Handler handler) {
        handlers
                .computeIfAbsent(method, k -> new HashMap<>())
                .computeIfAbsent(path, k -> handler);
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

    private class Client extends Thread {
        final Socket clientSocket;
        final BufferedInputStream in;
        final BufferedOutputStream out;

        public Client(Socket clientSocket) throws IOException {
            this.clientSocket = clientSocket;
            this.in = new BufferedInputStream(clientSocket.getInputStream());
            this.out = new BufferedOutputStream(clientSocket.getOutputStream());
        }

        private Optional<String> extractHeader(List<String> headers, String header) {
            return headers.stream()
                    .filter(o -> o.startsWith(header))
                    .map(o -> o.substring(o.indexOf(" ")))
                    .map(String::trim)
                    .findFirst();
        }

        private void badRequest(BufferedOutputStream out) throws IOException {
            out.write((
                    "HTTP/1.1 400 Bad Request\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
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

        // from google guava with modifications
        private int indexOf(byte[] array, byte[] target, int start, int max) {
            outer:
            for (int i = start; i < max - target.length + 1; i++) {
                for (int j = 0; j < target.length; j++) {
                    if (array[i + j] != target[j]) {
                        continue outer;
                    }
                }
                return i;
            }
            return -1;
        }

        @Override
        public void run() {
            try {

                final var limit = 4096;
                in.mark(limit);
                final var buffer = new byte[limit];
                final var read = in.read(buffer);

                // ищем request line
                final var requestLineDelimiter = new byte[]{'\r', '\n'};
                final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
                if (requestLineEnd == -1) {
                    badRequest(out);
                    Thread.currentThread().interrupt();
                }

                // читаем request line
                final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
                if (requestLine.length != 3) {
                    badRequest(out);
                    Thread.currentThread().interrupt();
                }

                final var method = requestLine[0];
                if (!allowedMethods.contains(method)) {
                    badRequest(out); // На самом деле нужно выдавать 415 Method not support
                    Thread.currentThread().interrupt();
                }

                final var path = requestLine[1];
                if (!path.startsWith("/")) {
                    badRequest(out);
                    Thread.currentThread().interrupt();
                }

                // ищем заголовки
                final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
                final var headersStart = requestLineEnd + requestLineDelimiter.length;
                final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
                if (headersEnd == -1) {
                    badRequest(out);
                    Thread.currentThread().interrupt();
                }

                // отматываем на начало буфера
                in.reset();
                // пропускаем requestLine
                in.skip(headersStart);

                final var headersBytes = in.readNBytes(headersEnd - headersStart);
                final var headers = Arrays.asList(new String(headersBytes).split("\r\n"));

                // для GET тела нет
                String body = "";
                if (!method.equals(App.GET)) {
                    in.skip(headersDelimiter.length);
                    // вычитываем Content-Length, чтобы прочитать body
                    final var contentLength = extractHeader(headers, "Content-Length");
                    if (contentLength.isPresent()) {
                        final var length = Integer.parseInt(contentLength.get());
                        final var bodyBytes = in.readNBytes(length);

                        body = new String(bodyBytes, StandardCharsets.UTF_8);
                    }
                }

                Request request = new Request(requestLine, headers, body);


                if (handlers.getOrDefault(request.getMethod(), null)
                        .getOrDefault(request.getPath(), null) != null) {

                    handlers.get(request.getMethod()).get(request.getPath())
                            .handle(request, out);
                } else if (validPaths.contains(request.getPath())){

                    final var filePath = Path.of(".", "public", request.getPath());
                    final var mimeType = Files.probeContentType(filePath);
                    defaultCase(filePath, mimeType);

                } else badRequest(out);


                clientSocket.close();
                in.close();
                out.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
