package com.aegis.core.mission;

import com.aegis.core.llm.LlmChatClient;
import com.aegis.model.mission.Mission;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 8 "Natural language missions", the AI-backed path: asks a real
 * model to extract structured mission fields (starting URL, goal,
 * success condition, credentials, iteration count, exploration/input
 * strategy) from free-text QA instructions — things RuleBasedMissionParser
 * can't do beyond finding a bare URL.
 *
 * Same validation discipline as LlmActionScorer: the model's own
 * "baseUrl" is never trusted blindly — it must parse as a real
 * http(s) URL with a host, or this falls back, exactly as it does on a
 * network failure, a malformed response, or no response at all. A
 * mission with an unusable starting URL is worse than a plain-text
 * fallback that got less out of the instruction but is at least valid.
 *
 * The model's "strategy"/"inputStrategy" answers are stored as opaque
 * parameter strings, never validated here — an invalid/hallucinated value
 * gets exactly the same downstream rejection a human's typo would (see
 * {@code MissionRequestMapper} in aegis-web), so this class only needs to
 * know how to extract, not what's actually valid.
 *
 * See {@link #parseWithDiagnostics} for a richer result than {@link
 * #parse} that also says whether the AI path was actually used, for a
 * caller that wants to show a degraded/fallback result differently
 * instead of silently presenting it the same as a full success.
 */
public class LlmMissionParser implements MissionParser {

    private static final Logger log = LoggerFactory.getLogger(LlmMissionParser.class);

    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private final LlmChatClient client;
    private final MissionParser fallback;
    private final ObjectMapper mapper = new ObjectMapper();

    public LlmMissionParser(LlmChatClient client) {
        this(client, new RuleBasedMissionParser());
    }

    public LlmMissionParser(LlmChatClient client, MissionParser fallback) {
        this.client = client;
        this.fallback = fallback;
    }

    /** A closed, small set of reasons — never a raw exception message, so callers can match on it rather than parse free text. */
    private static final String REASON_UNREACHABLE = "Couldn't reach the AI model or understand its response";
    private static final String REASON_NO_USABLE_URL = "The AI didn't return a usable starting URL";

    /**
     * Everything {@link #parse} needs, plus whether the full AI path
     * actually ran — {@code aiUsed} is {@code false} whenever {@link
     * #fallback} (the bare-URL-only {@code RuleBasedMissionParser}) is
     * what actually produced {@code mission}, so a caller that shows this
     * to a user (see {@code NaturalLanguageHandler}) can say so instead of
     * silently presenting a mostly-empty result as if nothing degraded.
     */
    public record NaturalLanguageParseResult(Mission mission, boolean aiUsed, String fallbackReason) {
    }

    @Override
    public Mission parse(String naturalLanguageDescription) {
        return parseWithDiagnostics(naturalLanguageDescription).mission();
    }

    public NaturalLanguageParseResult parseWithDiagnostics(String naturalLanguageDescription) {

        String fallbackReason;

        try {

            Mission parsed = askModel(naturalLanguageDescription);

            if (parsed != null) {
                return new NaturalLanguageParseResult(parsed, true, null);
            }

            fallbackReason = REASON_NO_USABLE_URL;
            log.warn("LLM did not return a usable baseUrl; falling back to {}.",
                    fallback.getClass().getSimpleName());

        } catch (Exception e) {
            fallbackReason = REASON_UNREACHABLE;
            log.warn("LLM mission parsing failed ({}); falling back to {}.",
                    e.toString(), fallback.getClass().getSimpleName());
        }

        Mission fallbackMission = fallback.parse(naturalLanguageDescription);
        return new NaturalLanguageParseResult(fallbackMission, false, fallbackReason);
    }

    private Mission askModel(String description) {

        String response = client.complete(systemPrompt(), description);

        Matcher matcher = JSON_OBJECT.matcher(response);

        if (!matcher.find()) {
            throw new IllegalStateException("LLM response contained no JSON object: " + response);
        }

        JsonNode json;

        try {
            json = mapper.readTree(matcher.group());
        } catch (Exception e) {
            throw new IllegalStateException("LLM response was not valid JSON: " + response, e);
        }

        String baseUrl = textOrNull(json, "baseUrl");

        if (baseUrl == null || !looksLikeUrl(baseUrl)) {
            return null;
        }

        Map<String, String> parameters = new HashMap<>();
        parameters.put("baseUrl", baseUrl);

        putIfPresent(parameters, "successUrlContains", textOrNull(json, "successUrlContains"));
        putIfPresent(parameters, "username", textOrNull(json, "username"));
        putIfPresent(parameters, "password", textOrNull(json, "password"));

        Integer maxIterations = intOrNull(json, "maxIterations");
        putIfPresent(parameters, "maxIterations", maxIterations == null ? null : String.valueOf(maxIterations));
        putIfPresent(parameters, "strategy", textOrNull(json, "strategy"));
        putIfPresent(parameters, "inputStrategy", textOrNull(json, "inputStrategy"));

        String goal = textOrNull(json, "goal");

        return new Mission(
                UUID.randomUUID(),
                goal != null ? goal : shortName(description),
                description,
                Map.copyOf(parameters)
        );
    }

    private void putIfPresent(Map<String, String> parameters, String key, String value) {

        if (value != null && !value.isBlank()) {
            parameters.put(key, value);
        }
    }

    private String textOrNull(JsonNode json, String field) {

        JsonNode node = json.get(field);

        return (node == null || node.isNull()) ? null : node.asText();
    }

    /** Mirrors {@code LlmActionScorer}'s numeric-field convention (a sentinel-checked {@code .asInt(-1)}), so a quoted "20" from the model still parses. */
    private Integer intOrNull(JsonNode json, String field) {

        JsonNode node = json.get(field);

        if (node == null || node.isNull()) {
            return null;
        }

        int value = node.asInt(-1);
        return value == -1 ? null : value;
    }

    private boolean looksLikeUrl(String value) {

        try {
            URI uri = URI.create(value);
            return uri.getScheme() != null && uri.getScheme().startsWith("http") && uri.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private String shortName(String text) {

        String trimmed = text.strip();

        return trimmed.length() > 60 ? trimmed.substring(0, 57) + "..." : trimmed;
    }

    private String systemPrompt() {

        return "You are translating a plain-English QA testing instruction into a structured mission definition. "
                + "Extract: the starting URL to navigate to, a short goal/name for the mission, an optional "
                + "success condition (a distinctive substring the URL should contain once the goal is reached), "
                + "optional username/password if credentials are mentioned, an optional iteration/step count if "
                + "one is mentioned (e.g. \"explore for 20 steps\"), an optional exploration strategy if the "
                + "instruction implies one (one of: greedy, random, risk-based, breadth-first, depth-first, "
                + "form-first, navigation-first, coverage-aware, adaptive, llm — only if it's a clear match, "
                + "otherwise null), and an optional input strategy: \"edge-case\" if the instruction asks for "
                + "invalid/malformed/adversarial input testing, \"realistic\" if it asks for normal valid data, "
                + "otherwise null. "
                + "Respond with ONLY a JSON object of the exact shape "
                + "{\"baseUrl\": \"<url or null>\", \"goal\": \"<short description or null>\", "
                + "\"successUrlContains\": \"<substring or null>\", \"username\": \"<value or null>\", "
                + "\"password\": \"<value or null>\", \"maxIterations\": <integer or null>, "
                + "\"strategy\": \"<one of the strategies above or null>\", "
                + "\"inputStrategy\": \"<realistic, edge-case, or null>\"} — no markdown, no code fences, no other text. "
                + "If you cannot find a URL in the instruction, set baseUrl to null.";
    }
}
