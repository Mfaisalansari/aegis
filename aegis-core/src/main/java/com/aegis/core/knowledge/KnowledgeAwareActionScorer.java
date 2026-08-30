package com.aegis.core.knowledge;

import com.aegis.core.plugin.NamedActionScorer;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AEGIS 2.0 Phase 5 — Knowledge-driven Autonomous Exploration. A Stage 2
 * "MissionStrategy" plugin (see {@link NamedActionScorer}), discovered
 * via {@code ServiceLoader} exactly like a third party's own would be —
 * registered here (via {@code META-INF/services}) as a real, always-
 * available 11th {@code explorationStrategy} value, {@code
 * "knowledge-aware"}, without touching {@code EngineFactory}'s own
 * hand-built 10-strategy map at all.
 *
 * Biases toward a candidate whose destination (see {@link
 * #knownDestinationsThisRun}) is NOT already known-covered from a prior
 * persisted run — i.e. prefers exploring what neither this run nor any
 * previous one (AEGIS's own, or an ingested Selenium/Playwright/
 * annotation-driven session) has reached before.
 *
 * <b>Honest caveat</b>: {@link #UNCOVERED_BONUS}'s exact value is a
 * defensible starting point, not tuned against real accumulated data —
 * same discipline as {@code ExperienceScoreCatalogProvider}'s severity
 * weights.
 *
 * <b>Why this lives in {@code com.aegis.core.knowledge}</b>, not
 * alongside the other 10 scorers in {@code com.aegis.core.reasoning.scorer}:
 * it needs package-private {@link NodeNaming#slug} to compute a node key
 * from a raw URL the same way {@link NodeCatalogProvider} does, without
 * widening that deliberately narrow utility's visibility. Implementing
 * {@code ActionScorer} from a different package than its other
 * implementations is otherwise unremarkable — Java doesn't restrict
 * interface implementation by package.
 *
 * <b>No live {@code WorldModel} dependency</b>, unlike {@code
 * CoverageAwareActionScorer} — {@code ServiceLoader}-discovered plugins
 * are constructed with no arguments, so there's nothing to inject one
 * through. Instead this recomputes a this-run-only destination map
 * directly from {@link MissionContext#getExecutionState()}'s raw {@code
 * Observation}/{@code Action} history, the same {@code (actionType,
 * actionTarget) -> destination} correlation the frozen {@code
 * MissionReportData.from(...)} already relies on for its own {@code
 * com.aegis.model.reasoning.NavigationEdge} list.
 */
public final class KnowledgeAwareActionScorer implements NamedActionScorer {

    private static final double UNCOVERED_BONUS = 0.15;

    private final CoverageStore coverageStore;

    public KnowledgeAwareActionScorer() {
        this(CoverageStore.defaultLocation());
    }

    KnowledgeAwareActionScorer(CoverageStore coverageStore) {
        this.coverageStore = coverageStore;
    }

    @Override
    public String strategyName() {
        return "knowledge-aware";
    }

    @Override
    public CandidateAction choose(MissionContext context, List<CandidateAction> candidates) {

        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("No candidate actions available.");
        }

        Set<String> previouslyCovered = coverageStore.load(context.getMission().name()).visitedNodeKeys();
        Map<ActionKey, String> knownDestinations = knownDestinationsThisRun(context);

        return candidates.stream()
                .max(Comparator.comparingDouble(candidate -> scoreOf(candidate, knownDestinations, previouslyCovered)))
                .orElseThrow();
    }

    private double scoreOf(CandidateAction candidate, Map<ActionKey, String> knownDestinations, Set<String> previouslyCovered) {

        String destinationNodeKey = knownDestinations.get(new ActionKey(candidate.action().type(), candidate.action().target()));

        if (destinationNodeKey == null || previouslyCovered.contains(destinationNodeKey)) {
            return candidate.confidence();
        }

        return candidate.confidence() + UNCOVERED_BONUS;
    }

    private record ActionKey(ActionType type, String target) {
    }

    private Map<ActionKey, String> knownDestinationsThisRun(MissionContext context) {

        List<Observation> observations = context.getExecutionState().getObservations();
        List<Action> actions = context.getExecutionState().getActions();

        Map<ActionKey, String> destinations = new LinkedHashMap<>();

        for (int i = 0; i < actions.size() && i + 1 < observations.size(); i++) {
            ActionKey key = new ActionKey(actions.get(i).type(), actions.get(i).target());
            destinations.putIfAbsent(key, NodeNaming.slug(observations.get(i + 1).url()));
        }

        return destinations;
    }
}
