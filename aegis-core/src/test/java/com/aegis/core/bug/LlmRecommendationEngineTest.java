package com.aegis.core.bug;

import com.aegis.core.llm.LlmChatClient;
import com.aegis.core.llm.LlmClientException;
import com.aegis.model.finding.FindingSeverity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmRecommendationEngineTest {

    private final BugCluster cluster = new BugCluster(
            "CRASH|", "CRASH: tab crashed", FindingSeverity.CRITICAL,
            1, Set.of("https://example.com"), Instant.now(), Instant.now());

    @Test
    void returnsTheModelsResponseWhenTheCallSucceeds() {

        LlmRecommendationEngine engine = new LlmRecommendationEngine(
                stubClient("Fix the tab crash first — it blocks the entire flow."));

        String result = engine.recommend(List.of(cluster), "fallback text");

        assertEquals("Fix the tab crash first — it blocks the entire flow.", result);
    }

    @Test
    void fallsBackWhenTheClientThrows() {

        LlmRecommendationEngine engine = new LlmRecommendationEngine((system, user) -> {
            throw new LlmClientException("connection refused");
        });

        String result = engine.recommend(List.of(cluster), "fallback text");

        assertEquals("fallback text", result);
    }

    @Test
    void fallsBackWhenTheModelReturnsAnEmptyResponse() {

        LlmRecommendationEngine engine = new LlmRecommendationEngine(stubClient("   "));

        String result = engine.recommend(List.of(cluster), "fallback text");

        assertEquals("fallback text", result);
    }

    @Test
    void skipsTheModelCallEntirelyWhenThereAreNoClusters() {

        LlmRecommendationEngine engine = new LlmRecommendationEngine((system, user) -> {
            throw new AssertionError("should not call the model with no clusters");
        });

        String result = engine.recommend(List.of(), "fallback text");

        assertEquals("fallback text", result);
    }

    private LlmChatClient stubClient(String response) {
        return (systemPrompt, userPrompt) -> response;
    }
}
