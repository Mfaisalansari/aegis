package com.aegis.core.engine;

import com.aegis.core.browser.Browser;
import com.aegis.core.browser.BrowserConfig;
import com.aegis.core.plugin.BrowserFactory;

/**
 * Test-only {@link BrowserFactory}, registered for real via {@code
 * META-INF/services} (see {@code src/test/resources}), pairing with
 * {@link TestCloseThrowingBrowser}. {@code type()} is a deliberately
 * unique string ("test-close-throws") so this never fires for a real
 * chromium/firefox/webkit request or collides with any other test.
 */
public class TestCloseThrowingBrowserFactory implements BrowserFactory {

    @Override
    public String type() {
        return "test-close-throws";
    }

    @Override
    public Browser create(BrowserConfig config) {
        return new TestCloseThrowingBrowser();
    }
}
