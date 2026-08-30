package com.aegis.web.handler;

import com.aegis.core.llm.LlmClientException;
import com.aegis.core.mission.LlmMissionParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalLanguageHandlerTest {

    @Test
    void showsTheOrdinaryNoticeWhenAiParsingSucceeds() {

        NaturalLanguageHandler handler = new NaturalLanguageHandler(new LlmMissionParser(
                (system, user) -> "{\"baseUrl\": \"https://example.com\", \"goal\": \"test\"}"));

        String html = handler.render(Map.of("instruction", "Go to https://example.com"));

        assertTrue(html.contains("Parsed from your description"));
        assertFalse(html.contains("wasn"));
    }

    @Test
    void showsAVisiblyDifferentNoticeWhenAiParsingFallsBack() {

        NaturalLanguageHandler handler = new NaturalLanguageHandler(new LlmMissionParser(
                (system, user) -> {
                    throw new LlmClientException("connection refused");
                }));

        String html = handler.render(Map.of("instruction", "Go to https://example.com"));

        // "wasn't" is HTML-escaped ("wasn&#39;t") by MissionFormView, so match around the apostrophe.
        assertTrue(html.contains("AI parsing wasn"));
        assertTrue(html.contains("using the plain-text fallback"));
        assertFalse(html.contains("Parsed from your description"));
        // The bare-URL fallback should still have found the URL despite the AI path failing.
        assertTrue(html.contains("https://example.com"));
    }

    @Test
    void asksForAnInstructionWhenNoneWasSubmitted() {

        NaturalLanguageHandler handler = new NaturalLanguageHandler(new LlmMissionParser(
                (system, user) -> "{\"baseUrl\": \"https://example.com\"}"));

        String html = handler.render(Map.of());

        assertTrue(html.contains("Please describe the mission before parsing."));
    }
}
