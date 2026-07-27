package com.aegis.core.resilience;

import com.aegis.core.browser.Browser;
import com.aegis.core.browser.SignalRecorder;
import com.aegis.core.knowledge.ElementSnapshot;
import com.aegis.core.plugin.AuthenticatedSession;
import com.aegis.model.observation.AnomalySignal;
import com.aegis.model.observation.ElementInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Decorates any {@link Browser} with resilience against transient failures,
 * without changing the interface's contract: every method still either
 * completes normally or throws, exactly as before — a caller (the frozen
 * {@code ActionExecutor}/{@code ActionHandler} layer) can't tell the
 * difference except that fewer calls fail.
 *
 * Three things happen on a failure, in order, each only attempted if the
 * previous one also failed:
 * <ol>
 *   <li>Retry the exact same call once, after a short delay — covers
 *   ordinary transient flakiness (an animation still settling, a network
 *   blip) that a Playwright TimeoutError already reports.</li>
 *   <li>For element actions (click/type/select/...), retry against each
 *   locator candidate {@link LocatorHealer} derives from the original —
 *   covers a locator that's gone stale because the DOM shifted slightly
 *   since it was generated.</li>
 *   <li>Give up and rethrow the most recent failure — matches
 *   {@link Browser}'s existing contract, so mission-loop error recovery
 *   ({@code DefaultMissionEngine}) needs no changes at all.</li>
 * </ol>
 *
 * Retry/heal attempts use a short, explicit timeout rather than Playwright's
 * full default wait — the first attempt already paid that cost once, so
 * repeating it verbatim on every retry would make a genuinely-broken
 * locator stall the mission for minutes instead of seconds.
 *
 * Also captures one screenshot after every element action and navigation
 * call (success or failure), available via {@link #capturedScreenshots()}
 * — this is what backs the Mission Timeline's screenshot hooks.
 */
public final class SelfHealingBrowser implements Browser {

    private static final Logger log = LoggerFactory.getLogger(SelfHealingBrowser.class);

    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofMillis(300);
    private static final Duration DEFAULT_RETRY_TIMEOUT = Duration.ofSeconds(3);

    private final Browser delegate;
    private final Duration retryDelay;
    private final Duration retryTimeout;
    private final List<ScreenshotSample> screenshots = new ArrayList<>();

    public SelfHealingBrowser(Browser delegate) {
        this(delegate, DEFAULT_RETRY_DELAY, DEFAULT_RETRY_TIMEOUT);
    }

    /** Widened for tests: a real delay would make retry/heal tests slow for no benefit. */
    SelfHealingBrowser(Browser delegate, Duration retryDelay, Duration retryTimeout) {
        this.delegate = delegate;
        this.retryDelay = retryDelay;
        this.retryTimeout = retryTimeout;
    }

    @Override
    public void launch() {
        delegate.launch();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public void navigate(String url) {
        executeNavigation("navigate(" + url + ")", () -> delegate.navigate(url));
    }

    @Override
    public void refresh() {
        executeNavigation("refresh()", delegate::refresh);
    }

    @Override
    public void goBack() {
        executeNavigation("goBack()", delegate::goBack);
    }

    @Override
    public void click(String locator) {
        executeElementAction(locator, l -> delegate.click(l), (l, t) -> delegate.click(l, t));
    }

    @Override
    public void doubleClick(String locator) {
        executeElementAction(locator, l -> delegate.doubleClick(l), (l, t) -> delegate.doubleClick(l, t));
    }

    @Override
    public void raceClick(String locator) {
        executeElementAction(locator, l -> delegate.raceClick(l), (l, t) -> delegate.raceClick(l, t));
    }

    @Override
    public void type(String locator, String text) {
        executeElementAction(locator, l -> delegate.type(l, text), (l, t) -> delegate.type(l, text, t));
    }

    @Override
    public void select(String locator) {
        executeElementAction(locator, l -> delegate.select(l), (l, t) -> delegate.select(l, t));
    }

    @Override
    public void scrollTo(String locator) {
        executeElementAction(locator, l -> delegate.scrollTo(l), (l, t) -> delegate.scrollTo(l, t));
    }

    @Override
    public String getPageTitle() {
        return delegate.getPageTitle();
    }

    @Override
    public String getCurrentUrl() {
        return delegate.getCurrentUrl();
    }

    @Override
    public List<ElementInfo> getButtons() {
        return delegate.getButtons();
    }

    @Override
    public List<ElementInfo> getInputs() {
        return delegate.getInputs();
    }

    @Override
    public List<ElementInfo> getLinks() {
        return delegate.getLinks();
    }

    @Override
    public List<ElementInfo> getSelects() {
        return delegate.getSelects();
    }

    @Override
    public List<AnomalySignal> drainAnomalies() {
        return delegate.drainAnomalies();
    }

    /**
     * Was missing entirely until now — {@code Browser.applySession}'s
     * interface default is a no-op, and this class never overrode it, so
     * every real mission (which always runs through this decorator, per
     * {@code EngineFactory}) silently discarded any Stage 2 pre-
     * authenticated session ({@code SessionProvider}) instead of applying
     * it. Found while wiring the Page Inspection Layer's own
     * {@code attachSignalRecorder}, which had the exact same gap.
     */
    @Override
    public void applySession(AuthenticatedSession session) {
        delegate.applySession(session);
    }

    @Override
    public void attachSignalRecorder(SignalRecorder recorder) {
        delegate.attachSignalRecorder(recorder);
    }

    @Override
    public ElementSnapshot captureElementSnapshot(String locator) {
        return delegate.captureElementSnapshot(locator);
    }

    /**
     * Every screenshot captured so far this session, oldest first. Not
     * part of the {@link Browser} interface — this is specific
     * instrumentation this decorator adds, not a general browser
     * capability, so it's a plain method on the concrete type. Read by
     * {@code Aegis.run} after a mission finishes to build the Mission
     * Timeline's screenshot hooks (see {@code TimelineEvent}).
     */
    public List<ScreenshotSample> capturedScreenshots() {
        return List.copyOf(screenshots);
    }

    private void executeElementAction(String locator, Attempt attempt, TimedAttempt timedAttempt) {

        try {
            executeElementActionAttempts(locator, attempt, timedAttempt);
        } finally {
            captureScreenshot();
        }
    }

    private void executeElementActionAttempts(String locator, Attempt attempt, TimedAttempt timedAttempt) {

        try {
            attempt.run(locator);
            return;
        } catch (RuntimeException firstFailure) {
            log.warn("Action against '{}' failed, retrying: {}", locator, firstFailure.getMessage());
        }

        sleep();

        try {
            timedAttempt.run(locator, retryTimeout);
            return;
        } catch (RuntimeException retryFailure) {

            for (String candidate : LocatorHealer.candidatesFor(locator)) {
                try {
                    timedAttempt.run(candidate, retryTimeout);
                    log.info("Healed locator: '{}' -> '{}'", locator, candidate);
                    return;
                } catch (RuntimeException healFailure) {
                    log.warn("Healing candidate '{}' also failed: {}", candidate, healFailure.getMessage());
                }
            }

            throw retryFailure;
        }
    }

    private void executeNavigation(String description, Runnable attempt) {

        try {
            executeNavigationAttempts(description, attempt);
        } finally {
            captureScreenshot();
        }
    }

    private void executeNavigationAttempts(String description, Runnable attempt) {

        try {
            attempt.run();
            return;
        } catch (RuntimeException firstFailure) {
            log.warn("{} failed, retrying: {}", description, firstFailure.getMessage());
        }

        sleep();
        attempt.run();
    }

    /**
     * Captures one screenshot per logical action, regardless of how many
     * retry/heal attempts it took internally, or whether it ultimately
     * succeeded or failed — a screenshot of the failure is useful
     * evidence too. Never lets a capture failure (e.g. the page already
     * crashed) mask the real action's own outcome.
     */
    private void captureScreenshot() {

        try {
            byte[] png = delegate.screenshotPng();
            if (png.length > 0) {
                screenshots.add(new ScreenshotSample(Instant.now(), png));
            }
        } catch (RuntimeException e) {
            log.warn("Screenshot capture failed: {}", e.getMessage());
        }
    }

    private void sleep() {
        try {
            Thread.sleep(retryDelay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface Attempt {
        void run(String locator);
    }

    @FunctionalInterface
    private interface TimedAttempt {
        void run(String locator, Duration timeout);
    }
}
