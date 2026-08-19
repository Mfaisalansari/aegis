package com.aegis.web.form;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Decodes an {@code application/x-www-form-urlencoded} request body into
 * a {@code Map<String,String>}. Pure and dependency-free — no servlet
 * API, just what {@link com.sun.net.httpserver.HttpExchange} hands over
 * as raw bytes.
 */
public final class FormBodyParser {

    private FormBodyParser() {
    }

    public static Map<String, String> parse(byte[] body) {

        Map<String, String> values = new LinkedHashMap<>();

        String raw = new String(body, StandardCharsets.UTF_8);
        if (raw.isEmpty()) {
            return values;
        }

        for (String pair : raw.split("&")) {

            if (pair.isEmpty()) {
                continue;
            }

            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";

            values.put(decode(key), decode(value));
        }

        return values;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
