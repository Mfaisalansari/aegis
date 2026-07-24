package com.aegis.core.reasoning.scorer;

import com.aegis.core.llm.LlmChatClient;
import com.aegis.model.action.Action;
import com.aegis.model.context.MissionContext;
import com.aegis.model.finding.Finding;
import com.aegis.model.reasoning.CandidateAction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The first genuinely AI-driven decision-maker in the pipeline — every
 * other ActionScorer picks from the candidate list with a fixed,
 * hand-written rule (highest confidence, random, a keyword match, an
 * element-tag proxy). This one asks a real language model to judge which
 * candidate is actually worth trying next, given the mission's goal and
 * what's happened so far — the kind of judgment an exploratory tester
 * applies, not a static heuristic.
 *
 * Deliberately doesn't ask the model to invent an action or a locator:
 * candidates are already generated and validated by the existing
 * pipeline (GenericCandidateActionGenerator et al.), so the LLM's job is
 * narrowly "which of these", not "figure out how to interact with this
 * page" — a malformed or nonsensical model response can never result in
 * something invalid being executed, only in a fallback.
 *
 * Networks fail and models return malformed JSON. Either falls back to
 * the supplied fallback scorer (HighestConfidenceActionScorer by
 * default) rather than crashing the mission — consistent with how the
 * rest of the engine treats a single bad step as recoverable, not fatal.
 */
public class LlmActionScorer implements ActionScorer {

    private static final Logger log = LoggerFactory.getLogger(LlmActionScorer.class);

    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private final LlmChatClient client;
    private final ActionScorer fallback;
    private final ObjectMapper mapper = new ObjectMapper();

    public LlmActionScorer(LlmChatClient client) {
        this(client, new HighestConfidenceActionScorer());
    }

    public LlmActionScorer(LlmChatClient client, ActionScorer fallback) {
        this.client = client;
        this.fallback = fallback;
    }

    @Override
    public CandidateAction choose(MissionContext context, List<CandidateAction> candidates) {

        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("No candidate actions available.");
        }

        try {

            int index = askModel(context, candidates);

            if (index >= 0 && index < candidates.size()) {
                return candidates.get(index);
            }

            log.warn("LLM returned an out-of-range candidate index ({} of {}); falling back.",
                    index, candidates.size());

        } catch (Exception e) {
            log.warn("LLM scorer failed ({}); falling back to {}.",
                    e.toString(), fallback.getClass().getSimpleName());
        }

        return fallback.choose(context, candidates);
    }

    private int askModel(MissionContext context, List<CandidateAction> candidates) {

        String response = client.complete(systemPrompt(), userPrompt(context, candidates));

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

        if (!json.has("index")) {
            throw new IllegalStateException("LLM response JSON had no 'index' field: " + response);
        }

        return json.get("index").asInt(-1);
    }

    private String systemPrompt() {

        return "You are an exploratory QA tester deciding the single next action to take on a web page. "
                + "You will be given the mission's goal, a summary of what has happened so far, and a numbered "
                + "list of candidate actions already validated as executable. Pick the ONE candidate most likely "
                + "to make progress toward the goal or to uncover a defect. "
                + "Respond with ONLY a JSON object of the exact shape "
                + "{\"index\": <number>, \"reasoning\": \"<short reason>\"} — no markdown, no code fences, no other text.";
    }

    private String userPrompt(MissionContext context, List<CandidateAction> candidates) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("Mission: ").append(context.getMission().name()).append('\n');
        prompt.append("Goal: ").append(context.getMission().description()).append('\n');

        String successMarker = context.getMission().parameter("successUrlContains");

        if (successMarker != null && !successMarker.isBlank()) {
            prompt.append("Success means reaching a URL containing: ").append(successMarker).append('\n');
        }

        List<Action> history = context.getExecutionState().getActions();
        int historyStart = Math.max(0, history.size() - 5);

        prompt.append("Last ").append(history.size() - historyStart)
                .append(" of ").append(history.size()).append(" actions taken so far: ");

        for (int i = historyStart; i < history.size(); i++) {
            Action action = history.get(i);
            prompt.append(action.type()).append(' ').append(action.target()).append("; ");
        }

        prompt.append('\n');

        List<Finding> findings = context.getExecutionState().getFindings();
        prompt.append("Findings recorded so far: ").append(findings.size()).append('\n');

        prompt.append("\nCandidates:\n");

        for (int i = 0; i < candidates.size(); i++) {

            CandidateAction candidate = candidates.get(i);

            prompt.append(i).append(") ")
                    .append(candidate.action().type()).append(' ')
                    .append(candidate.action().target())
                    .append(" (heuristic confidence=").append(String.format("%.2f", candidate.confidence()))
                    .append(", heuristic reasoning: ").append(candidate.reasoning()).append(")\n");
        }

        return prompt.toString();
    }
}
