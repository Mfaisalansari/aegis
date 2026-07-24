package com.aegis.core.browser;

import com.aegis.model.observation.AnomalySignal;
import com.aegis.model.observation.ElementInfo;
import java.time.Duration;
import java.util.List;

public interface Browser {

    void launch();

    void close();

    void navigate(String url);

    /** Reloads the current page — used to probe how the app recovers from an interrupted workflow. */
    void refresh();

    /** Navigates back in browser history — same interruption-recovery purpose as {@link #refresh()}. */
    void goBack();

    void click(String locator);

    /**
     * Same as {@link #click(String)}, but bounds how long to wait for the
     * element before giving up. Only meaningful to a resilience layer
     * retrying against a short budget after an initial full-timeout
     * attempt already failed — the default just ignores the timeout and
     * defers to the single-arg behavior, so every existing caller is
     * unaffected.
     */
    default void click(String locator, Duration timeout) {
        click(locator);
    }

    /** Fires a real double-click event — probes for duplicate-submission bugs (e.g. a form that doesn't disable its submit button). */
    void doubleClick(String locator);

    /** See {@link #click(String, Duration)} — same bounded-wait purpose, for double-clicks. */
    default void doubleClick(String locator, Duration timeout) {
        doubleClick(locator);
    }

    /**
     * Fires two click events on the same element back-to-back, with no
     * wait between them, to probe for race conditions in whatever the
     * click triggers (e.g. two overlapping "place order" requests
     * reaching the server before either completes). Deliberately not
     * implemented via two separate Java-thread calls into Playwright —
     * a Page is not safe to drive from multiple threads concurrently —
     * so the double-firing happens inside a single evaluate() call,
     * entirely within the browser's own JS engine.
     */
    void raceClick(String locator);

    /**
     * See {@link #click(String, Duration)} — default ignores the timeout
     * since {@link #raceClick(String)} fires via a single JS evaluate()
     * rather than Playwright's actionability wait, so there's nothing
     * for a bounded wait to shorten.
     */
    default void raceClick(String locator, Duration timeout) {
        raceClick(locator);
    }

    void type(String locator, String text);

    /** See {@link #click(String, Duration)} — same bounded-wait purpose, for typing. */
    default void type(String locator, String text, Duration timeout) {
        type(locator, text);
    }

    /**
     * Picks a real (non-placeholder) option from a &lt;select&gt; and
     * selects it. Which option is picked is a browser-layer concern —
     * the available options aren't known until the live DOM is queried,
     * so there's nothing for a value resolver to decide in advance.
     */
    void select(String locator);

    /** See {@link #click(String, Duration)} — same bounded-wait purpose, for select. */
    default void select(String locator, Duration timeout) {
        select(locator);
    }

    void scrollTo(String locator);

    /** See {@link #click(String, Duration)} — same bounded-wait purpose, for scrollTo. */
    default void scrollTo(String locator, Duration timeout) {
        scrollTo(locator);
    }

    String getPageTitle();

    String getCurrentUrl();

    List<ElementInfo> getButtons();

    List<ElementInfo> getInputs();

    List<ElementInfo> getLinks();

    List<ElementInfo> getSelects();

    /**
     * Returns anomaly signals (console errors, page errors, failed
     * requests, crashes) captured since the last call, and clears them.
     */
    List<AnomalySignal> drainAnomalies();
}