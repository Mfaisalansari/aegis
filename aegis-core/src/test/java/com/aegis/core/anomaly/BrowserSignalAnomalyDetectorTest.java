package com.aegis.core.anomaly;

import com.aegis.core.browser.Browser;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.finding.Finding;
import com.aegis.model.finding.FindingSeverity;
import com.aegis.model.mission.Mission;
import com.aegis.model.observation.AnomalySignal;
import com.aegis.model.observation.ElementInfo;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserSignalAnomalyDetectorTest {

    @Test
    void noSignalsMeansNoFindings() {

        List<Finding> findings = detect(List.of());

        assertTrue(findings.isEmpty());
    }

    @Test
    void consoleErrorMapsToMediumSeverity() {

        List<Finding> findings = detect(List.of(
                signal("CONSOLE_ERROR", "TypeError: x is undefined")
        ));

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.MEDIUM, findings.get(0).severity());
    }

    @Test
    void pageErrorMapsToHighSeverity() {

        List<Finding> findings = detect(List.of(
                signal("PAGE_ERROR", "Uncaught ReferenceError")
        ));

        assertEquals(FindingSeverity.HIGH, findings.get(0).severity());
    }

    @Test
    void requestFailedMapsToMediumSeverity() {

        List<Finding> findings = detect(List.of(
                signal("REQUEST_FAILED", "net::ERR_CONNECTION_REFUSED — https://example.com/api")
        ));

        assertEquals(FindingSeverity.MEDIUM, findings.get(0).severity());
    }

    @Test
    void dialogMapsToLowSeverity() {

        List<Finding> findings = detect(List.of(
                signal("DIALOG", "confirm: Are you sure you want to delete this?")
        ));

        assertEquals(FindingSeverity.LOW, findings.get(0).severity());
    }

    @Test
    void crashMapsToCriticalSeverity() {

        List<Finding> findings = detect(List.of(
                signal("CRASH", "Page crashed")
        ));

        assertEquals(FindingSeverity.CRITICAL, findings.get(0).severity());
    }

    @Test
    void findingCarriesTheOriginalUrlAndSummary() {

        List<Finding> findings = detect(List.of(
                signal("CONSOLE_ERROR", "TypeError: x is undefined")
        ));

        Finding finding = findings.get(0);

        assertEquals("https://example.com", finding.url());
        assertTrue(finding.summary().contains("TypeError: x is undefined"));
    }

    @Test
    void summaryHasNoTrailingContextWhenNoActionHasRunYet() {

        List<Finding> findings = detect(List.of(
                signal("CONSOLE_ERROR", "TypeError: x is undefined")
        ));

        assertFalse(findings.get(0).summary().contains("(after"));
    }

    @Test
    void summaryNamesTheActionThatPrecededTheError() {

        MissionContext context = context();
        context.getExecutionState().addAction(action(ActionType.CLICK, "button[type='submit']"));

        Browser browser = new StubBrowser(List.of(
                signal("CONSOLE_ERROR", "TypeError: x is undefined")
        ));

        List<Finding> findings = new BrowserSignalAnomalyDetector(browser).detect(context);

        assertTrue(findings.get(0).summary().contains("(after CLICK button[type='submit'])"));
    }

    @Test
    void repeatingTheSameSignalIsReportedOnlyOnce() {

        AnomalySignal repeating = signal("CONSOLE_ERROR", "TypeError: x is undefined");

        Browser browser = new StubBrowser(List.of(repeating));
        BrowserSignalAnomalyDetector detector = new BrowserSignalAnomalyDetector(browser);

        MissionContext context = context();

        List<Finding> first = detector.detect(context);
        List<Finding> second = detector.detect(context);

        assertEquals(1, first.size());
        assertTrue(second.isEmpty(), "the same signal should not be reported twice in one mission");
    }

    @Test
    void differentSignalsAreBothReported() {

        Browser browser = new StubBrowser(List.of(
                signal("CONSOLE_ERROR", "TypeError: x is undefined"),
                signal("CONSOLE_ERROR", "RangeError: y is out of range")
        ));

        List<Finding> findings = new BrowserSignalAnomalyDetector(browser).detect(context());

        assertEquals(2, findings.size());
    }

    private List<Finding> detect(List<AnomalySignal> signals) {

        Browser browser = new StubBrowser(signals);

        return new BrowserSignalAnomalyDetector(browser).detect(context());
    }

    private MissionContext context() {
        return new MissionContext(
                new Mission(UUID.randomUUID(), "Test Mission", "Test", Map.of()));
    }

    private AnomalySignal signal(String type, String detail) {
        return new AnomalySignal(type, detail, "https://example.com", Instant.now());
    }

    private Action action(ActionType type, String target) {
        return new Action(
                UUID.randomUUID(),
                type,
                target,
                "",
                "test action",
                1.0,
                "test",
                Duration.ofSeconds(5),
                Instant.now(),
                "button"
        );
    }

    /**
     * Minimal stub — this test targets severity mapping and dedup/context
     * logic only, so every other Browser method is unused and left
     * unimplemented.
     */
    private static class StubBrowser implements Browser {

        private final List<AnomalySignal> signals;

        StubBrowser(List<AnomalySignal> signals) {
            this.signals = signals;
        }

        @Override
        public List<AnomalySignal> drainAnomalies() {
            return signals;
        }

        @Override
        public void launch() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void navigate(String url) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void refresh() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void goBack() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void click(String locator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void doubleClick(String locator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void raceClick(String locator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void type(String locator, String text) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void select(String locator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void scrollTo(String locator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getPageTitle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getCurrentUrl() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ElementInfo> getButtons() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ElementInfo> getInputs() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ElementInfo> getLinks() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ElementInfo> getSelects() {
            throw new UnsupportedOperationException();
        }
    }
}
