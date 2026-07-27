package com.aegis.core.resilience;

import com.aegis.core.browser.Browser;
import com.aegis.model.observation.AnomalySignal;
import com.aegis.model.observation.ElementInfo;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfHealingBrowserTest {

    private static final Duration NO_DELAY = Duration.ZERO;
    private static final Duration RETRY_TIMEOUT = Duration.ofMillis(50);

    @Test
    void succeedsOnFirstAttemptWithoutAnyRetry() {

        RecordingBrowser fake = new RecordingBrowser();
        SelfHealingBrowser browser = new SelfHealingBrowser(fake, NO_DELAY, RETRY_TIMEOUT);

        browser.click("[id='submit']");

        assertEquals(List.of("click:[id='submit']"), fake.calls);
    }

    @Test
    void retriesTheSameLocatorOnceAfterATransientFailure() {

        RecordingBrowser fake = new RecordingBrowser();
        fake.clickFailuresRemaining.put("[id='submit']", 1);

        SelfHealingBrowser browser = new SelfHealingBrowser(fake, NO_DELAY, RETRY_TIMEOUT);
        browser.click("[id='submit']");

        assertEquals(List.of("click:[id='submit']", "click(t):[id='submit']"), fake.calls);
    }

    @Test
    void healsToAWorkingContainsCandidateWhenTheExactLocatorKeepsFailing() {

        RecordingBrowser fake = new RecordingBrowser();
        fake.clickShouldFail = locator -> !"[id*='submit']".equals(locator);

        SelfHealingBrowser browser = new SelfHealingBrowser(fake, NO_DELAY, RETRY_TIMEOUT);
        browser.click("[id='submit']");

        assertEquals(
                List.of("click:[id='submit']", "click(t):[id='submit']", "click(t):[id*='submit']"),
                fake.calls);
    }

    @Test
    void triesTheSecondHealedCandidateWhenTheFirstOneAlsoFails() {

        RecordingBrowser fake = new RecordingBrowser();
        fake.clickShouldFail = locator -> !":nth-match([id*='submit'], 1)".equals(locator);

        SelfHealingBrowser browser = new SelfHealingBrowser(fake, NO_DELAY, RETRY_TIMEOUT);
        browser.click("[id='submit']");

        assertEquals(
                List.of(
                        "click:[id='submit']",
                        "click(t):[id='submit']",
                        "click(t):[id*='submit']",
                        "click(t):" + ":nth-match([id*='submit'], 1)"),
                fake.calls);
    }

    @Test
    void givesUpAndRethrowsWhenNoCandidateWorks() {

        RecordingBrowser fake = new RecordingBrowser();
        fake.clickShouldFail = locator -> true;

        SelfHealingBrowser browser = new SelfHealingBrowser(fake, NO_DELAY, RETRY_TIMEOUT);

        RuntimeException e = assertThrows(RuntimeException.class, () -> browser.click("[id='submit']"));
        assertTrue(e.getMessage().contains("[id*='submit']") || e.getMessage().contains("submit"));

        assertEquals(
                List.of(
                        "click:[id='submit']",
                        "click(t):[id='submit']",
                        "click(t):[id*='submit']",
                        "click(t):" + ":nth-match([id*='submit'], 1)"),
                fake.calls);
    }

    @Test
    void stopsAfterTheSingleRetryWhenTheLocatorShapeIsNotHealable() {

        RecordingBrowser fake = new RecordingBrowser();
        fake.clickShouldFail = locator -> true;

        SelfHealingBrowser browser = new SelfHealingBrowser(fake, NO_DELAY, RETRY_TIMEOUT);

        assertThrows(RuntimeException.class, () -> browser.click("text=Sign in"));

        assertEquals(List.of("click:text=Sign in", "click(t):text=Sign in"), fake.calls);
    }

    @Test
    void carriesTheSameTextThroughEveryRetryAndHealAttemptForType() {

        RecordingBrowser fake = new RecordingBrowser();
        fake.typeShouldFail = locator -> !"[id*='email']".equals(locator);

        SelfHealingBrowser browser = new SelfHealingBrowser(fake, NO_DELAY, RETRY_TIMEOUT);
        browser.type("[id='email']", "user@example.com");

        assertEquals("user@example.com", fake.lastTypeText);
        assertEquals(
                List.of("type:[id='email']", "type(t):[id='email']", "type(t):[id*='email']"),
                fake.calls);
    }

    @Test
    void retriesNavigateOnceAfterATransientFailureThenSucceeds() {

        RecordingBrowser fake = new RecordingBrowser();
        fake.navigateFailuresRemaining = 1;

        SelfHealingBrowser browser = new SelfHealingBrowser(fake, NO_DELAY, RETRY_TIMEOUT);
        browser.navigate("https://example.com");

        assertEquals(List.of("navigate:https://example.com", "navigate:https://example.com"), fake.calls);
    }

    @Test
    void rethrowsWhenNavigateFailsTwiceInARow() {

        RecordingBrowser fake = new RecordingBrowser();
        fake.navigateFailuresRemaining = Integer.MAX_VALUE;

        SelfHealingBrowser browser = new SelfHealingBrowser(fake, NO_DELAY, RETRY_TIMEOUT);

        assertThrows(RuntimeException.class, () -> browser.navigate("https://example.com"));
        assertEquals(List.of("navigate:https://example.com", "navigate:https://example.com"), fake.calls);
    }

    @Test
    void readOnlyAndLifecycleMethodsPassThroughWithNoRetry() {

        RecordingBrowser fake = new RecordingBrowser();
        fake.getPageTitleAlwaysFails = true;

        SelfHealingBrowser browser = new SelfHealingBrowser(fake, NO_DELAY, RETRY_TIMEOUT);

        assertThrows(RuntimeException.class, browser::getPageTitle);
        assertEquals(List.of("getPageTitle"), fake.calls);
    }

    @Test
    void capturesOneScreenshotPerSuccessfulElementActionAndNavigation() {

        RecordingBrowser fake = new RecordingBrowser();
        fake.screenshotBytes = new byte[]{1, 2, 3};

        SelfHealingBrowser browser = new SelfHealingBrowser(fake, NO_DELAY, RETRY_TIMEOUT);
        browser.click("[id='submit']");
        browser.navigate("https://example.com");

        assertEquals(2, browser.capturedScreenshots().size());
    }

    @Test
    void stillCapturesAScreenshotWhenTheActionUltimatelyFails() {

        RecordingBrowser fake = new RecordingBrowser();
        fake.screenshotBytes = new byte[]{1, 2, 3};
        fake.clickShouldFail = locator -> true;

        SelfHealingBrowser browser = new SelfHealingBrowser(fake, NO_DELAY, RETRY_TIMEOUT);

        assertThrows(RuntimeException.class, () -> browser.click("text=Sign in"));

        assertEquals(1, browser.capturedScreenshots().size());
    }

    @Test
    void capturesNothingWhenTheDelegateHasNoScreenshotSupport() {

        RecordingBrowser fake = new RecordingBrowser();

        SelfHealingBrowser browser = new SelfHealingBrowser(fake, NO_DELAY, RETRY_TIMEOUT);
        browser.click("[id='submit']");

        assertTrue(browser.capturedScreenshots().isEmpty());
    }

    @Test
    void aScreenshotCaptureFailureDoesNotMaskTheActionsOwnOutcome() {

        RecordingBrowser fake = new RecordingBrowser();
        fake.screenshotPngThrows = true;

        SelfHealingBrowser browser = new SelfHealingBrowser(fake, NO_DELAY, RETRY_TIMEOUT);

        browser.click("[id='submit']");

        assertTrue(browser.capturedScreenshots().isEmpty());
    }

    /**
     * Regression test: {@code applySession} was missing an override
     * entirely, so it silently fell through to {@link Browser}'s no-op
     * default instead of ever reaching the wrapped browser — discovered
     * live, not by a prior test, since none existed for this method.
     */
    @Test
    void appliesASessionThroughToTheWrappedBrowser() {

        RecordingBrowser fake = new RecordingBrowser();
        SelfHealingBrowser browser = new SelfHealingBrowser(fake, NO_DELAY, RETRY_TIMEOUT);

        browser.applySession(com.aegis.core.plugin.AuthenticatedSession.ofBrowserProfile("/tmp/some-profile"));

        assertEquals(List.of("applySession"), fake.calls);
    }

    private static final class RecordingBrowser implements Browser {

        final List<String> calls = new ArrayList<>();
        final java.util.Map<String, Integer> clickFailuresRemaining = new java.util.HashMap<>();

        Predicate<String> clickShouldFail = locator -> false;
        Predicate<String> typeShouldFail = locator -> false;
        int navigateFailuresRemaining = 0;
        boolean getPageTitleAlwaysFails = false;
        String lastTypeText;
        byte[] screenshotBytes = new byte[0];
        boolean screenshotPngThrows = false;

        private boolean clickFails(String locator) {

            Integer remaining = clickFailuresRemaining.get(locator);
            if (remaining != null && remaining > 0) {
                clickFailuresRemaining.put(locator, remaining - 1);
                return true;
            }

            return clickShouldFail.test(locator);
        }

        @Override
        public void launch() {
            calls.add("launch");
        }

        @Override
        public void close() {
            calls.add("close");
        }

        @Override
        public void navigate(String url) {

            calls.add("navigate:" + url);

            if (navigateFailuresRemaining > 0) {
                navigateFailuresRemaining--;
                throw new RuntimeException("navigate failed: " + url);
            }
        }

        @Override
        public void refresh() {
            calls.add("refresh");
        }

        @Override
        public void goBack() {
            calls.add("goBack");
        }

        @Override
        public void click(String locator) {

            calls.add("click:" + locator);

            if (clickFails(locator)) {
                throw new RuntimeException("click failed: " + locator);
            }
        }

        @Override
        public void click(String locator, Duration timeout) {

            calls.add("click(t):" + locator);

            if (clickFails(locator)) {
                throw new RuntimeException("click failed: " + locator);
            }
        }

        @Override
        public void doubleClick(String locator) {
            calls.add("doubleClick:" + locator);
        }

        @Override
        public void raceClick(String locator) {
            calls.add("raceClick:" + locator);
        }

        @Override
        public void type(String locator, String text) {

            calls.add("type:" + locator);
            lastTypeText = text;

            if (typeShouldFail.test(locator)) {
                throw new RuntimeException("type failed: " + locator);
            }
        }

        @Override
        public void type(String locator, String text, Duration timeout) {

            calls.add("type(t):" + locator);
            lastTypeText = text;

            if (typeShouldFail.test(locator)) {
                throw new RuntimeException("type failed: " + locator);
            }
        }

        @Override
        public void select(String locator) {
            calls.add("select:" + locator);
        }

        @Override
        public void scrollTo(String locator) {
            calls.add("scrollTo:" + locator);
        }

        @Override
        public String getPageTitle() {

            calls.add("getPageTitle");

            if (getPageTitleAlwaysFails) {
                throw new RuntimeException("getPageTitle failed");
            }

            return "title";
        }

        @Override
        public String getCurrentUrl() {
            calls.add("getCurrentUrl");
            return "https://example.com";
        }

        @Override
        public List<ElementInfo> getButtons() {
            calls.add("getButtons");
            return List.of();
        }

        @Override
        public List<ElementInfo> getInputs() {
            calls.add("getInputs");
            return List.of();
        }

        @Override
        public List<ElementInfo> getLinks() {
            calls.add("getLinks");
            return List.of();
        }

        @Override
        public List<ElementInfo> getSelects() {
            calls.add("getSelects");
            return List.of();
        }

        @Override
        public List<AnomalySignal> drainAnomalies() {
            calls.add("drainAnomalies");
            return List.of();
        }

        @Override
        public byte[] screenshotPng() {

            if (screenshotPngThrows) {
                throw new RuntimeException("screenshot capture failed");
            }

            return screenshotBytes;
        }

        @Override
        public void applySession(com.aegis.core.plugin.AuthenticatedSession session) {
            calls.add("applySession");
        }
    }
}
