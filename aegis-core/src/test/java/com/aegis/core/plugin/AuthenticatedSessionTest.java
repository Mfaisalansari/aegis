package com.aegis.core.plugin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticatedSessionTest {

    @Test
    void nullCollectionsNormalizeToEmpty() {

        AuthenticatedSession session = new AuthenticatedSession(null, null, null, null, null);

        assertTrue(session.cookies().isEmpty());
        assertTrue(session.localStorage().isEmpty());
        assertTrue(session.sessionStorage().isEmpty());
        assertTrue(session.headers().isEmpty());
        assertNull(session.browserProfilePath());
    }

    @Test
    void ofCookiesCarriesOnlyTheCookiesGiven() {

        var cookie = new AuthenticatedSession.SessionCookie("session", "abc123", "example.com", "/");
        AuthenticatedSession session = AuthenticatedSession.ofCookies(List.of(cookie));

        assertEquals(1, session.cookies().size());
        assertEquals("session", session.cookies().get(0).name());
        assertTrue(session.localStorage().isEmpty());
        assertNull(session.browserProfilePath());
    }

    @Test
    void ofBrowserProfileCarriesOnlyTheProfilePath() {

        AuthenticatedSession session = AuthenticatedSession.ofBrowserProfile("/tmp/my-profile");

        assertEquals("/tmp/my-profile", session.browserProfilePath());
        assertTrue(session.cookies().isEmpty());
    }
}
