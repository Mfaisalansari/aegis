package com.aegis.core.knowledge;

import com.aegis.model.action.Action;
import com.aegis.model.observation.Observation;

import java.util.List;

/**
 * Everything a {@link KnowledgeProvider} needs to build its catalog: the
 * raw facts from this mission run ({@code observations}/{@code
 * actions}, in chronological order, straight from {@code
 * ExecutionState}), the organization-declared {@link KnowledgeConfig}
 * (or {@code null} if none was supplied), {@code partialBase} — the
 * catalogs already built earlier in the same {@link KnowledgeBaseBuilder}
 * run, so e.g. the node provider can read the already-built state
 * catalog without recomputing it — and {@code signals}, whatever {@code
 * SignalRecorder} captured live during the run ({@link SignalLog#empty()}
 * when inspection capture was disabled).
 */
public record KnowledgeBuildContext(
        String applicationName,
        List<Observation> observations,
        List<Action> actions,
        KnowledgeConfig config,
        KnowledgeBase partialBase,
        SignalLog signals
) {

    public KnowledgeBuildContext {
        observations = observations == null ? List.of() : List.copyOf(observations);
        actions = actions == null ? List.of() : List.copyOf(actions);
        signals = signals == null ? SignalLog.empty() : signals;
    }
}
