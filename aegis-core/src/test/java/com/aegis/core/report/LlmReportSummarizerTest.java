package com.aegis.core.report;

import com.aegis.core.knowledge.KnowledgeBase;
import com.aegis.core.knowledge.KnowledgeBaseBuilder;
import com.aegis.core.knowledge.KnowledgeConfig;
import com.aegis.core.llm.LlmChatClient;
import com.aegis.core.llm.LlmClientException;
import com.aegis.model.mission.MissionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmReportSummarizerTest {

    private final KnowledgeBase knowledgeBase =
            KnowledgeBaseBuilder.standard().build("Test", List.of(), List.of(), KnowledgeConfig.empty());

    @Test
    void returnsTheModelsResponseWhenTheCallSucceeds() {

        LlmReportSummarizer summarizer = new LlmReportSummarizer(
                stubClient("AEGIS tested your site and everything worked great."));

        String result = summarize(summarizer);

        assertEquals("AEGIS tested your site and everything worked great.", result);
    }

    @Test
    void fallsBackWhenTheClientThrows() {

        LlmReportSummarizer summarizer = new LlmReportSummarizer((system, user) -> {
            throw new LlmClientException("connection refused");
        });

        String result = summarize(summarizer);

        assertEquals("fallback text", result);
    }

    @Test
    void fallsBackWhenTheModelReturnsAnEmptyResponse() {

        LlmReportSummarizer summarizer = new LlmReportSummarizer(stubClient("   "));

        String result = summarize(summarizer);

        assertEquals("fallback text", result);
    }

    @Test
    void includesAppContextInThePromptWhenConstructedWithIt() {

        StringBuilder capturedPrompt = new StringBuilder();
        LlmReportSummarizer summarizer = new LlmReportSummarizer(
                capturingClient(capturedPrompt, "summary"), "This is a demo bank app.");

        summarize(summarizer);

        assertTrue(capturedPrompt.toString().contains("Context about this application:\nThis is a demo bank app."));
    }

    @Test
    void theOriginalOneArgConstructorOmitsTheContextSectionEntirely() {

        StringBuilder capturedPrompt = new StringBuilder();
        LlmReportSummarizer summarizer = new LlmReportSummarizer(capturingClient(capturedPrompt, "summary"));

        summarize(summarizer);

        assertFalse(capturedPrompt.toString().contains("Context about this application"));
    }

    @Test
    void theTwoArgConstructorWithABlankContextAlsoOmitsTheSection() {

        StringBuilder capturedPrompt = new StringBuilder();
        LlmReportSummarizer summarizer = new LlmReportSummarizer(capturingClient(capturedPrompt, "summary"), "   ");

        summarize(summarizer);

        assertFalse(capturedPrompt.toString().contains("Context about this application"));
    }

    private String summarize(LlmReportSummarizer summarizer) {
        return summarizer.summarize(
                "Mission", "Goal", MissionStatus.SUCCESS,
                new ExplorationCoverage(10, 5, 50.0), List.of(), Map.of(),
                "recommendation", knowledgeBase, "fallback text");
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
}
