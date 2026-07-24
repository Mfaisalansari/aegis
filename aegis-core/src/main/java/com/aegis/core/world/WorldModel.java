package com.aegis.core.world;

import com.aegis.core.reasoning.memory.StateSignature;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.NavigationEdge;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A navigation graph built incrementally as the mission runs: which
 * action, taken from which state, led to which other state. This is what
 * makes "does this action lead somewhere new" a known fact instead of a
 * guess — but only for actions that have already been tried at least
 * once; there's no prediction for untried actions.
 *
 * In-run only, not persisted across runs — same reasoning as Memory
 * (see ARCHITECTURE.md's Memory Scope note).
 *
 * Deliberately narrow: tracks state transitions only. It does not model
 * dialogs or forms as distinct entities — the Observer has no concept of
 * either yet, so there's nothing to build that on top of.
 */
public class WorldModel {

    private final Map<String, Set<NavigationEdge>> edgesByState = new ConcurrentHashMap<>();

    public void recordTransition(Observation from, Action action, Observation to) {

        String fromState = StateSignature.of(from);
        String toState = StateSignature.of(to);

        NavigationEdge edge = new NavigationEdge(
                fromState,
                action.type(),
                action.target(),
                toState
        );

        edgesByState.computeIfAbsent(fromState, key -> new LinkedHashSet<>()).add(edge);
    }

    public Set<NavigationEdge> edgesFrom(Observation observation) {
        return edgesByState.getOrDefault(StateSignature.of(observation), Set.of());
    }

    /**
     * Every destination this exact action (by type + target) has ever
     * led to, from any state it's been tried from this mission — not
     * just the current one. The same locator (a persistent nav link, an
     * "About" button in a shared header) tends to behave the same way
     * regardless of which page it's clicked from, so history from one
     * state is meaningful evidence for a candidate reached via a
     * different, not-yet-tried-from state. Empty if this action has
     * never been tried from anywhere yet.
     */
    public Set<String> knownDestinationsOf(ActionType type, String target) {

        return edgesByState.values().stream()
                .flatMap(Set::stream)
                .filter(edge -> edge.actionType() == type && edge.actionTarget().equals(target))
                .map(NavigationEdge::toState)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public int stateCount() {
        return edgesByState.size();
    }

    public int edgeCount() {
        return edgesByState.values().stream().mapToInt(Set::size).sum();
    }
}
