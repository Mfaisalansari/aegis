package com.aegis.core.reasoning.learning;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;

import java.util.Map;

/**
 * Keyed by ActionKey (type + target), not Action itself — Action's
 * equals() includes a random id/createdAt that's different every time a
 * candidate is regenerated, so a Map<Action, Double> would never
 * actually match a future lookup against the same logical action.
 *
 * Two lookup forms: adjustmentFor(Action) for callers that already have
 * one (e.g. after execution), and adjustmentFor(ActionType, String) for
 * callers scoring a candidate before an Action object exists yet (e.g.
 * HeuristicCandidateConfidenceEstimator, which only has an ElementInfo
 * and its locator at that point).
 */
public record LearningResult(Map<String, Double> scoreAdjustments) {

    public static LearningResult empty() {
        return new LearningResult(Map.of());
    }

    public double adjustmentFor(Action action) {
        return adjustmentFor(action.type(), action.target());
    }

    public double adjustmentFor(ActionType type, String target) {
        return scoreAdjustments.getOrDefault(type + "|" + target, 0.0);
    }

    /**
     * Whether we have ever recorded an Experience for this exact action —
     * distinct from adjustmentFor() being 0.0, which is ambiguous between
     * "never tried" and "tried and landed in the neutral, 50-75%
     * success-rate bucket". Exploration bonus (Phase 3) cares about the
     * former specifically: an action we have zero information about is a
     * stronger reason to try it than one we've already seen behave
     * unremarkably.
     */
    public boolean hasExperience(ActionType type, String target) {
        return scoreAdjustments.containsKey(type + "|" + target);
    }

    public boolean isEmpty() {
        return scoreAdjustments.isEmpty();
    }
}
