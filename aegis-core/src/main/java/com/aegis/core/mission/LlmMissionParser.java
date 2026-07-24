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
 * success condition, credentials) from free-text QA instructions —
 * things RuleBasedMissionParser can't do beyond finding a bare URL.
 *
 * Same validation discipline as LlmActionScorer: the model's own
 * "baseUrl" is never trusted blindly — it must parse as a real
 * http(s) URL with a host, or this falls back, exactly as it does on a
 * network failure, a malformed response, or no response at all. A
 * mission with an unusable starting URL is worse than a plain-text
 * fallback that got less out of the instruction but is at least valid.
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

    @Override
    public Mission parse(String naturalLanguageDescription) {

        try {

            Mission parsed = askModel(naturalLanguageDescription);

            if (parsed != null) {
                return parsed;
            }

            log.warn("LLM did not return a usable baseUrl; falling back to {}.",
                    fallback.getClass().getSimpleName());

        } catch (Exception e) {
            log.warn("LLM mission parsing failed ({}); falling back to {}.",
                    e.toString(), fallback.getClass().getSimpleName());
        }

        return fallback.parse(naturalLanguageDescription);
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
                + "and optional username/password if credentials are mentioned. "
                + "Respond with ONLY a JSON object of the exact shape "
                + "{\"baseUrl\": \"<url or null>\", \"goal\": \"<short description or null>\", "
                + "\"successUrlContains\": \"<substring or null>\", \"username\": \"<value or null>\", "
                + "\"password\": \"<value or null>\"} — no markdown, no code fences, no other text. "
                + "If you cannot find a URL in the instruction, set baseUrl to null.";
    }
}
