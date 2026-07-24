package com.aegis.core.reasoning.memory;

import com.aegis.model.observation.Observation;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tracks which pages and page states have been observed during a mission,
 * so exploration can recognise when it's back somewhere it's already been.
 * Scoped to a single mission run; not persisted across runs — see
 * ARCHITECTURE.md for why (no stable mission identity across runs yet).
 *
 * See StateSignature for what counts as a "state".
 */
public class VisitedStateMemory {

    private final Set<String> visitedPages = new LinkedHashSet<>();
    private final Set<String> visitedStates = new LinkedHashSet<>();

    public void remember(Observation observation) {
        visitedPages.add(observation.url());
        visitedStates.add(StateSignature.of(observation));
    }

    public boolean hasVisitedPage(String url) {
        return visitedPages.contains(url);
    }

    public boolean hasVisitedState(Observation observation) {
        return visitedStates.contains(StateSignature.of(observation));
    }

    public boolean hasVisitedState(String stateSignature) {
        return visitedStates.contains(stateSignature);
    }

    public Set<String> getVisitedPages() {
        return visitedPages;
    }

    public int visitedStateCount() {
        return visitedStates.size();
    }
}
