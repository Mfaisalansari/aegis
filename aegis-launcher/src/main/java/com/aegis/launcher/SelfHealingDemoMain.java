package com.aegis.launcher;

import com.aegis.core.browser.BrowserConfig;
import com.aegis.core.browser.playwright.PlaywrightBrowser;
import com.aegis.core.resilience.SelfHealingBrowser;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Demonstrates Phase 9 self-healing against a real browser, not a JUnit
 * mock. {@code self-healing-demo.html}'s real button id is
 * {@code login-button-v2} — AEGIS is made to click the stale locator
 * {@code [id='login-button']} on purpose, which does not exist on the page
 * at all. A plain {@code Browser} would throw; wrapping it in {@link
 * SelfHealingBrowser} should retry, then fall through to {@code
 * LocatorHealer}'s substring-match candidate ({@code [id*='login-button']})
 * and succeed against the real element.
 *
 * The first attempt pays Playwright's full default locator-wait (~30s)
 * before healing kicks in — that pause is real and expected, not a hang.
 */
public class SelfHealingDemoMain {

    public static void main(String[] args) throws Exception {

        Path html = extractDemoPage();
        String url = "file://" + html.toAbsolutePath();

        PlaywrightBrowser real = new PlaywrightBrowser(new BrowserConfig("chromium", true));
        SelfHealingBrowser healing = new SelfHealingBrowser(real);

        healing.launch();
        try {
            healing.navigate(url);
            System.out.println("Before click, title = " + healing.getPageTitle());
            System.out.println("Clicking the stale locator [id='login-button'] (real id is 'login-button-v2') ...");
            System.out.println("(the first attempt takes Playwright's full ~30s default wait before healing kicks in)");

            healing.click("[id='login-button']");

            String title = healing.getPageTitle();
            System.out.println("After click, title = " + title);
            System.out.println(title.equals("HEALED-OK") ? "RESULT: SELF-HEALING WORKED" : "RESULT: SELF-HEALING FAILED");
        } finally {
            healing.close();
        }
    }

    private static Path extractDemoPage() throws Exception {

        Path temp = Files.createTempFile("aegis-self-healing-demo", ".html");
        temp.toFile().deleteOnExit();

        try (InputStream in = SelfHealingDemoMain.class.getResourceAsStream("/self-healing-demo.html")) {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        }

        return temp;
    }
}
