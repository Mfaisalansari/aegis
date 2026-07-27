package com.aegis.core.browser;

import com.aegis.core.knowledge.BoundingBox;
import com.aegis.core.knowledge.ElementSnapshot;
import com.aegis.core.knowledge.SignalLog;
import com.aegis.model.finding.FindingSeverity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignalRecorderTest {

    @Test
    void recordedConsoleSignalsAppearInTheFinishedSignalLog() {

        SignalRecorder recorder = new SignalRecorder();

        recorder.recordConsole(FindingSeverity.HIGH, "Uncaught TypeError", "app.js:42", "https://app/login");

        SignalLog log = recorder.toSignalLog();

        assertEquals(1, log.console().size());
        assertEquals(FindingSeverity.HIGH, log.console().get(0).level());
        assertEquals("Uncaught TypeError", log.console().get(0).text());
        assertEquals("https://app/login", log.console().get(0).pageUrl());
    }

    @Test
    void recordedNetworkSignalsAppearInTheFinishedSignalLog() {

        SignalRecorder recorder = new SignalRecorder();

        recorder.recordNetwork("https://app/api/orders", 404, "GET", "xhr", "https://app/orders");

        SignalLog log = recorder.toSignalLog();

        assertEquals(1, log.network().size());
        assertEquals(404, log.network().get(0).status());
        assertEquals("https://app/api/orders", log.network().get(0).requestUrl());
    }

    @Test
    void recordedDomSnapshotsAppearInTheFinishedSignalLog() {

        SignalRecorder recorder = new SignalRecorder();

        ElementSnapshot snapshot = new ElementSnapshot(
                "login-button", "Log In", List.of(), new BoundingBox(0, 0, 100, 40), "rgb(0,0,0)", "rgb(255,255,255)", 14, false);

        recorder.recordDomSnapshot("state-sig-1", List.of(snapshot));

        SignalLog log = recorder.toSignalLog();

        assertEquals(1, log.dom().size());
        assertEquals("state-sig-1", log.dom().get(0).stateSignature());
        assertEquals(1, log.dom().get(0).elements().size());
    }

    @Test
    void aFreshRecorderProducesAnEmptySignalLog() {

        SignalLog log = new SignalRecorder().toSignalLog();

        assertTrue(log.console().isEmpty());
        assertTrue(log.network().isEmpty());
        assertTrue(log.dom().isEmpty());
    }

    @Test
    void toSignalLogIsASnapshotNotALiveView() {

        SignalRecorder recorder = new SignalRecorder();
        recorder.recordConsole(FindingSeverity.LOW, "warning", "", "https://app");

        SignalLog first = recorder.toSignalLog();
        recorder.recordConsole(FindingSeverity.LOW, "another warning", "", "https://app");
        SignalLog second = recorder.toSignalLog();

        assertEquals(1, first.console().size());
        assertEquals(2, second.console().size());
    }
}
