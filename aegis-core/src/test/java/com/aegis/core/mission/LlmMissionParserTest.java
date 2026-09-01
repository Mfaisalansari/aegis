package com.aegis.core.mission;

import com.aegis.core.llm.LlmChatClient;
import com.aegis.core.llm.LlmClientException;
import com.aegis.core.mission.LlmMissionParser.NaturalLanguageParseResult;
import com.aegis.model.mission.Mission;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmMissionParserTest {

    @Test
    void buildsAMissionFromTheModelsJsonResponse() {

        LlmMissionParser parser = new LlmMissionParser(stubClient("""
                {"baseUrl": "https://www.saucedemo.com/", "goal": "Log in and add an item to the cart",
                 "successUrlContains": "inventory.html", "username": "standard_user", "password": "secret_sauce",
                 "maxIterations": 15, "strategy": "coverage-aware", "inputStrategy": "edge-case"}
                """));

        Mission mission = parser.parse("Log into saucedemo with standard_user/secret_sauce and add an item to cart");

        assertEquals("https://www.saucedemo.com/", mission.parameter("baseUrl"));
        assertEquals("inventory.html", mission.parameter("successUrlContains"));
        assertEquals("standard_user", mission.parameter("username"));
        assertEquals("secret_sauce", mission.parameter("password"));
        assertEquals("15", mission.parameter("maxIterations"));
        assertEquals("coverage-aware", mission.parameter("strategy"));
        assertEquals("edge-case", mission.parameter("inputStrategy"));
        assertEquals("Log in and add an item to the cart", mission.name());
    }

    @Test
    void extractsRequiredAndUndoActionsWhenTheInstructionImpliesThem() {

        LlmMissionParser parser = new LlmMissionParser(stubClient("""
                {"baseUrl": "https://www.saucedemo.com/", "goal": "Add an item to the cart and check out",
                 "successUrlContains": "checkout-complete", "username": "standard_user", "password": "secret_sauce",
                 "requiredActionsContain": "add-to-cart", "undoActionsContain": "remove"}
                """));

        Mission mission = parser.parse(
                "Log into saucedemo.com as standard_user/secret_sauce, add an item to the cart "
                        + "without removing it, and complete checkout");

        assertEquals("add-to-cart", mission.parameter("requiredActionsContain"));
        assertEquals("remove", mission.parameter("undoActionsContain"));
    }

    @Test
    void omitsParametersTheModelReturnedAsNull() {

        LlmMissionParser parser = new LlmMissionParser(stubClient(
                "{\"baseUrl\": \"https://example.com\", \"goal\": null, "
                        + "\"successUrlContains\": null, \"username\": null, \"password\": null, "
                        + "\"maxIterations\": null, \"strategy\": null, \"inputStrategy\": null, "
                        + "\"requiredActionsContain\": null, \"undoActionsContain\": null}"));

        Mission mission = parser.parse("Go to example.com");

        assertEquals("https://example.com", mission.parameter("baseUrl"));
        assertNull(mission.parameter("successUrlContains"));
        assertNull(mission.parameter("username"));
        assertNull(mission.parameter("maxIterations"));
        assertNull(mission.parameter("strategy"));
        assertNull(mission.parameter("inputStrategy"));
        assertNull(mission.parameter("requiredActionsContain"));
        assertNull(mission.parameter("undoActionsContain"));
    }

    @Test
    void passesThroughAnInvalidStrategyNameUnfiltered() {

        // Extraction, not validation, is this class's job — an invalid/hallucinated
        // value gets the same downstream rejection a human's typo would (MissionRequestMapper).
        LlmMissionParser parser = new LlmMissionParser(stubClient(
                "{\"baseUrl\": \"https://example.com\", \"strategy\": \"make-it-up\"}"));

        Mission mission = parser.parse("Go to example.com");

        assertEquals("make-it-up", mission.parameter("strategy"));
    }

    @Test
    void parseWithDiagnosticsReportsAiUsedTrueOnTheHappyPath() {

        LlmMissionParser parser = new LlmMissionParser(stubClient(
                "{\"baseUrl\": \"https://example.com\", \"goal\": \"test\"}"));

        NaturalLanguageParseResult result = parser.parseWithDiagnostics("Go to example.com");

        assertTrue(result.aiUsed());
        assertNull(result.fallbackReason());
        assertEquals("https://example.com", result.mission().parameter("baseUrl"));
    }

    @Test
    void tolerantOfMarkdownFencesAroundTheJson() {

        LlmMissionParser parser = new LlmMissionParser(stubClient(
                "```json\n{\"baseUrl\": \"https://example.com\", \"goal\": \"test\"}\n```"));

        Mission mission = parser.parse("Go to example.com");

        assertEquals("https://example.com", mission.parameter("baseUrl"));
    }

    @Test
    void fallsBackWhenTheModelReturnsAnUnusableBaseUrl() {

        LlmMissionParser parser = new LlmMissionParser(
                stubClient("{\"baseUrl\": \"not a real url\", \"goal\": \"test\"}"));

        Mission mission = parser.parse("Log into https://www.saucedemo.com/ and add an item");

        // Falls all the way through to RuleBasedMissionParser, which
        // extracts the URL directly from the raw instruction instead.
        assertEquals("https://www.saucedemo.com/", mission.parameter("baseUrl"));

        NaturalLanguageParseResult result = parser.parseWithDiagnostics("Log into https://www.saucedemo.com/ and add an item");
        assertFalse(result.aiUsed());
        assertNotNull(result.fallbackReason());
    }

    @Test
    void fallsBackWhenTheModelReturnsNoBaseUrlAtAll() {

        LlmMissionParser parser = new LlmMissionParser(
                stubClient("{\"baseUrl\": null, \"goal\": \"test\"}"));

        Mission mission = parser.parse("Log into https://www.saucedemo.com/ and add an item");

        assertEquals("https://www.saucedemo.com/", mission.parameter("baseUrl"));

        NaturalLanguageParseResult result = parser.parseWithDiagnostics("Log into https://www.saucedemo.com/ and add an item");
        assertFalse(result.aiUsed());
        assertNotNull(result.fallbackReason());
    }

    @Test
    void fallsBackWhenTheClientThrows() {

        LlmMissionParser parser = new LlmMissionParser((system, user) -> {
            throw new LlmClientException("connection refused");
        });

        Mission mission = parser.parse("Log into https://www.saucedemo.com/ and add an item");

        assertEquals("https://www.saucedemo.com/", mission.parameter("baseUrl"));

        NaturalLanguageParseResult result = parser.parseWithDiagnostics("Log into https://www.saucedemo.com/ and add an item");
        assertFalse(result.aiUsed());
        assertNotNull(result.fallbackReason());
    }

    @Test
    void fallsBackWhenTheResponseHasNoJson() {

        LlmMissionParser parser = new LlmMissionParser(stubClient("I'm not sure what you mean"));

        Mission mission = parser.parse("Log into https://www.saucedemo.com/ and add an item");

        assertEquals("https://www.saucedemo.com/", mission.parameter("baseUrl"));

        NaturalLanguageParseResult result = parser.parseWithDiagnostics("Log into https://www.saucedemo.com/ and add an item");
        assertFalse(result.aiUsed());
        assertNotNull(result.fallbackReason());
    }

    private LlmChatClient stubClient(String response) {
        return (systemPrompt, userPrompt) -> response;
    }
}
