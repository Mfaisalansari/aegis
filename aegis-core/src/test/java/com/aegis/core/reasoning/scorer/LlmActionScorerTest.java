package com.aegis.core.reasoning.scorer;

import com.aegis.core.llm.LlmChatClient;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import com.aegis.model.reasoning.CandidateAction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmActionScorerTest {

    private final MissionContext context = new MissionContext(
            new Mission(UUID.randomUUID(), "Test", "Test", Map.of()));

    @Test
    void picksTheCandidateAtTheIndexTheModelReturns() {

        LlmActionScorer scorer = new LlmActionScorer(
                stubClient("{\"index\": 1, \"reasoning\": \"this one looks like the submit button\"}"));

        CandidateAction first = candidate(ActionType.TYPE, "#username", 0.85);
        CandidateAction second = candidate(ActionType.CLICK, "#submit", 0.15);

        CandidateAction chosen = scorer.choose(context, List.of(first, second));

        assertEquals("#submit", chosen.action().target());
    }

    @Test
    void tolerantOfMarkdownFencesAroundTheJson() {

        LlmActionScorer scorer = new LlmActionScorer(
                stubClient("```json\n{\"index\": 0, \"reasoning\": \"ok\"}\n```"));

        CandidateAction only = candidate(ActionType.CLICK, "#a", 0.5);

        CandidateAction chosen = scorer.choose(context, List.of(only, candidate(ActionType.CLICK, "#b", 0.5)));

        assertEquals("#a", chosen.action().target());
    }

    @Test
    void fallsBackToHighestConfidenceWhenTheResponseHasNoJson() {

        LlmActionScorer scorer = new LlmActionScorer(stubClient("I'm not sure, maybe try the button?"));

        CandidateAction lowConfidence = candidate(ActionType.TYPE, "#a", 0.10);
        CandidateAction highConfidence = candidate(ActionType.CLICK, "#b", 0.90);

        CandidateAction chosen = scorer.choose(context, List.of(lowConfidence, highConfidence));

        assertEquals("#b", chosen.action().target(), "must fall back to the highest-confidence candidate");
    }

    @Test
    void fallsBackWhenTheIndexIsOutOfRange() {

        LlmActionScorer scorer = new LlmActionScorer(
                stubClient("{\"index\": 99, \"reasoning\": \"bogus\"}"));

        CandidateAction lowConfidence = candidate(ActionType.TYPE, "#a", 0.10);
        CandidateAction highConfidence = candidate(ActionType.CLICK, "#b", 0.90);

        CandidateAction chosen = scorer.choose(context, List.of(lowConfidence, highConfidence));

        assertEquals("#b", chosen.action().target());
    }

    @Test
    void fallsBackWhenTheClientThrows() {

        LlmChatClient throwing = (system, user) -> {
            throw new RuntimeException("connection refused");
        };

        LlmActionScorer scorer = new LlmActionScorer(throwing);

        CandidateAction lowConfidence = candidate(ActionType.TYPE, "#a", 0.10);
        CandidateAction highConfidence = candidate(ActionType.CLICK, "#b", 0.90);

        CandidateAction chosen = scorer.choose(context, List.of(lowConfidence, highConfidence));

        assertEquals("#b", chosen.action().target());
    }

    @Test
    void usesTheSuppliedFallbackScorerInsteadOfTheDefault() {

        ActionScorer alwaysPicksFirst = (ctx, candidates) -> candidates.get(0);

        LlmActionScorer scorer = new LlmActionScorer(stubClient("garbage"), alwaysPicksFirst);

        CandidateAction first = candidate(ActionType.TYPE, "#a", 0.10);
        CandidateAction second = candidate(ActionType.CLICK, "#b", 0.90);

        CandidateAction chosen = scorer.choose(context, List.of(first, second));

        assertEquals("#a", chosen.action().target());
    }

    @Test
    void includesAppContextInThePromptWhenTheMissionParameterIsSet() {

        MissionContext withContext = new MissionContext(
                new Mission(UUID.randomUUID(), "Test", "Test", Map.of("appContext", "This is a demo bank app.")));

        StringBuilder capturedPrompt = new StringBuilder();
        LlmActionScorer scorer = new LlmActionScorer(capturingClient(capturedPrompt, "{\"index\": 0, \"reasoning\": \"ok\"}"));

        scorer.choose(withContext, List.of(candidate(ActionType.CLICK, "#a", 0.5)));

        assertTrue(capturedPrompt.toString().contains("Context about this application:\nThis is a demo bank app."));
    }

    @Test
    void omitsTheContextSectionWhenNoAppContextParameterIsSet() {

        StringBuilder capturedPrompt = new StringBuilder();
        LlmActionScorer scorer = new LlmActionScorer(capturingClient(capturedPrompt, "{\"index\": 0, \"reasoning\": \"ok\"}"));

        scorer.choose(context, List.of(candidate(ActionType.CLICK, "#a", 0.5)));

        assertFalse(capturedPrompt.toString().contains("Context about this application"));
    }

    private LlmChatClient stubClient(String response) {
        return (systemPrompt, userPrompt) -> response;
    }

    private LlmChatClient capturingClient(StringBuilder capturedUserPrompt, String response) {
        return (systemPrompt, userPrompt) -> {
            capturedUserPrompt.append(userPrompt);
            return response;
        };
    }

    private CandidateAction candidate(ActionType type, String target, double confidence) {

        Action action = new Action(UUID.randomUUID(), type, target, "", "reasoning", confidence,
                "expected", Duration.ofSeconds(1), Instant.now(), "");

        return new CandidateAction(action, confidence, "reasoning");
    }
}
