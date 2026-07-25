package com.aegis.api;

import com.aegis.core.reasoning.scorer.ActionScorerRegistry;
import com.aegis.core.reasoning.value.InputValueResolverRegistry;
import com.aegis.model.mission.Mission;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fluent, typed way to build a {@link Mission} instead of hand-writing a
 * raw {@code Map<String,String>} of parameter keys. Every method here maps
 * to exactly one existing mission parameter (see USAGE.md §4) — this adds
 * no new capability, just a discoverable surface over what already exists.
 * {@link #parameter(String, String)} is the escape hatch for anything not
 * covered by a named method yet.
 */
public final class MissionBuilder {

    private final String name;
    private final String description;
    private final Map<String, String> parameters = new LinkedHashMap<>();

    private MissionBuilder(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public static MissionBuilder create(String name, String description) {
        return new MissionBuilder(name, description);
    }

    /** Pre-populates a builder from a loaded {@link AegisConfig} — the bridge between the YAML path and this one. */
    public static MissionBuilder from(AegisConfig config) {

        MissionConfig mission = config.mission();
        ApplicationConfig application = config.application();

        MissionBuilder builder = create(mission.name(), mission.description())
                .baseUrl(application.baseUrl())
                .credentials(application.username(), application.password())
                .successWhenUrlContains(application.successUrlContains())
                .strategy(mission.strategy())
                .inputStrategy(mission.inputStrategy())
                .interruptions(mission.interruptions())
                .doubleClicks(mission.doubleClicks())
                .raceConditions(mission.raceConditions());

        if (mission.maxIterations() != null) {
            builder.maxIterations(mission.maxIterations());
        }

        return builder;
    }

    public MissionBuilder baseUrl(String baseUrl) {
        return parameter("baseUrl", baseUrl);
    }

    public MissionBuilder credentials(String username, String password) {
        parameter("username", username);
        return parameter("password", password);
    }

    public MissionBuilder successWhenUrlContains(String substring) {
        return parameter("successUrlContains", substring);
    }

    /** One of the keys in {@code ActionScorerRegistry} — "greedy" (default), "coverage-aware", "adaptive", "llm", etc. */
    public MissionBuilder strategy(String explorationStrategy) {
        return parameter(ActionScorerRegistry.PARAMETER_KEY, explorationStrategy);
    }

    /** "realistic" (default) or "edge-case" — see {@code InputValueResolverRegistry}. */
    public MissionBuilder inputStrategy(String inputStrategy) {
        return parameter(InputValueResolverRegistry.PARAMETER_KEY, inputStrategy);
    }

    public MissionBuilder maxIterations(int maxIterations) {
        return parameter("maxIterations", String.valueOf(maxIterations));
    }

    public MissionBuilder interruptions(boolean enabled) {
        return enabledFlag("interruptions", enabled);
    }

    public MissionBuilder doubleClicks(boolean enabled) {
        return enabledFlag("doubleClicks", enabled);
    }

    public MissionBuilder raceConditions(boolean enabled) {
        return enabledFlag("raceConditions", enabled);
    }

    /** Escape hatch: set any parameter key directly, including ones not wrapped by a named method above. */
    public MissionBuilder parameter(String key, String value) {

        if (value != null) {
            parameters.put(key, value);
        }

        return this;
    }

    public Mission build() {
        return new Mission(UUID.randomUUID(), name, description, Map.copyOf(parameters));
    }

    private MissionBuilder enabledFlag(String key, boolean enabled) {
        return enabled ? parameter(key, "enabled") : this;
    }
}
