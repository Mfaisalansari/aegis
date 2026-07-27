package com.aegis.core.browser;

import com.aegis.core.knowledge.ConsoleSignal;
import com.aegis.core.knowledge.DomSignal;
import com.aegis.core.knowledge.ElementSnapshot;
import com.aegis.core.knowledge.NetworkSignal;
import com.aegis.core.knowledge.SignalLog;
import com.aegis.model.finding.FindingSeverity;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Accumulates {@link SignalLog} data live during one mission — console/
 * network events from {@link Browser#attachSignalRecorder}, DOM snapshots
 * from {@code SignalCapturingObserver}. Owned by whatever constructs the
 * mission's {@link Browser} and {@code Observer} (see {@code
 * EngineFactory}); never stored on {@code ExecutionState}/{@code
 * MissionContext} — those are part of/adjacent to frozen components, and
 * this doesn't need to be.
 */
public final class SignalRecorder {

    private final List<ConsoleSignal> console = new CopyOnWriteArrayList<>();
    private final List<NetworkSignal> network = new CopyOnWriteArrayList<>();
    private final List<DomSignal> dom = new CopyOnWriteArrayList<>();

    public void recordConsole(FindingSeverity level, String text, String location, String pageUrl) {
        console.add(new ConsoleSignal(level, text, location, pageUrl, Instant.now()));
    }

    public void recordNetwork(String requestUrl, int status, String method, String resourceType, String pageUrl) {
        network.add(new NetworkSignal(requestUrl, status, method, resourceType, pageUrl));
    }

    public void recordDomSnapshot(String stateSignature, List<ElementSnapshot> elements) {
        dom.add(new DomSignal(stateSignature, elements));
    }

    public SignalLog toSignalLog() {
        return new SignalLog(List.copyOf(console), List.copyOf(network), List.copyOf(dom));
    }
}
