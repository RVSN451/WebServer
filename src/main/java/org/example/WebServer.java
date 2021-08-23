package org.example;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class WebServer {
    // Список разрешённых путей
    private final List<String> validPaths;
    private final List<String> allowedMethods;
    // Map обработчиков, включает key(метод):value(Map key(путь):value(обработчик))
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Handler>> handlers;
    private final ExecutorService threadPool;

    public WebServer(List<String> validPaths, List<String> allowedMethods,
                     ConcurrentHashMap<String, ConcurrentHashMap<String, Handler>> handlers) {
        this.validPaths = validPaths;
        this.allowedMethods = allowedMethods;
        threadPool = Executors.newFixedThreadPool(64);
        this.handlers = handlers;

    }

    public void addHandler(String method, String path, Handler handler) {
        handlers
                .computeIfAbsent(method, k -> new ConcurrentHashMap<String, Handler>())
                .computeIfAbsent(path, k -> handler);
    }

    @SuppressWarnings("InfiniteLoopStatement")
    public void serverOn(int port) {
        try (final var serverSocket = new ServerSocket(port)) {
            while (true) {
                final var socket = serverSocket.accept();
                threadPool.submit(new Client(socket));
                socket.close();
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

            clientSocket.close();
            in.close();
            out.close();
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
                    badRequest(out);
                    Thread.currentThread().interrupt();
                }
                System.out.println(method);

                final var path = requestLine[1];
                if (!path.startsWith("/")) {
                    badRequest(out);
                    Thread.currentThread().interrupt();
                }
                System.out.println(path);

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
                System.out.println(headers);

                // для GET тела нет
                String body = "";
                if (!method.equals(App.GET)) {
                    in.skip(headersDelimiter.length);
                    // вычитываем Content-Length, чтобы прочитать body
                    final var contentLength = extractHeader(headers, "Content-Length");
                    if (contentLength.isPresent()) {
                        final var length = Integer.parseInt(contentLength.get());
                        final var bodyBytes = in.readNBytes(length);

                        final var bodyString = new String(bodyBytes);
                        System.out.println(bodyString);
                        body = bodyString;
                    }
                }


                Request request = new Request(requestLine, headers, body);

                /*ConcurrentMap handle = handlers.entrySet()
                        .stream()
                        .filter(k -> k.getKey() == request.getMethod())
                        .collect(
                                Collectors.toConcurrentMap(k -> k.getKey(),v -> v.getValue()));
                handle.entrySet()
                        .stream()
                        .filter(k -> k.g)

                */





                ConcurrentHashMap handle = handlers.getOrDefault(request.getMethod(), null);
                if (handle != null) {
                    if (handle.getOrDefault(request.getPath(), null) != null) {
                        Set s = handle.entrySet()
                                .stream()
                                .filter(k -> k.getKey() == request.getPath()) // ПОЧЕМУ НЕ ФИЛЬТРУЕТ ПО КЛЮЧУ???
                                .forEach(Handler::handle);
                    }
                } else {

                    final var filePath = Path.of(".", "public", request.getPath());
                    final var mimeType = Files.probeContentType(filePath);
                    defaultCase(filePath, mimeType);
                }
                //TODO как вбрать нужнй обработчик?


            } catch (IOException e) {
                e.printStackTrace();
            }

        }






        /*public void notFound() throws IOException {
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
                in.close();
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }*/
    }
}
