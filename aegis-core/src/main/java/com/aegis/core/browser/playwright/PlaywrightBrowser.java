package com.aegis.core.browser.playwright;

import com.aegis.core.browser.Browser;
import com.aegis.core.browser.BrowserConfig;
import com.aegis.core.browser.SignalRecorder;
import com.aegis.core.knowledge.BoundingBox;
import com.aegis.core.knowledge.ElementSnapshot;
import com.aegis.core.plugin.AuthenticatedSession;
import com.aegis.model.finding.FindingSeverity;
import com.aegis.model.observation.AnomalySignal;
import com.aegis.model.observation.ElementInfo;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaywrightBrowser implements Browser {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightBrowser.class);

    /**
     * iframe support: a locator discovered inside an actual {@code <iframe>}
     * (not the top-level document) is prefixed with the index of its frame
     * in {@code page.frames()} — which already lists the main frame at
     * index 0, so main-frame locators need no special case, only no prefix.
     * Every existing locator this class ever produced stays exactly as-is
     * (unprefixed) — this only activates for genuinely framed elements.
     */
    private static final Pattern FRAME_PREFIX = Pattern.compile("^frame:(\\d+)>(.*)$", Pattern.DOTALL);

    private final List<AnomalySignal> anomalies = new CopyOnWriteArrayList<>();
    private final BrowserConfig config;

    /**
     * Bounded number of actions the mission's own reasoning loop gets to
     * spend on a same-domain tab opened mid-mission (see {@link
     * #handleNewPage}) before this class transparently switches back to
     * the parent tab. A defensible starting default, not tuned against
     * real usage — same discipline as every other adjustable constant in
     * this codebase (e.g. {@code ExperienceScoreCatalogProvider}'s
     * severity weights).
     */
    private static final int CHILD_TAB_ACTION_BUDGET = 3;

    private Playwright playwright;
    private com.microsoft.playwright.Browser browser;
    private Page page;

    /** Non-null exactly while {@link #page} is a same-domain child tab being explored — the tab to return to once {@link #childTabActionsRemaining} runs out. */
    private Page parentPage;
    private int childTabActionsRemaining;

    /** Set by applySession() when a session carries storage data — applied after the mission's first real navigation, see navigate(). */
    private AuthenticatedSession pendingStorageSession;

    public PlaywrightBrowser() {
        this(BrowserConfig.defaults());
    }

    public PlaywrightBrowser(BrowserConfig config) {
        this.config = config;
    }

    @Override
    public void launch() {

        playwright = Playwright.create();

        browser = engineFor(config.type()).launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(config.headless())
        );

        page = browser.newPage();

        registerAnomalyListeners();
        page.context().onPage(this::handleNewPage);
    }

    private BrowserType engineFor(String type) {
        return switch (type.toLowerCase()) {
            case "firefox" -> playwright.firefox();
            case "webkit" -> playwright.webkit();
            case "chromium" -> playwright.chromium();
            default -> throw new IllegalArgumentException(
                    "Unknown browser type: " + type + " (expected chromium, firefox, or webkit)");
        };
    }

    @Override
    public void applySession(AuthenticatedSession session) {

        if (session.browserProfilePath() != null && !session.browserProfilePath().isBlank()) {
            relaunchWithPersistentProfile(session.browserProfilePath());
        }

        if (!session.cookies().isEmpty()) {

            List<Cookie> cookies = session.cookies().stream()
                    .map(c -> {
                        Cookie cookie = new Cookie(c.name(), c.value());
                        if (c.domain() != null) {
                            cookie.setDomain(c.domain());
                        }
                        if (c.path() != null) {
                            cookie.setPath(c.path());
                        }
                        return cookie;
                    })
                    .toList();

            page.context().addCookies(cookies);
        }

        if (!session.headers().isEmpty()) {
            page.context().setExtraHTTPHeaders(session.headers());
        }

        // localStorage/sessionStorage are origin-scoped — the browser is
        // still on about:blank at this point (applySession runs right
        // after launch(), before any navigation), so there's no valid
        // origin to write them against yet. Deferred until the mission's
        // own first navigate() call, once a real origin exists.
        if (!session.localStorage().isEmpty() || !session.sessionStorage().isEmpty()) {
            pendingStorageSession = session;
        }
    }

    /**
     * Playwright only exposes a persistent (reusable) browser profile via
     * launchPersistentContext, a different entry point than the
     * launch()+newPage() pair already used — so a profile-based session
     * means discarding the just-launched browser and relaunching this way
     * instead. Safe to do here specifically because applySession() is
     * documented to run before any mission activity: nothing has
     * navigated or observed anything yet.
     */
    private void relaunchWithPersistentProfile(String profilePath) {

        if (browser != null) {
            browser.close();
        }

        com.microsoft.playwright.BrowserContext context = engineFor(config.type()).launchPersistentContext(
                Path.of(profilePath),
                new BrowserType.LaunchPersistentContextOptions().setHeadless(config.headless())
        );

        browser = context.browser();
        page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);

        registerAnomalyListeners();
        context.onPage(this::handleNewPage);
    }

    private void registerAnomalyListeners() {

        page.onConsoleMessage(message -> {
            if ("error".equals(message.type())) {
                anomalies.add(new AnomalySignal(
                        "CONSOLE_ERROR", message.text(), page.url(), Instant.now()));
            }
        });

        page.onPageError(error ->
                anomalies.add(new AnomalySignal(
                        "PAGE_ERROR", error, page.url(), Instant.now())));

        page.onRequestFailed(request ->
                anomalies.add(new AnomalySignal(
                        "REQUEST_FAILED", request.failure() + " — " + request.url(), page.url(), Instant.now())));

        page.onCrash(crashedPage ->
                anomalies.add(new AnomalySignal(
                        "CRASH", "Page crashed", crashedPage.url(), Instant.now())));

        /*
         * Without a listener, Playwright silently auto-dismisses native
         * dialogs (alert/confirm/prompt/beforeunload) — the action that
         * triggered one just appears to do nothing, with zero record of
         * why. This makes that visible (as an anomaly, same pipeline as
         * console/page errors).
         *
         * "prompt" is accepted with a blank answer rather than dismissed:
         * dismiss() returns null to the calling script, which aborts most
         * prompt-gated flows outright, so the agent could never explore
         * past one. Accepting with "" still lets the flow continue while
         * committing to nothing.
         *
         * "confirm"/"beforeunload" stay dismissed: accepting would be
         * needed to reach some flows (e.g. a "delete this?" confirm), but
         * auto-accepting is a real-damage risk against a live site under
         * test, so those stay conservative rather than optimizing for
         * coverage. "alert" has no real choice either way — dismiss() is
         * kept for consistency with today's behavior.
         */
        page.onDialog(dialog -> {

            anomalies.add(new AnomalySignal(
                    "DIALOG", dialog.type() + ": " + dialog.message(), page.url(), Instant.now()));

            if ("prompt".equals(dialog.type())) {
                dialog.accept("");
            } else {
                dialog.dismiss();
            }
        });
    }

    /**
     * Fires whenever ANY action opens a new tab/window in this browser
     * context (e.g. a {@code target="_blank"} link) — before this, that
     * tab was never tracked at all: {@link #page} kept pointing at the
     * original tab, so the new one was invisible to the Observer and just
     * leaked as an orphaned process for the rest of the mission.
     *
     * Same-origin tabs are genuinely part of the app under test (a "view
     * invoice" or "open in new tab" link, say) — folding those pages'
     * observations into the same mission/WorldModel is correct, not a
     * corruption, so this actually switches {@link #page} to the new tab
     * and lets the mission's own normal reasoning loop drive real
     * interaction there, same as any other page, for {@link
     * #CHILD_TAB_ACTION_BUDGET} actions (see {@link
     * #consumeChildTabBudgetIfActive}) before automatically returning to
     * the parent tab and closing the child. Cross-origin tabs (e.g. a
     * link out to a payment processor or an unrelated site) aren't part
     * of the app under test — those are closed immediately with no
     * exploration, and the parent tab is never interrupted.
     *
     * If a child tab is already being explored when another new page
     * opens, the new one is closed outright rather than nesting — this
     * class tracks at most one level of "current tab", not a stack.
     */
    private void handleNewPage(Page newPage) {

        if (parentPage != null) {
            newPage.close();
            return;
        }

        try {
            newPage.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
        } catch (Exception e) {
            log.warn("New tab failed to settle, evaluating it anyway. URL: {}", newPage.url());
        }

        String parentHost = hostOf(page.url());
        String childHost = hostOf(newPage.url());

        if (parentHost == null || !parentHost.equals(childHost)) {
            newPage.close();
            return;
        }

        parentPage = page;
        page = newPage;
        childTabActionsRemaining = CHILD_TAB_ACTION_BUDGET;
        registerAnomalyListeners();
    }

    /**
     * Decrements the child-tab exploration budget after every mutating
     * action (this is called from {@link #settle}, the single chokepoint
     * every {@code click}/{@code type}/{@code select}/etc. already routes
     * through) and switches back to the parent tab once it's spent. A
     * no-op whenever {@link #page} isn't currently a child tab.
     */
    private void consumeChildTabBudgetIfActive() {

        if (parentPage == null) {
            return;
        }

        childTabActionsRemaining--;

        if (childTabActionsRemaining <= 0) {

            Page exploredTab = page;
            page = parentPage;
            parentPage = null;

            try {
                exploredTab.close();
            } catch (Exception e) {
                log.warn("Failed to close explored tab: {}", e.getMessage());
            }
        }
    }

    private String hostOf(String url) {
        try {
            return java.net.URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * A second, independent set of listeners on top of {@link
     * #registerAnomalyListeners()} — Playwright's {@code on*}/{@code off*}
     * pairing is standard multi-listener event registration, so this
     * doesn't touch or risk the existing anomaly-signal wiring. Passive:
     * these listeners only observe, they never change what the browser
     * does. {@code onResponse} only records 4xx/5xx — a successful
     * response is not a signal.
     */
    @Override
    public void attachSignalRecorder(SignalRecorder recorder) {

        page.onConsoleMessage(message -> {

            FindingSeverity level = switch (message.type()) {
                case "error" -> FindingSeverity.HIGH;
                case "warning" -> FindingSeverity.LOW;
                default -> null;
            };

            if (level != null) {
                recorder.recordConsole(level, message.text(), message.location(), page.url());
            }
        });

        page.onPageError(error ->
                recorder.recordConsole(FindingSeverity.CRITICAL, error, "", page.url()));

        page.onResponse(response -> {
            if (response.status() >= 400) {
                recorder.recordNetwork(
                        response.url(), response.status(), response.request().method(), response.request().resourceType(), page.url());
            }
        });

        page.onRequestFailed(request ->
                recorder.recordNetwork(request.url(), -1, request.method(), request.resourceType(), page.url()));
    }

    /**
     * Structural proxy only, not a real accessibility audit: {@code
     * accessibleName} falls back from {@code aria-label} to visible text,
     * not the full W3C accname computation. Returns {@code null} rather
     * than throwing when the locator can't be resolved (element detached/
     * gone since the observation that produced it) — a missed snapshot,
     * not a bug.
     */
    @Override
    public ElementSnapshot captureElementSnapshot(String locatorString) {

        try {

            Locator locator = resolveLocator(locatorString);

            com.microsoft.playwright.options.BoundingBox playwrightBox = locator.boundingBox();
            BoundingBox box = playwrightBox == null
                    ? null
                    : new BoundingBox(playwrightBox.x, playwrightBox.y, playwrightBox.width, playwrightBox.height);

            String accessibleName = safe(locator.getAttribute("aria-label"));
            if (accessibleName.isBlank()) {
                accessibleName = safe(locator.textContent()).trim();
            }

            // Resolved against the page's current URL: the raw attribute
            // can be relative ("/orders", "orders.html"), and a relative
            // string isn't independently probeable later by the Page
            // Inspection Layer's link checker, which has no page context
            // of its own by the time it runs post-hoc.
            String href = locator.getAttribute("href");
            List<String> hrefs = List.of();
            if (href != null && !href.isBlank()) {
                try {
                    hrefs = List.of(java.net.URI.create(page.url()).resolve(href).toString());
                } catch (Exception e) {
                    hrefs = List.of(href);
                }
            }

            Object styleResult = locator.evaluate(
                    "el => { const s = getComputedStyle(el); return { color: s.color, background: s.backgroundColor, "
                            + "fontSize: parseFloat(s.fontSize), truncated: el.scrollWidth > el.clientWidth || el.scrollHeight > el.clientHeight }; }");

            if (!(styleResult instanceof Map<?, ?> style)) {
                return new ElementSnapshot(locatorString, accessibleName, hrefs, box, null, null, 0, false);
            }

            return new ElementSnapshot(
                    locatorString,
                    accessibleName,
                    hrefs,
                    box,
                    String.valueOf(style.get("color")),
                    String.valueOf(style.get("background")),
                    style.get("fontSize") instanceof Number number ? number.doubleValue() : 0,
                    Boolean.TRUE.equals(style.get("truncated"))
            );

        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<AnomalySignal> drainAnomalies() {

        List<AnomalySignal> drained = new ArrayList<>(anomalies);
        anomalies.removeAll(drained);
        return drained;
    }

    /**
     * {@code settle()} (used after every action) only waits for
     * DOMCONTENTLOADED, deliberately — see its own Javadoc. That's fine
     * for the next observation/action, but a screenshot taken right after
     * can catch an SPA still mid-render: the HTML parsed, but the JS that
     * actually paints the page hasn't run yet, so the capture comes back
     * blank/white. A bounded NETWORKIDLE wait right before reading pixels
     * gives that render a chance to finish — returns immediately if the
     * page's already quiet, and gives up after 1s rather than hanging a
     * screenshot on a page with a persistent websocket/long-poll that
     * never truly goes idle.
     */
    @Override
    public byte[] screenshotPng() {

        try {
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(1000));
        } catch (Exception e) {
            log.debug("Page didn't reach network-idle before screenshot; capturing anyway: {}", e.getMessage());
        }

        return page.screenshot();
    }

    @Override
    public void close() {

        if (browser != null) {
            browser.close();
        }

        if (playwright != null) {
            playwright.close();
        }
    }

    @Override
    public void navigate(String url) {

        try {

            // NETWORKIDLE, not DOMCONTENTLOADED: this is the one moment a
            // full page's JS bundle loads from scratch, and
            // DOMCONTENTLOADED alone can fire before a JS-heavy SPA has
            // actually rendered anything — confirmed live against
            // OrangeHRM's Vue app, which has zero real inputs/buttons at
            // DOMCONTENTLOADED but the real login form once network
            // activity settles. settle() (used after every other action)
            // deliberately stays at the faster DOMCONTENTLOADED — later
            // actions within an already-loaded SPA don't re-fetch its
            // bundle, so there's no reason to pay this cost every time.
            page.navigate(
                    url,
                    new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.NETWORKIDLE)
                            .setTimeout(30000)
            );

        } catch (Exception e) {

            log.warn("Navigation timeout. Current URL: {}", page.url());

            throw e;
        }

        if (pendingStorageSession != null) {
            applyPendingStorage();
            pendingStorageSession = null;
        }
    }

    /**
     * Writes any localStorage/sessionStorage from a session applied
     * before the mission started, now that a real origin exists, then
     * reloads — many apps only read storage once at boot, so writing it
     * without a reload wouldn't be picked up by app code that already ran.
     * Applied exactly once, on the mission's first navigation.
     */
    private void applyPendingStorage() {

        try {

            for (Map.Entry<String, String> entry : pendingStorageSession.localStorage().entrySet()) {
                page.evaluate("([k, v]) => localStorage.setItem(k, v)", List.of(entry.getKey(), entry.getValue()));
            }

            for (Map.Entry<String, String> entry : pendingStorageSession.sessionStorage().entrySet()) {
                page.evaluate("([k, v]) => sessionStorage.setItem(k, v)", List.of(entry.getKey(), entry.getValue()));
            }

            page.reload(new Page.ReloadOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));

        } catch (Exception e) {
            log.warn("Failed to apply session storage: {}", e.getMessage());
        }
    }

    @Override
    public void refresh() {
        page.reload();
        settle();
    }

    @Override
    public void goBack() {
        page.goBack();
        settle();
    }

    @Override
    public void click(String locator) {
        resolveLocator(locator).click();
        settle();
    }

    @Override
    public void click(String locator, Duration timeout) {
        resolveLocator(locator).click(new Locator.ClickOptions().setTimeout(timeout.toMillis()));
        settle();
    }

    @Override
    public void doubleClick(String locator) {
        resolveLocator(locator).dblclick();
        settle();
    }

    @Override
    public void doubleClick(String locator, Duration timeout) {
        resolveLocator(locator).dblclick(new Locator.DblclickOptions().setTimeout(timeout.toMillis()));
        settle();
    }

    @Override
    public void raceClick(String locator) {
        resolveLocator(locator).evaluate("el => { el.click(); el.click(); }");
        settle();
    }

    @Override
    public void type(String locator, String text) {
        resolveLocator(locator).fill(text);
        settle();
    }

    @Override
    public void type(String locator, String text, Duration timeout) {
        resolveLocator(locator).fill(text, new Locator.FillOptions().setTimeout(timeout.toMillis()));
        settle();
    }

    @Override
    public void select(String locator) {
        select(resolveLocator(locator), new Locator.SelectOptionOptions());
    }

    @Override
    public void select(String locator, Duration timeout) {
        select(resolveLocator(locator), new Locator.SelectOptionOptions().setTimeout(timeout.toMillis()));
    }

    private void select(Locator select, Locator.SelectOptionOptions options) {

        List<String> values = select.locator("option").all().stream()
                .map(option -> option.getAttribute("value"))
                .filter(value -> value != null && !value.isBlank())
                .toList();

        if (!values.isEmpty()) {
            select.selectOption(values.get(0), options);
        }

        settle();
    }

    @Override
    public void scrollTo(String locator) {
        resolveLocator(locator).scrollIntoViewIfNeeded();
    }

    @Override
    public void scrollTo(String locator, Duration timeout) {
        resolveLocator(locator)
                .scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(timeout.toMillis()));
    }

    /**
     * Waits for the page to reach a stable load state after an action.
     * Any of click/type/select can trigger navigation (a link, a form's
     * onchange submit, a JS handler) — without this, the next
     * observation can catch the page mid-navigation and see an empty
     * DOM, which crashed a real run (see git history: 0-element
     * observation immediately after a click that navigated home).
     * Harmless to call after an action that didn't navigate — the wait
     * returns immediately since the page is already settled.
     */
    private void settle() {

        try {
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        } catch (Exception e) {
            log.warn("Timed out waiting for page to settle after an action. Current URL: {}", page.url());
        }

        consumeChildTabBudgetIfActive();
    }

    /**
     * {@code Locator.isVisible()} only checks CSS visibility (not {@code
     * display:none}/{@code visibility:hidden}, has a bounding box) — it
     * does NOT know whether something else is sitting on top of it. A
     * background button behind an open modal overlay reports {@code
     * isVisible()==true} even though clicking it would just hit the
     * backdrop. This adds the same topmost-hit-test Playwright's own
     * {@code click()} actionability check does internally (but doesn't
     * expose as a queryable method) — {@code document.elementFromPoint}
     * at the element's own center, checked against the element itself.
     * Runs inside the element's own frame automatically ({@code
     * Locator.evaluate} executes in the frame the locator belongs to),
     * so this is correct for framed elements with no extra handling.
     *
     * Deliberately folded into the existing {@code visible} field on
     * {@link ElementInfo} rather than adding a new one: {@code
     * DefaultElementActionMapper} already skips any element where {@code
     * !visible()} — every downstream consumer that already treats
     * "not visible" as "not a candidate" gets modal-awareness for free,
     * with no changes needed there.
     */
    private boolean isActionable(Locator element) {

        if (!element.isVisible()) {
            return false;
        }

        try {
            return Boolean.TRUE.equals(element.evaluate("""
                    el => {
                        const r = el.getBoundingClientRect();
                        const cx = r.left + r.width / 2;
                        const cy = r.top + r.height / 2;
                        const top = document.elementFromPoint(cx, cy);
                        return top !== null && (top === el || el.contains(top));
                    }
                    """));
        } catch (Exception e) {
            // Detached/gone between the isVisible() check and this call —
            // treat as not actionable, same as any other vanished element.
            return false;
        }
    }

    @Override
    public String getPageTitle() {
        return page.title();
    }

    @Override
    public String getCurrentUrl() {
        return page.url();
    }

    /**
     * iframe support: every discovery method loops {@code page.frames()}
     * rather than querying the top-level page alone — index 0 in that list
     * already *is* the main frame, so no separate top-level pass is needed.
     * Elements found in the main frame produce exactly the locators this
     * class always produced; elements found inside an actual {@code
     * <iframe>} get a {@code frame:N>} prefix via {@link
     * #buildBestLocator(int, String, Locator, String, String, int)} so
     * {@link #resolveLocator} can find them again later.
     */
    @Override
    public List<ElementInfo> getButtons() {

        List<ElementInfo> buttons = new ArrayList<>();
        List<Frame> frames = page.frames();

        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {

            Locator locator = frames.get(frameIndex).locator("button");

            for (int i = 0; i < locator.count(); i++) {

                Locator button = locator.nth(i);

                String id = safe(button.getAttribute("id"));
                String name = safe(button.getAttribute("name"));
                String text = safe(button.textContent()).trim();
                String type = normalizeButtonType(button.getAttribute("type"));

                String bestLocator = buildBestLocator(frameIndex, "button", button, id, name, i);

                buttons.add(new ElementInfo(
                        "button",
                        id,
                        name,
                        text,
                        type,
                        "",
                        isActionable(button),
                        button.isEnabled(),
                        bestLocator
                ));
            }
        }

        return buttons;
    }

    @Override
    public List<ElementInfo> getInputs() {

        List<ElementInfo> inputs = new ArrayList<>();
        List<Frame> frames = page.frames();

        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {

            Locator locator = frames.get(frameIndex).locator("input");

            for (int i = 0; i < locator.count(); i++) {

                Locator input = locator.nth(i);

                String id = safe(input.getAttribute("id"));
                String name = safe(input.getAttribute("name"));
                String type = normalizeInputType(input.getAttribute("type"));

                String bestLocator = buildBestLocator(frameIndex, "input", input, id, name, i);

                inputs.add(new ElementInfo(
                        "input",
                        id,
                        name,
                        "",
                        type,
                        safe(input.inputValue()),
                        isActionable(input),
                        input.isEnabled(),
                        bestLocator
                ));
            }
        }

        return inputs;
    }

    @Override
    public List<ElementInfo> getLinks() {

        List<ElementInfo> links = new ArrayList<>();
        List<Frame> frames = page.frames();

        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {

            Locator locator = frames.get(frameIndex).locator("a");

            for (int i = 0; i < locator.count(); i++) {

                Locator link = locator.nth(i);

                String id = safe(link.getAttribute("id"));
                String name = safe(link.getAttribute("name"));
                String text = safe(link.textContent()).trim();

                String bestLocator = buildBestLocator(frameIndex, "a", link, id, name, i);

                links.add(new ElementInfo(
                        "a",
                        id,
                        name,
                        text,
                        "link",
                        "",
                        isActionable(link),
                        link.isEnabled(),
                        bestLocator
                ));
            }
        }

        return links;
    }

    @Override
    public List<ElementInfo> getSelects() {

        List<ElementInfo> selects = new ArrayList<>();
        List<Frame> frames = page.frames();

        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {

            Locator locator = frames.get(frameIndex).locator("select");

            for (int i = 0; i < locator.count(); i++) {

                Locator select = locator.nth(i);

                String id = safe(select.getAttribute("id"));
                String name = safe(select.getAttribute("name"));

                String bestLocator = buildBestLocator(frameIndex, "select", select, id, name, i);

                selects.add(new ElementInfo(
                        "select",
                        id,
                        name,
                        "",
                        "select",
                        "",
                        isActionable(select),
                        select.isEnabled(),
                        bestLocator
                ));
            }
        }

        return selects;
    }

    /**
     * The single chokepoint every locator-consuming method routes through.
     * A plain locator (no prefix) resolves against the top-level page,
     * exactly as before. A {@code frame:N>...} locator (see {@link
     * #FRAME_PREFIX}) resolves against that frame instead — thrown as an
     * {@link IllegalStateException} (a {@code RuntimeException}) when frame
     * N no longer exists, deliberately: {@code SelfHealingBrowser} already
     * retries/heals on any {@code RuntimeException}, so a frame that's gone
     * stale between observation and action gets the same treatment a
     * disappeared element already gets today, with no changes needed there.
     */
    private Locator resolveLocator(String raw) {

        Matcher framePrefix = FRAME_PREFIX.matcher(raw);

        if (!framePrefix.matches()) {
            return page.locator(raw);
        }

        List<Frame> frames = page.frames();
        int frameIndex = Integer.parseInt(framePrefix.group(1));

        if (frameIndex >= frames.size()) {
            throw new IllegalStateException(
                    "Frame " + frameIndex + " no longer exists (page currently has " + frames.size() + " frame(s))");
        }

        return frames.get(frameIndex).locator(framePrefix.group(2));
    }

    /**
     * Returns a locator guaranteed to match exactly this one element, tried
     * in order of preference: {@code id}, {@code name}, {@code data-testid},
     * {@code aria-label}, {@code placeholder}, then finally a purely
     * positional {@code :nth-match} fallback. The first four are real DOM
     * attributes AEGIS reads directly off the element; {@code data-testid}
     * and {@code aria-label} in particular are often more stable across
     * deploys than an id a framework regenerates, and reaching for them
     * before falling back to position means fewer elements ever need the
     * weakest, purely-positional shape at all.
     *
     * None of the four can be trusted to be unique without checking: even
     * id and name have known real-world collision patterns (malformed HTML
     * duplicating an id; ASP.NET MVC pairing a checkbox with a hidden input
     * of the same name so unchecked boxes still submit "false"), so this
     * verifies against the live page rather than assuming any of them is
     * safe — same reasoning now applied uniformly to all four instead of
     * just the original two.
     *
     * Every value is embedded as a quoted attribute value ([attr='...']),
     * not a bare CSS identifier (#...): real ids can contain characters
     * that are syntactically invalid after a bare '#' — e.g. saucedemo.com
     * uses ids like "add-to-cart-test.allthethings()-t-shirt-(red)", where
     * the unescaped '.' and '(' broke every observation on that page.
     * Inside a quoted attribute value only the quote character and
     * backslash need escaping, so this handles those cases without needing
     * full CSS-identifier escaping.
     *
     * The final fallback, :nth-match, is a Playwright CSS extension that
     * indexes globally across the whole page — unlike native
     * :nth-of-type, which is per-parent and previously caused a
     * strict-mode crash on a page with 61 links spread across many
     * different parent containers.
     *
     * {@code frameIndex} is the same index {@code page.frames()} uses (0 =
     * main frame). The uniqueness probe ({@link #matchesExactlyOne}) must
     * run against that same frame's own document — an attribute value
     * unique within an iframe's document could coincidentally collide with
     * one elsewhere on the page, and vice versa, since each frame is a
     * genuinely separate document. The returned locator only gets the
     * {@code frame:N>} prefix when {@code frameIndex != 0}, so every
     * main-frame locator stays byte-for-byte identical to what this method
     * always produced.
     */
    private String buildBestLocator(int frameIndex, String tag, Locator element, String id, String name, int index) {

        String prefix = frameIndex == 0 ? "" : "frame:" + frameIndex + ">";

        String[][] attributeCandidates = {
                {"id", id},
                {"name", name},
                {"data-testid", safe(element.getAttribute("data-testid"))},
                {"aria-label", safe(element.getAttribute("aria-label"))},
                {"placeholder", safe(element.getAttribute("placeholder"))},
        };

        for (String[] candidate : attributeCandidates) {

            String value = candidate[1];
            if (value.isBlank()) {
                continue;
            }

            String attributeLocator = "[" + candidate[0] + "='" + escapeAttributeValue(value) + "']";
            if (matchesExactlyOne(frameIndex, attributeLocator)) {
                return prefix + attributeLocator;
            }
        }

        return prefix + ":nth-match(" + tag + ", " + (index + 1) + ")";
    }

    private String escapeAttributeValue(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private boolean matchesExactlyOne(int frameIndex, String locator) {
        return page.frames().get(frameIndex).locator(locator).count() == 1;
    }

    /**
     * Converts null values to empty strings.
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * An {@code <input>} with no {@code type} attribute at all is a real,
     * valid, common HTML pattern (every browser treats it as
     * {@code type="text"} per spec) — but the raw DOM attribute read
     * comes back {@code null}/blank in that case, not "text". Left
     * unnormalized, that blank string doesn't match any known type
     * anywhere downstream (DefaultElementActionMapper's switch,
     * DefaultInputValueResolver's field-shape heuristics, ...), so the
     * field silently gets zero candidate actions generated for it —
     * confirmed live against OrangeHRM's username field, which uses
     * exactly this pattern.
     */
    private String normalizeInputType(String type) {
        return (type == null || type.isBlank()) ? "text" : type;
    }

    /**
     * Same gap as {@link #normalizeInputType}, for {@code <button>}: no
     * {@code type} attribute at all is real, valid, common HTML (found
     * live while verifying iframe support — a bare {@code <button
     * onclick="...">} with no {@code type=} generated zero candidates,
     * since {@code DefaultElementActionMapper} only recognizes the literal
     * strings "BUTTON"/"SUBMIT"). Defaults to "button", not "submit" —
     * this label only needs to make the mapper recognize the element as
     * clickable, and every {@code <button>} tag is clickable regardless of
     * its type, so it doesn't need the form-submission-specific default a
     * real browser would apply.
     */
    private String normalizeButtonType(String type) {
        return (type == null || type.isBlank()) ? "button" : type;
    }
}