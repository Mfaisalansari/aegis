package com.aegis.core.mission;

import com.aegis.core.llm.LlmChatClient;
import com.aegis.model.mission.Mission;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 8 "AI mission planning", the AI-backed path: asks a real model
 * to sketch the expected high-level approach for a mission before it
 * runs, using the mission's own description and parameters rather than
 * RuleBasedMissionPlanner's mechanical "if baseUrl is set, add a
 * navigate step" logic.
 *
 * Purely advisory, same as the rule-based version — this plan is never
 * read by GoalReasoner, ActionScorer, or any live decision, only
 * rendered in the report. That's what makes this safe to build without
 * the validation machinery LlmActionScorer/LlmMissionParser need: a
 * plan step is just display text, never something that gets executed.
 */
public class LlmMissionPlanner implements MissionPlanner {

    private static final Logger log = LoggerFactory.getLogger(LlmMissionPlanner.class);

    private static final Pattern JSON_ARRAY = Pattern.compile("\\[.*]", Pattern.DOTALL);

    private final LlmChatClient client;
    private final MissionPlanner fallback;
    private final ObjectMapper mapper = new ObjectMapper();

    public LlmMissionPlanner(LlmChatClient client) {
        this(client, new RuleBasedMissionPlanner());
    }

    public LlmMissionPlanner(LlmChatClient client, MissionPlanner fallback) {
        this.client = client;
        this.fallback = fallback;
    }

    @Override
    public MissionPlan plan(Mission mission) {

        try {

            MissionPlan plan = askModel(mission);

            if (plan != null) {
                return plan;
            }

            log.warn("LLM returned no usable plan steps; falling back to {}.", fallback.getClass().getSimpleName());

        } catch (Exception e) {
            log.warn("LLM mission planning failed ({}); falling back to {}.",
                    e.toString(), fallback.getClass().getSimpleName());
        }

        return fallback.plan(mission);
    }

    private MissionPlan askModel(Mission mission) {

        String response = client.complete(systemPrompt(), userPrompt(mission));

        Matcher matcher = JSON_ARRAY.matcher(response);

        if (!matcher.find()) {
            throw new IllegalStateException("LLM response contained no JSON array: " + response);
        }

        JsonNode json;

        try {
            json = mapper.readTree(matcher.group());
        } catch (Exception e) {
            throw new IllegalStateException("LLM response was not valid JSON: " + response, e);
        }

        if (!json.isArray()) {
            throw new IllegalStateException("LLM response JSON was not an array: " + response);
        }

        List<String> steps = new ArrayList<>();

        for (JsonNode node : json) {

            String step = node.isTextual() ? node.asText().strip() : "";

            if (!step.isEmpty()) {
                steps.add(step);
            }
        }

        return steps.isEmpty() ? null : new MissionPlan(List.copyOf(steps));
    }

    private String systemPrompt() {

        return "You are a QA lead planning an exploratory testing mission before it starts. Given the mission's "
                + "name, goal description, and known parameters (starting URL, success condition, whether "
                + "credentials are provided), write 3-6 short, ordered, high-level steps describing the approach "
                + "you'd expect an exploratory tester to take. This is advisory only — it previews the expected "
                + "approach and will not control what the tester actually does. "
                + "Respond with ONLY a JSON array of strings, e.g. [\"step one\", \"step two\"] — no markdown, "
                + "no other text.";
    }

    private String userPrompt(Mission mission) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("Mission: ").append(mission.name()).append('\n');
        prompt.append("Goal: ").append(mission.description()).append('\n');

        String baseUrl = mission.parameter("baseUrl");

        if (baseUrl != null && !baseUrl.isBlank()) {
            prompt.append("Starting URL: ").append(baseUrl).append('\n');
        }

        String successMarker = mission.parameter("successUrlContains");

        if (successMarker != null && !successMarker.isBlank()) {
            prompt.append("Success means reaching a URL containing: ").append(successMarker).append('\n');
        }

        prompt.append("Credentials provided: ")
                .append(mission.parameter("username") != null ? "yes" : "no").append('\n');

        String appContext = mission.parameter("appContext");

        if (appContext != null && !appContext.isBlank()) {
            prompt.append("\nContext about this application:\n").append(appContext).append('\n');
        }

        return prompt.toString();
    }
}
