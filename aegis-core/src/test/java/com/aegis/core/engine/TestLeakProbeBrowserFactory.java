package com.aegis.core.engine;

import com.aegis.core.browser.Browser;
import com.aegis.core.browser.BrowserConfig;
import com.aegis.core.plugin.BrowserFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Test-only {@link BrowserFactory}, registered for real via {@code
 * META-INF/services} (see {@code src/test/resources}) so {@code
 * EngineFactoryResourceLeakTest} exercises the actual ServiceLoader
 * discovery path — same discipline as {@code TestUppercaseCredentialProvider}
 * in {@code aegis-api}. {@code type()} is a deliberately unique string
 * ("test-leak-probe") so this never fires for a real chromium/firefox/webkit
 * request or collides with any other test.
 */
public class TestLeakProbeBrowserFactory implements BrowserFactory {

    private static final AtomicReference<TestLeakProbeBrowser> LAST = new AtomicReference<>();

    static TestLeakProbeBrowser last() {
        return LAST.get();
    }

    @Override
    public String type() {
        return "test-leak-probe";
    }

    @Override
    public Browser create(BrowserConfig config) {
        TestLeakProbeBrowser browser = new TestLeakProbeBrowser();
        LAST.set(browser);
        return browser;
    }
}
