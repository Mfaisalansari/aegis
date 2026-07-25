package com.aegis.core.plugin;

import com.aegis.core.browser.Browser;
import com.aegis.core.browser.BrowserConfig;

/**
 * Stage 2 "Browser Plugin": an alternative {@link Browser} implementation
 * — a Selenium-backed one, a remote/cloud browser grid, anything besides
 * Playwright's own chromium/firefox/webkit. Discovered via
 * {@link java.util.ServiceLoader} and selected by setting
 * {@code browser.type} in config to something other than those 3 built-in
 * engine names; {@link #type()} must match exactly (case-insensitive).
 *
 * This is also how "Observer Plugin" extensibility works, deliberately:
 * {@code DefaultObserver} (frozen) builds every Observation purely from
 * {@code Browser.getButtons/getInputs/getLinks/getSelects()} — a plugin
 * wanting different or additional element discovery provides those
 * through a custom Browser here, not a separate Observer interface.
 */
public interface BrowserFactory {

    /** The value {@code browser.type} must equal (case-insensitive) to select this factory. */
    String type();

    Browser create(BrowserConfig config);
}
