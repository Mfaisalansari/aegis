package com.aegis.core.browser.playwright;

import com.aegis.core.browser.Browser;
import com.aegis.model.observation.AnomalySignal;
import com.aegis.model.observation.ElementInfo;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PlaywrightBrowser implements Browser {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightBrowser.class);

    private final List<AnomalySignal> anomalies = new CopyOnWriteArrayList<>();

    private Playwright playwright;
    private com.microsoft.playwright.Browser browser;
    private Page page;

    @Override
    public void launch() {

        playwright = Playwright.create();

        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(false)
        );

        page = browser.newPage();

        registerAnomalyListeners();
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

    @Override
    public List<AnomalySignal> drainAnomalies() {

        List<AnomalySignal> drained = new ArrayList<>(anomalies);
        anomalies.removeAll(drained);
        return drained;
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

            page.navigate(
                    url,
                    new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(30000)
            );

        } catch (Exception e) {

            log.warn("Navigation timeout. Current URL: {}", page.url());

            throw e;
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
        page.locator(locator).click();
        settle();
    }

    @Override
    public void click(String locator, Duration timeout) {
        page.locator(locator).click(new Locator.ClickOptions().setTimeout(timeout.toMillis()));
        settle();
    }

    @Override
    public void doubleClick(String locator) {
        page.locator(locator).dblclick();
        settle();
    }

    @Override
    public void doubleClick(String locator, Duration timeout) {
        page.locator(locator).dblclick(new Locator.DblclickOptions().setTimeout(timeout.toMillis()));
        settle();
    }

    @Override
    public void raceClick(String locator) {
        page.locator(locator).evaluate("el => { el.click(); el.click(); }");
        settle();
    }

    @Override
    public void type(String locator, String text) {
        page.locator(locator).fill(text);
        settle();
    }

    @Override
    public void type(String locator, String text, Duration timeout) {
        page.locator(locator).fill(text, new Locator.FillOptions().setTimeout(timeout.toMillis()));
        settle();
    }

    @Override
    public void select(String locator) {
        select(page.locator(locator), new Locator.SelectOptionOptions());
    }

    @Override
    public void select(String locator, Duration timeout) {
        select(page.locator(locator), new Locator.SelectOptionOptions().setTimeout(timeout.toMillis()));
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
        page.locator(locator).scrollIntoViewIfNeeded();
    }

    @Override
    public void scrollTo(String locator, Duration timeout) {
        page.locator(locator)
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
    }

    @Override
    public String getPageTitle() {
        return page.title();
    }

    @Override
    public String getCurrentUrl() {
        return page.url();
    }

    @Override
    public List<ElementInfo> getButtons() {

        List<ElementInfo> buttons = new ArrayList<>();

        Locator locator = page.locator("button");

        for (int i = 0; i < locator.count(); i++) {

            Locator button = locator.nth(i);

            String id = safe(button.getAttribute("id"));
            String name = safe(button.getAttribute("name"));
            String text = safe(button.textContent()).trim();
            String type = safe(button.getAttribute("type"));

            String bestLocator = buildBestLocator("button", id, name, i);

            buttons.add(new ElementInfo(
                    "button",
                    id,
                    name,
                    text,
                    type,
                    "",
                    button.isVisible(),
                    button.isEnabled(),
                    bestLocator
            ));
        }

        return buttons;
    }

    @Override
    public List<ElementInfo> getInputs() {

        List<ElementInfo> inputs = new ArrayList<>();

        Locator locator = page.locator("input");

        for (int i = 0; i < locator.count(); i++) {

            Locator input = locator.nth(i);

            String id = safe(input.getAttribute("id"));
            String name = safe(input.getAttribute("name"));
            String value = safe(input.getAttribute("value"));
            String type = safe(input.getAttribute("type"));

            String bestLocator = buildBestLocator("input", id, name, i);

            inputs.add(new ElementInfo(
                    "input",
                    id,
                    name,
                    "",
                    type,
                    safe(input.inputValue()),
                    input.isVisible(),
                    input.isEnabled(),
                    bestLocator
            ));
        }

        return inputs;
    }

    @Override
    public List<ElementInfo> getLinks() {

        List<ElementInfo> links = new ArrayList<>();

        Locator locator = page.locator("a");

        for (int i = 0; i < locator.count(); i++) {

            Locator link = locator.nth(i);

            String id = safe(link.getAttribute("id"));
            String name = safe(link.getAttribute("name"));
            String text = safe(link.textContent()).trim();

            String bestLocator = buildBestLocator("a", id, name, i);

            links.add(new ElementInfo(
                    "a",
                    id,
                    name,
                    text,
                    "link",
                    "",
                    link.isVisible(),
                    link.isEnabled(),
                    bestLocator
            ));
        }

        return links;
    }

    @Override
    public List<ElementInfo> getSelects() {

        List<ElementInfo> selects = new ArrayList<>();

        Locator locator = page.locator("select");

        for (int i = 0; i < locator.count(); i++) {

            Locator select = locator.nth(i);

            String id = safe(select.getAttribute("id"));
            String name = safe(select.getAttribute("name"));

            String bestLocator = buildBestLocator("select", id, name, i);

            selects.add(new ElementInfo(
                    "select",
                    id,
                    name,
                    "",
                    "select",
                    "",
                    select.isVisible(),
                    select.isEnabled(),
                    bestLocator
            ));
        }

        return selects;
    }

    /**
     * Returns a locator guaranteed to match exactly this one element.
     *
     * Neither id nor name can be trusted to be unique without checking:
     * malformed HTML can duplicate ids, and name collisions are a
     * standard pattern (e.g. ASP.NET MVC pairs a checkbox with a hidden
     * input of the same name so unchecked boxes still submit "false").
     * Both were observed on real sites during testing, so this verifies
     * against the live page rather than assuming either is safe.
     *
     * id/name are embedded as quoted attribute values ([id='...']), not
     * bare CSS identifiers (#...): real ids can contain characters that
     * are syntactically invalid after a bare '#' — e.g. saucedemo.com uses
     * ids like "add-to-cart-test.allthethings()-t-shirt-(red)", where the
     * unescaped '.' and '(' broke every observation on that page. Inside a
     * quoted attribute value only the quote character and backslash need
     * escaping, so this handles those cases without needing full
     * CSS-identifier escaping.
     *
     * The final fallback, :nth-match, is a Playwright CSS extension that
     * indexes globally across the whole page — unlike native
     * :nth-of-type, which is per-parent and previously caused a
     * strict-mode crash on a page with 61 links spread across many
     * different parent containers.
     */
    private String buildBestLocator(String tag, String id, String name, int index) {

        if (!id.isBlank()) {
            String idLocator = "[id='" + escapeAttributeValue(id) + "']";
            if (matchesExactlyOne(idLocator)) {
                return idLocator;
            }
        }

        if (!name.isBlank()) {
            String nameLocator = "[name='" + escapeAttributeValue(name) + "']";
            if (matchesExactlyOne(nameLocator)) {
                return nameLocator;
            }
        }

        return ":nth-match(" + tag + ", " + (index + 1) + ")";
    }

    private String escapeAttributeValue(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private boolean matchesExactlyOne(String locator) {
        return page.locator(locator).count() == 1;
    }

    /**
     * Converts null values to empty strings.
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }
}