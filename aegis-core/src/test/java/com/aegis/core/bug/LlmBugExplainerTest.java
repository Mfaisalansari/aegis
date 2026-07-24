package com.aegis.core.bug;

import com.aegis.core.llm.LlmChatClient;
import com.aegis.core.llm.LlmClientException;
import com.aegis.model.finding.FindingSeverity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmBugExplainerTest {

    private final BugCluster cluster = new BugCluster(
            "REQUEST_FAILED|", "REQUEST_FAILED: /api/checkout failed", FindingSeverity.HIGH,
            3, Set.of("https://example.com/checkout"), Instant.now(), Instant.now());

    @Test
    void returnsTheModelsResponseWhenTheCallSucceeds() {

        LlmBugExplainer explainer = new LlmBugExplainer(
                stubClient("The checkout endpoint is failing repeatedly, likely blocking purchases."));

        String result = explainer.explain(cluster, "fallback text");

        assertEquals("The checkout endpoint is failing repeatedly, likely blocking purchases.", result);
    }

    @Test
    void fallsBackWhenTheClientThrows() {

        LlmBugExplainer explainer = new LlmBugExplainer((system, user) -> {
            throw new LlmClientException("connection refused");
        });

        String result = explainer.explain(cluster, "fallback text");

        assertEquals("fallback text", result);
    }

    @Test
    void fallsBackWhenTheModelReturnsAnEmptyResponse() {

        LlmBugExplainer explainer = new LlmBugExplainer(stubClient("   "));

        String result = explainer.explain(cluster, "fallback text");

        assertEquals("fallback text", result);
    }

    private LlmChatClient stubClient(String response) {
        return (systemPrompt, userPrompt) -> response;
    }
}
