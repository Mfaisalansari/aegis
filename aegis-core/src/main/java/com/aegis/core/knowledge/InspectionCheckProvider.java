package com.aegis.core.knowledge;

import com.aegis.model.finding.FindingSeverity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Built-in {@link KnowledgeProvider} for {@link InspectionCatalog} — the
 * Page Inspection Layer's analysis half. Reads whatever {@code
 * SignalRecorder} captured live ({@link KnowledgeBuildContext#signals()})
 * and turns it into findings: console errors/exceptions, network
 * failures, broken links (opt-in, post-run only), and structural UI
 * checks (contrast, accessible name, zero-size elements, text overflow —
 * all require {@link InspectionConfig#captureDom()} to have been on, so
 * {@link SignalLog#dom()} is empty and these simply produce nothing when
 * it wasn't). Console/network checks need no DOM capture at all — the
 * listeners that feed them are always attached, cheap and passive.
 */
public final class InspectionCheckProvider implements KnowledgeProvider {

    private static final int MAX_LINKS_TO_PROBE = 50;
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(5);

    private static final Pattern RGB_PATTERN =
            Pattern.compile("rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*(?:,\\s*[\\d.]+)?\\s*\\)");

    @Override
    public InspectionCatalog provide(KnowledgeBuildContext context) {

        StateCatalog stateCatalog = context.partialBase().require(StateCatalog.class);
        KnowledgeConfig config = context.config() == null ? KnowledgeConfig.empty() : context.config();
        InspectionConfig inspectionConfig = config.inspection();
        SignalLog signals = context.signals() == null ? SignalLog.empty() : context.signals();

        List<InspectionFinding> findings = new ArrayList<>();
        findings.addAll(detectConsoleSignals(signals.console(), inspectionConfig));
        findings.addAll(detectNetworkSignals(signals.network()));
        findings.addAll(detectBrokenLinks(signals.dom(), stateCatalog, inspectionConfig));
        findings.addAll(detectLowContrast(signals.dom(), inspectionConfig.contrastThreshold()));
        findings.addAll(detectMissingAccessibleNames(signals.dom()));
        findings.addAll(detectZeroSizeElements(signals.dom()));
        findings.addAll(detectTextOverflow(signals.dom()));

        return new InspectionCatalog(findings);
    }

    // --- Console (live-only) ---

    private List<InspectionFinding> detectConsoleSignals(List<ConsoleSignal> consoleSignals, InspectionConfig config) {

        List<InspectionFinding> findings = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<Pattern> denyPatterns = compileDenyPatterns(config.noiseDenyPatterns());

        for (ConsoleSignal signal : consoleSignals) {

            if (signal.level() == FindingSeverity.LOW && !config.consoleWarnings()) {
                continue;
            }

            if (matchesAnyDenyPattern(signal.text(), denyPatterns)) {
                continue;
            }

            if (!seen.add(signal.text() + "|" + signal.location())) {
                continue;
            }

            // pageerror (uncaught exceptions) are recorded with no
            // location by attachSignalRecorder — console.error/warning
            // always carry Playwright's ConsoleMessage.location(), even
            // if it's an empty string for a script-less message.
            boolean isUncaughtException = signal.level() == FindingSeverity.CRITICAL;

            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("pageUrl", signal.pageUrl());
            if (signal.location() != null && !signal.location().isBlank()) {
                metadata.put("location", signal.location());
            }

            findings.add(new InspectionFinding(
                    isUncaughtException ? InspectionCheckType.UNCAUGHT_EXCEPTION : InspectionCheckType.CONSOLE_ERROR,
                    signal.level(),
                    (isUncaughtException ? "Uncaught exception: " : "Console message: ") + signal.text(),
                    signal.pageUrl(),
                    metadata
            ));
        }

        return findings;
    }

    private List<Pattern> compileDenyPatterns(List<String> patterns) {

        List<Pattern> compiled = new ArrayList<>();

        for (String pattern : patterns) {
            try {
                compiled.add(Pattern.compile(pattern));
            } catch (RuntimeException e) {
                // A malformed operator-declared regex shouldn't fail the
                // whole run — it just doesn't suppress anything.
            }
        }

        return compiled;
    }

    private boolean matchesAnyDenyPattern(String text, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    // --- Network (passive capture) ---

    private List<InspectionFinding> detectNetworkSignals(List<NetworkSignal> networkSignals) {

        List<InspectionFinding> findings = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (NetworkSignal signal : networkSignals) {

            if (!seen.add(signal.requestUrl() + "|" + signal.status())) {
                continue;
            }

            FindingSeverity severity;
            String summary;

            if (signal.status() < 0) {
                severity = FindingSeverity.HIGH;
                summary = "Request failed to load: " + signal.requestUrl();
            } else if (signal.status() >= 500) {
                severity = FindingSeverity.HIGH;
                summary = "Server error (" + signal.status() + ") loading " + signal.requestUrl();
            } else {
                severity = FindingSeverity.MEDIUM;
                summary = "Client error (" + signal.status() + ") loading " + signal.requestUrl();
            }

            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("pageUrl", signal.pageUrl());
            metadata.put("method", signal.method());
            metadata.put("resourceType", signal.resourceType());
            metadata.put("status", String.valueOf(signal.status()));

            findings.add(new InspectionFinding(InspectionCheckType.NETWORK_FAILURE, severity, summary, signal.requestUrl(), metadata));
        }

        return findings;
    }

    // --- Broken links (opt-in active probe, post-run only) ---

    private List<InspectionFinding> detectBrokenLinks(List<DomSignal> domSignals, StateCatalog stateCatalog, InspectionConfig config) {

        if (!config.probeLinks()) {
            return List.of();
        }

        Map<String, String> urlByStateSignature = new HashMap<>();
        for (State state : stateCatalog.states()) {
            urlByStateSignature.put(state.stateSignature(), state.url());
        }

        String origin = null;
        Set<String> hrefsToProbe = new LinkedHashSet<>();

        for (DomSignal domSignal : domSignals) {

            String pageUrl = urlByStateSignature.get(domSignal.stateSignature());

            if (pageUrl != null && origin == null) {
                origin = hostOf(pageUrl);
            }

            for (ElementSnapshot element : domSignal.elements()) {
                for (String href : element.hrefs()) {
                    if (isSameOrigin(href, origin)) {
                        hrefsToProbe.add(href);
                    }
                }
            }
        }

        List<InspectionFinding> findings = new ArrayList<>();
        HttpClient client = HttpClient.newBuilder().connectTimeout(PROBE_TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();

        int probed = 0;
        for (String href : hrefsToProbe) {

            if (probed >= MAX_LINKS_TO_PROBE) {
                break;
            }
            probed++;

            Integer status = probe(client, href);

            if (status == null || status >= 400) {

                Map<String, String> metadata = new LinkedHashMap<>();
                metadata.put("status", status == null ? "unreachable" : String.valueOf(status));

                findings.add(new InspectionFinding(
                        InspectionCheckType.BROKEN_LINK, FindingSeverity.HIGH,
                        "In-app link appears broken: " + href, href, metadata));
            }
        }

        return findings;
    }

    /** HEAD first (cheaper); falls back to GET only if the server rejects HEAD outright (405). */
    private Integer probe(HttpClient client, String href) {

        try {

            URI uri = URI.create(href);

            HttpRequest headRequest = HttpRequest.newBuilder(uri).method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(PROBE_TIMEOUT).build();

            HttpResponse<Void> response = client.send(headRequest, HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() == 405) {
                HttpRequest getRequest = HttpRequest.newBuilder(uri).GET().timeout(PROBE_TIMEOUT).build();
                response = client.send(getRequest, HttpResponse.BodyHandlers.discarding());
            }

            return response.statusCode();

        } catch (Exception e) {
            return null;
        }
    }

    private boolean isSameOrigin(String href, String origin) {

        if (origin == null) {
            return false;
        }

        try {
            return origin.equalsIgnoreCase(URI.create(href).getHost());
        } catch (Exception e) {
            return false;
        }
    }

    private String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    // --- UI checks (DOM snapshot detectors, need captureDom) ---

    /**
     * A settled state gets a fresh {@link DomSignal} every time {@code
     * SignalCapturingObserver} observes it — including revisits (e.g. a
     * login page observed again after each field fill). An element's
     * geometry/style is static for that state, so without this, revisiting
     * a state N times would report the exact same defect N times. Keyed
     * by state + locator (not locator alone) since the same locator string
     * can legitimately refer to a different element on a different page.
     */
    private boolean firstTimeSeen(Set<String> seen, DomSignal domSignal, ElementSnapshot element) {
        return seen.add(domSignal.stateSignature() + "|" + element.locator());
    }

    /** Real WCAG contrast-ratio math on the captured color/background pair, not a proxy. */
    private List<InspectionFinding> detectLowContrast(List<DomSignal> domSignals, double threshold) {

        List<InspectionFinding> findings = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (DomSignal domSignal : domSignals) {
            for (ElementSnapshot element : domSignal.elements()) {

                Double ratio = contrastRatio(element.color(), element.background());

                if (ratio == null || ratio >= threshold || !firstTimeSeen(seen, domSignal, element)) {
                    continue;
                }

                Map<String, String> metadata = new LinkedHashMap<>();
                metadata.put("contrastRatio", String.format("%.2f", ratio));
                metadata.put("threshold", String.valueOf(threshold));

                findings.add(new InspectionFinding(
                        InspectionCheckType.LOW_CONTRAST, FindingSeverity.MEDIUM,
                        "Text contrast ratio " + String.format("%.2f", ratio) + ":1 is below the " + threshold + ":1 threshold",
                        element.locator(), metadata));
            }
        }

        return findings;
    }

    static Double contrastRatio(String colorCss, String backgroundCss) {

        double[] color = parseRgb(colorCss);
        double[] background = parseRgb(backgroundCss);

        if (color == null || background == null) {
            return null;
        }

        double l1 = relativeLuminance(color);
        double l2 = relativeLuminance(background);

        double lighter = Math.max(l1, l2);
        double darker = Math.min(l1, l2);

        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double[] parseRgb(String css) {

        if (css == null) {
            return null;
        }

        Matcher matcher = RGB_PATTERN.matcher(css);

        if (!matcher.matches()) {
            return null;
        }

        return new double[]{
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
        };
    }

    /** WCAG 2.x relative luminance formula. */
    private static double relativeLuminance(double[] rgb) {

        double[] linear = new double[3];

        for (int i = 0; i < 3; i++) {
            double channel = rgb[i] / 255.0;
            linear[i] = channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
        }

        return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
    }

    /**
     * Higher-fidelity than {@link UxAnalysisCatalogProvider}'s own
     * accessible-name check: this reads {@code ElementSnapshot.accessibleName}
     * (aria-label, falling back to visible text — resolved once at DOM-
     * snapshot time), rather than {@code UxFinding}'s cruder proxy over raw
     * {@code ElementInfo} fields. Only runs when DOM capture was on; {@link
     * UxAnalysisCatalogProvider}'s version stays available either way as
     * the always-on fallback.
     */
    private List<InspectionFinding> detectMissingAccessibleNames(List<DomSignal> domSignals) {

        List<InspectionFinding> findings = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (DomSignal domSignal : domSignals) {
            for (ElementSnapshot element : domSignal.elements()) {

                if (element.accessibleName() != null && !element.accessibleName().isBlank()) {
                    continue;
                }

                if (!firstTimeSeen(seen, domSignal, element)) {
                    continue;
                }

                findings.add(new InspectionFinding(
                        InspectionCheckType.MISSING_ACCESSIBLE_NAME, FindingSeverity.MEDIUM,
                        "Element has no discoverable accessible name (checked aria-label, then visible text)",
                        element.locator(), Map.of()));
            }
        }

        return findings;
    }

    private List<InspectionFinding> detectZeroSizeElements(List<DomSignal> domSignals) {

        List<InspectionFinding> findings = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (DomSignal domSignal : domSignals) {
            for (ElementSnapshot element : domSignal.elements()) {
                if (element.box() != null && element.box().isZeroArea() && firstTimeSeen(seen, domSignal, element)) {
                    findings.add(new InspectionFinding(
                            InspectionCheckType.ZERO_SIZE_ELEMENT, FindingSeverity.MEDIUM,
                            "Element has zero width or height — not visible to a real user despite being in the DOM",
                            element.locator(), Map.of()));
                }
            }
        }

        return findings;
    }

    private List<InspectionFinding> detectTextOverflow(List<DomSignal> domSignals) {

        List<InspectionFinding> findings = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (DomSignal domSignal : domSignals) {
            for (ElementSnapshot element : domSignal.elements()) {
                if (element.textTruncated() && firstTimeSeen(seen, domSignal, element)) {
                    findings.add(new InspectionFinding(
                            InspectionCheckType.TEXT_OVERFLOW, FindingSeverity.LOW,
                            "Element's text content overflows its visible area",
                            element.locator(), Map.of()));
                }
            }
        }

        return findings;
    }
}
