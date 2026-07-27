package com.aegis.core.knowledge;

import java.net.URI;
import java.util.Locale;

/**
 * The auto-naming heuristic (hybrid: mechanical default, config can
 * always override) — pure functions over a URL, no state. Deliberately
 * simple: a path like {@code /customers/search} becomes the slug {@code
 * customers-search} and the humanized name {@code "Customers Search"}.
 * Never claims to understand the page, just makes something readable
 * out of what's there.
 */
final class NodeNaming {

    private NodeNaming() {
    }

    /** A stable, URL-derived slug — used as the default {@code key}/{@code technicalName} when config doesn't declare one. */
    static String slug(String url) {

        String path = pathOf(url);

        String slug = path.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        return slug.isEmpty() ? "root" : slug;
    }

    /** A human-readable default display name derived purely from the URL path — used only when there's no usable page title. */
    static String humanize(String url) {

        String path = pathOf(url);
        String[] segments = path.split("[^a-zA-Z0-9]+");

        StringBuilder result = new StringBuilder();

        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(segment.charAt(0))).append(segment.substring(1));
        }

        return result.isEmpty() ? "Home" : result.toString();
    }

    private static String pathOf(String url) {

        try {
            String path = URI.create(url).getPath();
            return path == null ? "" : path;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }
}
