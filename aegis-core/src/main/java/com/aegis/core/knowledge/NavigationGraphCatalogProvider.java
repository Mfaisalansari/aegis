package com.aegis.core.knowledge;

import com.aegis.core.reasoning.memory.StateSignature;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.observation.Observation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Built-in {@link KnowledgeProvider} for {@link NavigationGraphCatalog}.
 * Derives the chronological node-key visit sequence the same way {@link
 * UxAnalysisCatalogProvider#detectBacktracking} does (raw observations,
 * not {@link Journey#actualNodeKeySequence()}, which is already
 * deduplicated and would hide every transition this catalog exists to
 * report), then reads three independent facts: which distinct
 * transitions happened ({@link NavigationGraphEdge}), which cycles were
 * actually walked ({@link NavigationLoop}), and which visited nodes the
 * actor explicitly retreated from ({@link #detectDeadEnds}).
 */
public final class NavigationGraphCatalogProvider implements KnowledgeProvider {

    @Override
    public NavigationGraphCatalog provide(KnowledgeBuildContext context) {

        StateCatalog stateCatalog = context.partialBase().require(StateCatalog.class);
        NodeCatalog nodeCatalog = context.partialBase().require(NodeCatalog.class);

        Map<String, String> keyByStateSignature = new HashMap<>();
        for (State state : stateCatalog.states()) {
            nodeCatalog.byStateId(state.id()).ifPresent(node -> keyByStateSignature.put(state.stateSignature(), node.key()));
        }

        List<String> visitedKeysInOrder = new ArrayList<>();
        for (Observation observation : context.observations()) {
            String key = keyByStateSignature.get(StateSignature.of(observation));
            if (key != null) {
                visitedKeysInOrder.add(key);
            }
        }

        List<String> steps = collapseConsecutiveDuplicates(visitedKeysInOrder);

        List<NavigationGraphEdge> edges = buildEdges(steps);
        List<NavigationLoop> loops = detectLoops(steps);
        List<String> deadEnds = detectDeadEnds(context.observations(), context.actions(), keyByStateSignature);

        return new NavigationGraphCatalog(edges, loops, deadEnds);
    }

    /** Two observations of the same node back to back (a re-render, not a navigation) collapse into one step. */
    private List<String> collapseConsecutiveDuplicates(List<String> sequence) {

        List<String> collapsed = new ArrayList<>();

        for (String key : sequence) {
            if (collapsed.isEmpty() || !collapsed.get(collapsed.size() - 1).equals(key)) {
                collapsed.add(key);
            }
        }

        return collapsed;
    }

    private record Transition(String from, String to) {
    }

    private List<NavigationGraphEdge> buildEdges(List<String> steps) {

        Map<Transition, Integer> counts = new LinkedHashMap<>();

        for (int i = 1; i < steps.size(); i++) {
            counts.merge(new Transition(steps.get(i - 1), steps.get(i)), 1, Integer::sum);
        }

        List<NavigationGraphEdge> edges = new ArrayList<>();
        for (Map.Entry<Transition, Integer> entry : counts.entrySet()) {
            edges.add(new NavigationGraphEdge(entry.getKey().from(), entry.getKey().to(), entry.getValue()));
        }

        return edges;
    }

    /** The first walked cycle per distinct starting node — same first-occurrence convention used throughout this package. */
    private List<NavigationLoop> detectLoops(List<String> steps) {

        List<NavigationLoop> loops = new ArrayList<>();
        Set<String> alreadyReportedStart = new LinkedHashSet<>();

        for (int i = 0; i < steps.size(); i++) {

            String key = steps.get(i);

            if (!alreadyReportedStart.add(key)) {
                continue;
            }

            int nextOccurrence = steps.subList(i + 1, steps.size()).indexOf(key);
            if (nextOccurrence < 0) {
                continue;
            }

            int end = i + 1 + nextOccurrence;
            loops.add(new NavigationLoop(steps.subList(i, end + 1)));
        }

        return loops;
    }

    /**
     * A node the actor explicitly retreated from via a {@code BACK}
     * action, rather than continuing forward — the one honest,
     * directly-observable "this didn't lead anywhere" signal available
     * from a flat action/observation trace. {@code actions.get(i)} is
     * always the action taken while {@code observations.get(i)} was
     * current, producing {@code observations.get(i + 1)} — the same
     * correlation the frozen {@code MissionReportData.from(...)} already
     * relies on to build its own {@code com.aegis.model.reasoning.NavigationEdge}
     * list, reused here rather than invented fresh.
     *
     * Deliberately NOT "any node with no observed outgoing transition" —
     * in a single chronological trace, every non-final visit to a node is
     * followed by *some* next observation by construction, so that
     * definition is always vacuously empty. A real absence-of-forward-
     * progress signal needs the action that was actually taken, not just
     * the resulting state sequence.
     */
    private List<String> detectDeadEnds(List<Observation> observations, List<Action> actions, Map<String, String> keyByStateSignature) {

        Set<String> deadEnds = new LinkedHashSet<>();

        for (int i = 0; i < actions.size() && i + 1 < observations.size(); i++) {

            if (actions.get(i).type() != ActionType.BACK) {
                continue;
            }

            String fromKey = keyByStateSignature.get(StateSignature.of(observations.get(i)));
            if (fromKey != null) {
                deadEnds.add(fromKey);
            }
        }

        return List.copyOf(deadEnds);
    }
}
