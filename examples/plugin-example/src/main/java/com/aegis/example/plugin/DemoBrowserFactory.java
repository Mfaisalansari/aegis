package com.aegis.example.plugin;

import com.aegis.core.browser.Browser;
import com.aegis.core.browser.BrowserConfig;
import com.aegis.core.browser.playwright.PlaywrightBrowser;
import com.aegis.core.plugin.BrowserFactory;

/**
 * Worked example of a Stage 2 "Browser Plugin". Delegates to the real
 * {@code PlaywrightBrowser} — the point here is proving the {@code
 * browser.type} selection mechanism itself works end-to-end, not
 * demonstrating an actually-different engine (a real Selenium/remote-grid
 * Browser is a much larger undertaking than this example needs).
 * Registered via {@code META-INF/services/com.aegis.core.plugin.BrowserFactory}.
 */
public class DemoBrowserFactory implements BrowserFactory {

    @Override
    public String type() {
        return "demo-browser";
    }

    @Override
    public Browser create(BrowserConfig config) {
        System.out.println("DemoBrowserFactory: creating a Browser for a custom 'demo-browser' type");
        return new PlaywrightBrowser(new BrowserConfig("chromium", config.headless()));
    }
}
