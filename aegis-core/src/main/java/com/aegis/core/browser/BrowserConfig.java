package com.aegis.core.browser;

/**
 * How to launch the underlying browser. {@code type} is one of
 * "chromium", "firefox", "webkit" (case-insensitive) — mirrors the three
 * engines Playwright itself supports symmetrically.
 *
 * {@link #defaults()} matches exactly what was hardcoded before this
 * existed (chromium, headed) — passing no config anywhere is a
 * zero-behavior-change no-op.
 */
public record BrowserConfig(String type, boolean headless) {

    private static final String DEFAULT_TYPE = "chromium";

    public static BrowserConfig defaults() {
        return new BrowserConfig(DEFAULT_TYPE, false);
    }

    public BrowserConfig {
        if (type == null || type.isBlank()) {
            type = DEFAULT_TYPE;
        }
    }
}
