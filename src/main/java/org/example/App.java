package org.example;

import java.util.List;

public class App {
    public static void main(String[] args) {

        final var validPaths = List.of("/index.html", "/spring.svg",
                "/spring.png", "/resources.html", "/styles.css", "/app.js",
                "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");
        final var PORT = 28964;

        WebServer server = new WebServer(validPaths, PORT);
        server.serverOn();
    }
}
