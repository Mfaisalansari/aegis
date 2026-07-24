package com.aegis.core.reasoning.value;

import com.aegis.model.context.MissionContext;

import java.util.Map;

/**
 * Resolves which InputValueResolver a mission should use, based on its
 * "inputStrategy" parameter. Defaults to "realistic" so existing missions
 * that need valid credentials to reach a goal are unaffected; "edge-case"
 * swaps in EdgeCaseInputValueResolver for a dedicated input-validation
 * stress run instead. Mirrors ActionScorerRegistry's shape deliberately —
 * same pattern for the same kind of decision.
 */
public class InputValueResolverRegistry {

    public static final String PARAMETER_KEY = "inputStrategy";
    public static final String DEFAULT_STRATEGY = "realistic";

    private final Map<String, InputValueResolver> strategies;

    public InputValueResolverRegistry(Map<String, InputValueResolver> strategies) {
        this.strategies = strategies;
    }

    public InputValueResolver select(MissionContext context) {

        String requested = context.getMission().parameter(PARAMETER_KEY);

        String key = (requested == null || requested.isBlank())
                ? DEFAULT_STRATEGY
                : requested;

        InputValueResolver resolver = strategies.get(key);

        if (resolver == null) {
            throw new IllegalArgumentException("Unknown input strategy: " + key);
        }

        return resolver;
    }
}
