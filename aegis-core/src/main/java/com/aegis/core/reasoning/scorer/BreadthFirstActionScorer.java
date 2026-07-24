package com.aegis.core.reasoning.scorer;

import com.aegis.model.context.MissionContext;
import com.aegis.model.reasoning.CandidateAction;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Breadth First Exploration (proxy): exhausts non-navigating actions on
 * the current page (typing, local clicks) before following a link away
 * from it.
 *
 * Not a real graph search — there is no navigation graph to search yet
 * (that needs a World Model, Sprint 10). This approximates "explore this
 * level before going deeper" using the one signal available at decision
 * time: whether an action's target looks like a link.
 */
public class BreadthFirstActionScorer implements ActionScorer {

    @Override
    public CandidateAction choose(MissionContext context, List<CandidateAction> candidates) {

        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("No candidate actions available.");
        }

        Optional<CandidateAction> local = candidates.stream()
                .filter(candidate -> !isLink(candidate))
                .max(Comparator.comparingDouble(CandidateAction::confidence));

        return local.orElseGet(() -> candidates.stream()
                .max(Comparator.comparingDouble(CandidateAction::confidence))
                .orElseThrow());
    }

    private boolean isLink(CandidateAction candidate) {
        return "a".equalsIgnoreCase(candidate.action().elementTag());
    }
}
