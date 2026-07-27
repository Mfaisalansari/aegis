package com.aegis.core.knowledge;

import java.util.List;

/**
 * Everything {@code SignalRecorder} captured live during a mission —
 * console/network signals from the browser's own event listeners, DOM
 * snapshots taken at each settled state. {@link #empty()} is what a
 * mission with inspection capture disabled produces, same "nothing
 * captured, nothing to analyze" shape {@link KnowledgeConfig#empty()}
 * already establishes for organization config.
 */
public record SignalLog(List<ConsoleSignal> console, List<NetworkSignal> network, List<DomSignal> dom) {

    public SignalLog {
        console = console == null ? List.of() : List.copyOf(console);
        network = network == null ? List.of() : List.copyOf(network);
        dom = dom == null ? List.of() : List.copyOf(dom);
    }

    public static SignalLog empty() {
        return new SignalLog(List.of(), List.of(), List.of());
    }
}
