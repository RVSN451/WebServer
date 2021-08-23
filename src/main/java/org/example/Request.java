package org.example;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class Request {
    private final String[] requestLine;
    private final List<String> headers;
    private final String body;
    private final String method;
    private final String path;


    Request(String[] requestLine, List<String> headers, String body) {
        this.requestLine = requestLine;
        this.headers = headers;
        this.body = body;
        method = requestLine[0];
        path = requestLine[1];

    }

    public String[] getRequestLine() {
        return requestLine;
    }

    public String getBody() {
        return body;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public List<String> getHeaders() {
        return headers;
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
