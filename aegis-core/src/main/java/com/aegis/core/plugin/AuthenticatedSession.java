package com.aegis.core.plugin;

import java.util.List;
import java.util.Map;

/**
 * Pure data describing an already-authenticated session — never
 * behavior. Deliberately doesn't (and never will) carry anything
 * resembling a browser operation: a {@link SessionProvider} hands AEGIS
 * facts (cookies, storage, headers, a persistent profile to reuse), and
 * AEGIS itself is the only thing that ever touches the browser to apply
 * them (see {@code Browser.applySession}). Exploration afterward is
 * 100% the normal autonomous engine — a session provider only gets
 * AEGIS logged in, it never drives the app.
 *
 * Every field defaults to empty/null — a provider only needs to fill in
 * whatever mechanism it actually uses (e.g. just {@code cookies}, or
 * just {@code browserProfilePath}).
 */
public record AuthenticatedSession(
        List<SessionCookie> cookies,
        Map<String, String> localStorage,
        Map<String, String> sessionStorage,
        Map<String, String> headers,
        String browserProfilePath
) {

    public AuthenticatedSession {
        cookies = cookies == null ? List.of() : List.copyOf(cookies);
        localStorage = localStorage == null ? Map.of() : Map.copyOf(localStorage);
        sessionStorage = sessionStorage == null ? Map.of() : Map.copyOf(sessionStorage);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public static AuthenticatedSession ofCookies(List<SessionCookie> cookies) {
        return new AuthenticatedSession(cookies, null, null, null, null);
    }

    public static AuthenticatedSession ofBrowserProfile(String profilePath) {
        return new AuthenticatedSession(null, null, null, null, profilePath);
    }

    public record SessionCookie(String name, String value, String domain, String path) {
    }
}
