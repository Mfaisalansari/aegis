package com.aegis.core.browser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BrowserConfigTest {

    @Test
    void defaultsAreChromiumAndHeaded() {

        BrowserConfig config = BrowserConfig.defaults();

        assertEquals("chromium", config.type());
        assertFalse(config.headless());
    }

    @Test
    void nullTypeNormalizesToChromium() {
        assertEquals("chromium", new BrowserConfig(null, false).type());
    }

    @Test
    void blankTypeNormalizesToChromium() {
        assertEquals("chromium", new BrowserConfig("   ", false).type());
    }

    @Test
    void anExplicitTypeIsPreservedAsGiven() {
        assertEquals("firefox", new BrowserConfig("firefox", true).type());
    }
}
