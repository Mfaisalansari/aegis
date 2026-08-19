package com.aegis.web.handler;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Small shared response-writing helpers — no framework, just {@link HttpExchange}. */
final class HandlerSupport {

    private HandlerSupport() {
    }

    static void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        send(exchange, status, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8), null);
    }

    static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        send(exchange, status, "text/plain; charset=utf-8", text.getBytes(StandardCharsets.UTF_8), null);
    }

    static void sendFile(HttpExchange exchange, int status, String contentType, String content, String downloadFilename) throws IOException {
        String disposition = downloadFilename == null ? null : "attachment; filename=\"" + downloadFilename + "\"";
        send(exchange, status, contentType, content.getBytes(StandardCharsets.UTF_8), disposition);
    }

    static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body, String contentDisposition) throws IOException {

        exchange.getResponseHeaders().add("Content-Type", contentType);
        if (contentDisposition != null) {
            exchange.getResponseHeaders().add("Content-Disposition", contentDisposition);
        }

        exchange.sendResponseHeaders(status, body.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    static byte[] readBody(HttpExchange exchange) throws IOException {
        return exchange.getRequestBody().readAllBytes();
    }

    static String queryParam(HttpExchange exchange, String name) {

        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return null;
        }

        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            if (key.equals(name)) {
                return eq >= 0 ? pair.substring(eq + 1) : "";
            }
        }

        return null;
    }
}
