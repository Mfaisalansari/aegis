package com.aegis.core.knowledge;

import com.aegis.model.finding.FindingSeverity;
import com.aegis.model.observation.Observation;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.aegis.core.knowledge.KnowledgeTestFixtures.observation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectionCheckProviderTest {

    private final InspectionCheckProvider provider = new InspectionCheckProvider();

    // --- Console ---

    @Test
    void pageErrorProducesAnUncaughtExceptionAtCriticalSeverity() {

        SignalLog log = new SignalLog(
                List.of(new ConsoleSignal(FindingSeverity.CRITICAL, "TypeError: x is undefined", "", "https://app/login", Instant.now())),
                List.of(), List.of());

        List<InspectionFinding> findings = findingsOfType(build(List.of(), KnowledgeConfig.empty(), log), InspectionCheckType.UNCAUGHT_EXCEPTION);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.CRITICAL, findings.get(0).severity());
    }

    @Test
    void consoleErrorProducesAConsoleErrorAtHighSeverity() {

        SignalLog log = new SignalLog(
                List.of(new ConsoleSignal(FindingSeverity.HIGH, "ReferenceError: foo is not defined", "app.js:10", "https://app/login", Instant.now())),
                List.of(), List.of());

        List<InspectionFinding> findings = findingsOfType(build(List.of(), KnowledgeConfig.empty(), log), InspectionCheckType.CONSOLE_ERROR);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.HIGH, findings.get(0).severity());
    }

    @Test
    void warningIsSuppressedByDefaultButEmittedWhenConfigured() {

        SignalLog log = new SignalLog(
                List.of(new ConsoleSignal(FindingSeverity.LOW, "Deprecated API used", "app.js:5", "https://app/login", Instant.now())),
                List.of(), List.of());

        assertTrue(findingsOfType(build(List.of(), KnowledgeConfig.empty(), log), InspectionCheckType.CONSOLE_ERROR).isEmpty());

        KnowledgeConfig config = configWithInspection(new InspectionConfig(false, true, 4.5, false, List.of()));
        assertEquals(1, findingsOfType(build(List.of(), config, log), InspectionCheckType.CONSOLE_ERROR).size());
    }

    @Test
    void duplicateConsoleSignalsAreReportedOnce() {

        ConsoleSignal signal = new ConsoleSignal(FindingSeverity.HIGH, "same error", "app.js:1", "https://app/login", Instant.now());
        SignalLog log = new SignalLog(List.of(signal, signal), List.of(), List.of());

        assertEquals(1, findingsOfType(build(List.of(), KnowledgeConfig.empty(), log), InspectionCheckType.CONSOLE_ERROR).size());
    }

    @Test
    void denyPatternSuppressesAMatchingConsoleSignal() {

        SignalLog log = new SignalLog(
                List.of(new ConsoleSignal(FindingSeverity.HIGH, "ThirdPartyWidget failed to load", "widget.js:1", "https://app/login", Instant.now())),
                List.of(), List.of());

        KnowledgeConfig config = configWithInspection(new InspectionConfig(false, false, 4.5, false, List.of("ThirdPartyWidget")));

        assertTrue(findingsOfType(build(List.of(), config, log), InspectionCheckType.CONSOLE_ERROR).isEmpty());
    }

    // --- Network ---

    @Test
    void clientErrorProducesMediumSeverity() {

        SignalLog log = new SignalLog(List.of(), List.of(new NetworkSignal("https://app/api/orders", 404, "GET", "xhr", "https://app/orders")), List.of());

        List<InspectionFinding> findings = findingsOfType(build(List.of(), KnowledgeConfig.empty(), log), InspectionCheckType.NETWORK_FAILURE);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.MEDIUM, findings.get(0).severity());
    }

    @Test
    void serverErrorProducesHighSeverity() {

        SignalLog log = new SignalLog(List.of(), List.of(new NetworkSignal("https://app/api/orders", 500, "GET", "xhr", "https://app/orders")), List.of());

        List<InspectionFinding> findings = findingsOfType(build(List.of(), KnowledgeConfig.empty(), log), InspectionCheckType.NETWORK_FAILURE);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.HIGH, findings.get(0).severity());
    }

    @Test
    void requestFailedProducesHighSeverity() {

        SignalLog log = new SignalLog(List.of(), List.of(new NetworkSignal("https://app/api/orders", -1, "GET", "xhr", "https://app/orders")), List.of());

        List<InspectionFinding> findings = findingsOfType(build(List.of(), KnowledgeConfig.empty(), log), InspectionCheckType.NETWORK_FAILURE);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.HIGH, findings.get(0).severity());
    }

    @Test
    void duplicateNetworkSignalsAreReportedOnce() {

        NetworkSignal signal = new NetworkSignal("https://app/api/orders", 404, "GET", "xhr", "https://app/orders");
        SignalLog log = new SignalLog(List.of(), List.of(signal, signal), List.of());

        assertEquals(1, findingsOfType(build(List.of(), KnowledgeConfig.empty(), log), InspectionCheckType.NETWORK_FAILURE).size());
    }

    // --- Broken links (real local HTTP server) ---

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {

        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/ok", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/missing", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void probeDisabledByDefaultProducesNoBrokenLinkFindings() {

        List<Observation> observations = List.of(observation("http://localhost:" + port + "/page", "Page", "a"));
        DomSignal dom = domSignalFor(observations.get(0), "http://localhost:" + port + "/missing");
        SignalLog log = new SignalLog(List.of(), List.of(), List.of(dom));

        assertTrue(findingsOfType(build(observations, KnowledgeConfig.empty(), log), InspectionCheckType.BROKEN_LINK).isEmpty());
    }

    @Test
    void probeEnabledDetectsABrokenSameOriginLinkAndIgnoresAWorkingOne() {

        List<Observation> observations = List.of(observation("http://localhost:" + port + "/page", "Page", "a"));

        DomSignal dom = new DomSignal(
                com.aegis.core.reasoning.memory.StateSignature.of(observations.get(0)),
                List.of(
                        linkSnapshot("http://localhost:" + port + "/ok"),
                        linkSnapshot("http://localhost:" + port + "/missing")
                ));

        SignalLog log = new SignalLog(List.of(), List.of(), List.of(dom));
        KnowledgeConfig config = configWithInspection(new InspectionConfig(false, false, 4.5, true, List.of()));

        List<InspectionFinding> findings = findingsOfType(build(observations, config, log), InspectionCheckType.BROKEN_LINK);

        assertEquals(1, findings.size());
        assertTrue(findings.get(0).evidence().endsWith("/missing"));
        assertEquals(FindingSeverity.HIGH, findings.get(0).severity());
    }

    @Test
    void probeEnabledSkipsCrossOriginLinks() {

        List<Observation> observations = List.of(observation("http://localhost:" + port + "/page", "Page", "a"));

        DomSignal dom = new DomSignal(
                com.aegis.core.reasoning.memory.StateSignature.of(observations.get(0)),
                List.of(linkSnapshot("http://a-completely-different-host.invalid/other")));

        SignalLog log = new SignalLog(List.of(), List.of(), List.of(dom));
        KnowledgeConfig config = configWithInspection(new InspectionConfig(false, false, 4.5, true, List.of()));

        assertTrue(findingsOfType(build(observations, config, log), InspectionCheckType.BROKEN_LINK).isEmpty());
    }

    private DomSignal domSignalFor(Observation observation, String href) {
        return new DomSignal(com.aegis.core.reasoning.memory.StateSignature.of(observation), List.of(linkSnapshot(href)));
    }

    private ElementSnapshot linkSnapshot(String href) {
        return new ElementSnapshot("a-link", "link text", List.of(href), null, null, null, 0, false);
    }

    // --- UI checks ---

    @Test
    void blackTextOnWhiteBackgroundHasMaximumContrastRatio() {
        Double ratio = InspectionCheckProvider.contrastRatio("rgb(0,0,0)", "rgb(255,255,255)");
        assertTrue(ratio > 20.9 && ratio < 21.1);
    }

    @Test
    void lowContrastElementIsFlagged() {

        DomSignal dom = new DomSignal("sig", List.of(
                new ElementSnapshot("x", "label", List.of(), null, "rgb(200,200,200)", "rgb(255,255,255)", 14, false)));

        SignalLog log = new SignalLog(List.of(), List.of(), List.of(dom));

        List<InspectionFinding> findings = findingsOfType(build(List.of(), KnowledgeConfig.empty(), log), InspectionCheckType.LOW_CONTRAST);

        assertEquals(1, findings.size());
    }

    @Test
    void sufficientContrastProducesNoFinding() {

        DomSignal dom = new DomSignal("sig", List.of(
                new ElementSnapshot("x", "label", List.of(), null, "rgb(0,0,0)", "rgb(255,255,255)", 14, false)));

        SignalLog log = new SignalLog(List.of(), List.of(), List.of(dom));

        assertTrue(findingsOfType(build(List.of(), KnowledgeConfig.empty(), log), InspectionCheckType.LOW_CONTRAST).isEmpty());
    }

    @Test
    void blankAccessibleNameIsFlagged() {

        DomSignal dom = new DomSignal("sig", List.of(
                new ElementSnapshot("x", "", List.of(), null, null, null, 0, false)));

        SignalLog log = new SignalLog(List.of(), List.of(), List.of(dom));

        assertEquals(1, findingsOfType(build(List.of(), KnowledgeConfig.empty(), log), InspectionCheckType.MISSING_ACCESSIBLE_NAME).size());
    }

    @Test
    void presentAccessibleNameProducesNoFinding() {

        DomSignal dom = new DomSignal("sig", List.of(
                new ElementSnapshot("x", "Submit", List.of(), null, null, null, 0, false)));

        SignalLog log = new SignalLog(List.of(), List.of(), List.of(dom));

        assertTrue(findingsOfType(build(List.of(), KnowledgeConfig.empty(), log), InspectionCheckType.MISSING_ACCESSIBLE_NAME).isEmpty());
    }

    @Test
    void zeroAreaBoundingBoxIsFlagged() {

        DomSignal dom = new DomSignal("sig", List.of(
                new ElementSnapshot("x", "label", List.of(), new BoundingBox(0, 0, 0, 0), null, null, 0, false)));

        SignalLog log = new SignalLog(List.of(), List.of(), List.of(dom));

        assertEquals(1, findingsOfType(build(List.of(), KnowledgeConfig.empty(), log), InspectionCheckType.ZERO_SIZE_ELEMENT).size());
    }

    @Test
    void nonZeroBoundingBoxProducesNoFinding() {

        DomSignal dom = new DomSignal("sig", List.of(
                new ElementSnapshot("x", "label", List.of(), new BoundingBox(0, 0, 100, 40), null, null, 0, false)));

        SignalLog log = new SignalLog(List.of(), List.of(), List.of(dom));

        assertTrue(findingsOfType(build(List.of(), KnowledgeConfig.empty(), log), InspectionCheckType.ZERO_SIZE_ELEMENT).isEmpty());
    }

    @Test
    void truncatedTextIsFlagged() {

        DomSignal dom = new DomSignal("sig", List.of(
                new ElementSnapshot("x", "label", List.of(), null, null, null, 0, true)));

        SignalLog log = new SignalLog(List.of(), List.of(), List.of(dom));

        List<InspectionFinding> findings = findingsOfType(build(List.of(), KnowledgeConfig.empty(), log), InspectionCheckType.TEXT_OVERFLOW);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.LOW, findings.get(0).severity());
    }

    // --- helpers ---

    private KnowledgeConfig configWithInspection(InspectionConfig inspection) {
        return new KnowledgeConfig(1, List.of(), List.of(), List.of(), inspection);
    }

    private List<InspectionFinding> findingsOfType(InspectionCatalog catalog, InspectionCheckType type) {
        return catalog.findings().stream().filter(f -> f.type() == type).toList();
    }

    private InspectionCatalog build(List<Observation> observations, KnowledgeConfig config, SignalLog signals) {

        StateCatalog stateCatalog = new StateCatalogProvider().provide(
                new KnowledgeBuildContext("Test", observations, List.of(), config, new KnowledgeBase(Map.of()), signals));

        KnowledgeBase partial = new KnowledgeBase(Map.of(StateCatalog.class, stateCatalog));

        return provider.provide(new KnowledgeBuildContext("Test", observations, List.of(), config, partial, signals));
    }
}
