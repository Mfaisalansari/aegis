package com.aegis.core.reasoning.scorer;

import com.aegis.model.context.MissionContext;
import com.aegis.model.reasoning.CandidateAction;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Depth First Exploration (proxy): prefers following a link away from the
 * current page over exhausting local actions first, diving deep into the
 * app rather than wide.
 *
 * Same caveat as BreadthFirstActionScorer — approximated from element
 * tag, not a real navigation graph (that's Sprint 10, World Model).
 */
public class DepthFirstActionScorer implements ActionScorer {

    @Override
    public CandidateAction choose(MissionContext context, List<CandidateAction> candidates) {

        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("No candidate actions available.");
        }

        Optional<CandidateAction> link = candidates.stream()
                .filter(this::isLink)
                .max(Comparator.comparingDouble(CandidateAction::confidence));

        return link.orElseGet(() -> candidates.stream()
                .max(Comparator.comparingDouble(CandidateAction::confidence))
                .orElseThrow());
    }

    private boolean isLink(CandidateAction candidate) {
        return "a".equalsIgnoreCase(candidate.action().elementTag());
    }
}
