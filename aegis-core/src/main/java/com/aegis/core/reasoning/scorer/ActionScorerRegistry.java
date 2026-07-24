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

        String requested = context.getMission().parameter(PARAMETER_KEY);

        String key = (requested == null || requested.isBlank())
                ? DEFAULT_STRATEGY
                : requested;

        ActionScorer scorer = strategies.get(key);

        if (scorer == null) {
            throw new IllegalArgumentException("Unknown exploration strategy: " + key);
        }

        return scorer;
    }
}
