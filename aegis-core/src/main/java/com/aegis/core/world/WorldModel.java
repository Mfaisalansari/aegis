package com.aegis.core.world;

import com.aegis.core.reasoning.memory.StateSignature;
import com.aegis.model.action.Action;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.NavigationEdge;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
     * Where this exact action, taken from this exact state, led last
     * time it was tried — empty if it's never been tried from here.
     */
    public Optional<String> knownDestinationOf(Observation from, Action action) {

        return edgesFrom(from).stream()
                .filter(edge -> edge.actionType() == action.type()
                        && edge.actionTarget().equals(action.target()))
                .map(NavigationEdge::toState)
                .findFirst();
    }

    public int stateCount() {
        return edgesByState.size();
    }

    public int edgeCount() {
        return edgesByState.values().stream().mapToInt(Set::size).sum();
    }
}
