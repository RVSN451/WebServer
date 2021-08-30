package org.example;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Request {
    private static final URLEncodedUtils URL_ENCODED_UTILS = new URLEncodedUtils();

    private final String stringHeaders; // хранение избыточно - выполнено для реализации ДЗ Метод: getQueryParams().
    private final String[] requestLine;
    private final Map<String, String> headers;
    private final byte[] body;
    private final String method;
    private final String path;


    private Request(String[] requestLine, String stringHeaders, Map<String, String> headers, byte[] body) {
        this.stringHeaders = stringHeaders;
        this.requestLine = requestLine;
        this.headers = headers;
        this.body = body;
        method = requestLine[0];
        path = requestLine[1];

    }


    // ДЗ Формы и форматы передачи данных. Query. Метод: getQueryParams().
    public List<NameValuePair> getQueryParams() throws IOException {
        return getListQueryParams(stringHeaders);
    }

    // ДЗ Формы и форматы передачи данных. Query. Метод: getQueryParam(String headerName).
    public String getQueryParam(String headerName)  {
        return headers.getOrDefault(headerName, null);
    }


    public String[] getRequestLine() {
        return requestLine;
    }

    public byte[] getBody() {
        return body;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getCleanPath() {
        return this.path.split("\\?")[0];
    }

    public Map<String, String> getHeaders() {
        return headers;
    }


    public static Request requestFromInputStream(InputStream inputStream) throws IOException {

        var in = new BufferedReader(new InputStreamReader(inputStream));

        // читаем request line
        final var REQUEST_LINE = in.readLine().split(" ");
        if (REQUEST_LINE.length != 3) {
            throw new IOException("String[] REQUEST_LINE.length != 3");
        }

        final var METHOD = REQUEST_LINE[0];
        if (!App.allowedMethods.contains(METHOD)) {
            throw new IOException("415 Method not support.");
        }

        final var path = REQUEST_LINE[1];
        if (!path.startsWith("/")) {
            throw new IOException("PATH starts not with '/'.");

        }

        // ищем заголовки
        final var STRING_HEADERS = in.readLine();
        final var LIST_NAME_VALUE_PAIR = getListQueryParams(STRING_HEADERS);
        final var HEADERS = headersLitToMap(LIST_NAME_VALUE_PAIR);

        //Ищем тело, в случае наличия
        //  * пропускаем пустую строку
        in.readLine();
        final byte[] BODY =
                (!(REQUEST_LINE[0].equals(App.GET)) && HEADERS.containsKey("Content-Length")) ?
                        in.readLine().getBytes(StandardCharsets.UTF_8) : null;

        return new Request(REQUEST_LINE, STRING_HEADERS, HEADERS, BODY);
    }


    private static List<NameValuePair> getListQueryParams(String queryString) {
        return URL_ENCODED_UTILS.parse
                (queryString, StandardCharsets.UTF_8);
    }

    private static Map<String, String> headersLitToMap(List<NameValuePair> headers) {
        Map<String, String> mapHeaders = new HashMap<>();
        for (NameValuePair line : headers) {
            mapHeaders.put(line.getName(), line.getValue());
        }
        return mapHeaders;
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Request) obj;
        return Arrays.equals(this.requestLine, that.requestLine) &&
                Objects.equals(this.headers, that.headers) && Objects.equals(this.body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestLine);
    }

    @Override
    public String toString() {
        return "Request[" +
                "method=" + method + ", " +
                "path=" + path + ", " +
                "headers=" + headers + "]";
    }
}
