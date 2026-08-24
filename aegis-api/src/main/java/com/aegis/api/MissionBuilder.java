package com.aegis.api;

import com.aegis.core.reasoning.scorer.ActionScorerRegistry;
import com.aegis.core.reasoning.value.InputValueResolverRegistry;
import com.aegis.model.mission.Mission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static final Logger log = LoggerFactory.getLogger(MissionBuilder.class);

    /**
     * Bounds the "appContext" mission parameter (see {@link
     * #appContext(String)}) — unlike a one-off instruction, this text is
     * re-sent on every LLM call an {@code llm}-strategy mission makes
     * (potentially every iteration via {@code LlmActionScorer}), so an
     * unbounded document would silently balloon token cost/latency.
     */
    static final int MAX_APP_CONTEXT_LENGTH = 4000;

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
                .appContext(application.contextFile())
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

    /**
     * Reads {@code path} (relative to the JVM's working directory — same
     * convention {@link AegisConfigLoader#load(Path)} itself already uses
     * for the top-level config file) and stores its raw text as the
     * {@code "appContext"} mission parameter. Free-form Markdown, no
     * schema — alongside {@code knowledge.yml}, not a replacement for it:
     * only {@code llm}-strategy components (see {@code LlmActionScorer},
     * {@code LlmMissionPlanner}, {@code LlmReportSummarizer}) ever read
     * this parameter; every deterministic knowledge/coverage/inspection
     * provider is completely unaffected.
     *
     * A missing/unreadable file degrades to no context rather than
     * failing the whole mission build — this is enrichment for an
     * already-opt-in strategy, not a required input. Read once, here,
     * rather than by each of the three consumers separately, so the
     * {@link #MAX_APP_CONTEXT_LENGTH} guardrail exists in exactly one
     * place instead of being duplicated three times.
     */
    public MissionBuilder appContext(String path) {

        if (path == null || path.isBlank()) {
            return this;
        }

        String text;

        try {
            text = Files.readString(Path.of(path));
        } catch (IOException e) {
            log.warn("Could not read context file '{}' ({}); continuing without it.", path, e.getMessage());
            return this;
        }

        if (text.length() > MAX_APP_CONTEXT_LENGTH) {
            log.warn("Context file '{}' is {} characters; truncating to {} to bound LLM prompt cost.",
                    path, text.length(), MAX_APP_CONTEXT_LENGTH);
            text = text.substring(0, MAX_APP_CONTEXT_LENGTH);
        }

        return parameter("appContext", text);
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
