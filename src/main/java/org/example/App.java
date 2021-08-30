package org.example;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

public class App {

    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final int PORT = 9999;

    public static final List<String> validPaths = List.of("/index.html", "/spring.svg",
            "/spring.png", "/resources.html", "/styles.css", "/app.js",
            "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js", "/favicon.ico");
    public static final List<String> allowedMethods = List.of(GET, POST);
    public static final HashMap<String, HashMap<String, Handler>> handlers = new HashMap<>();


    public static void main(String[] args) {

        WebServer server = new WebServer();

        server.addHandler("GET", "/classic.html", new Handler() {
            @Override
            public void handle(Request request, BufferedOutputStream responseStream) {
                try {

                    final var filePath = Path.of(".", "public", request.getCleanPath());
                    final var mimeType = Files.probeContentType(filePath);

                    final var template = Files.readString(filePath);
                    final var content = template.replace(
                            "{time}",
                            LocalDateTime.now().toString()
                    ).getBytes();
                    responseStream.write((
                            "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: " + mimeType + "\r\n" +
                                    "Content-Length: " + content.length + "\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n"
                    ).getBytes());
                    responseStream.write(content);
                    responseStream.flush();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

       server.addHandler("GET", "/default-get.html", new Handler() { //TODO
            @Override
            public void handle(Request request, BufferedOutputStream responseStream) {
                try {

                    final var filePath = Path.of(".", "public", request.getCleanPath());
                    final var mimeType = Files.probeContentType(filePath);

                    final var template = Files.readString(filePath);
                    final var content = template.replace(
                            "{time}",
                            LocalDateTime.now().toString()
                    ).getBytes();
                    responseStream.write((
                            "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: " + mimeType + "\r\n" +
                                    "Content-Length: " + content.length + "\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n"
                    ).getBytes());
                    responseStream.write(content);
                    responseStream.flush();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        server.addHandler("GET", "/", new Handler() { //TODO
            @Override
            public void handle(Request request, BufferedOutputStream responseStream) {
                try {

                    final var filePath = Path.of(".", "public", "response.html");
                    final var mimeType = Files.probeContentType(filePath);

                    final var template = Files.readString(filePath);
                    final var contentReplaceTime = template.replace(
                            "{time}",
                            LocalDateTime.now().toString()
                    );

                    final var content = contentReplaceTime
                            .replace("{response}", request.getPath())
                            .getBytes(StandardCharsets.UTF_8);
                    responseStream.write((
                            "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: " + mimeType + "\r\n" +
                                    "Content-Length: " + content.length + "\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n"
                    ).getBytes());
                    responseStream.write(content);
                    responseStream.flush();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

       server.serverOn(PORT);
    }
}
