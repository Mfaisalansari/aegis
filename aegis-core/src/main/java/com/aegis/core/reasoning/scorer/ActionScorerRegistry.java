package com.aegis.core.reasoning.scorer;

import com.aegis.model.context.MissionContext;

import java.util.Map;

/**
 * Resolves which exploration strategy (ActionScorer) a mission should use,
 * based on its "explorationStrategy" parameter. Defaults to "greedy".
 */
public class ActionScorerRegistry {

    public static final String PARAMETER_KEY = "explorationStrategy";
    public static final String DEFAULT_STRATEGY = "greedy";

    private final Map<String, ActionScorer> strategies;

    public ActionScorerRegistry(Map<String, ActionScorer> strategies) {
        this.strategies = strategies;
    }

    public ActionScorer resolve(MissionContext context) {

        String key = resolveKey(context);
        ActionScorer scorer = strategies.get(key);

        if (scorer == null) {
            throw new IllegalArgumentException("Unknown exploration strategy: " + key);
        }

        return scorer;
    }

    /** The strategy key {@link #resolve(MissionContext)} would use — exposed so callers that already hold the resolved {@link ActionScorer} can still record which key produced it (e.g. for {@code ReasoningStep.strategy()}). */
    public String resolveKey(MissionContext context) {

        String requested = context.getMission().parameter(PARAMETER_KEY);

        return (requested == null || requested.isBlank())
                ? DEFAULT_STRATEGY
                : requested;
    }
}
