package com.aegis.core.mission;

import com.aegis.core.llm.LlmChatClient;
import com.aegis.core.llm.LlmClientException;
import com.aegis.model.mission.Mission;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LlmMissionParserTest {

    @Test
    void buildsAMissionFromTheModelsJsonResponse() {

        LlmMissionParser parser = new LlmMissionParser(stubClient("""
                {"baseUrl": "https://www.saucedemo.com/", "goal": "Log in and add an item to the cart",
                 "successUrlContains": "inventory.html", "username": "standard_user", "password": "secret_sauce"}
                """));

        Mission mission = parser.parse("Log into saucedemo with standard_user/secret_sauce and add an item to cart");

        assertEquals("https://www.saucedemo.com/", mission.parameter("baseUrl"));
        assertEquals("inventory.html", mission.parameter("successUrlContains"));
        assertEquals("standard_user", mission.parameter("username"));
        assertEquals("secret_sauce", mission.parameter("password"));
        assertEquals("Log in and add an item to the cart", mission.name());
    }

    @Test
    void omitsParametersTheModelReturnedAsNull() {

        LlmMissionParser parser = new LlmMissionParser(stubClient(
                "{\"baseUrl\": \"https://example.com\", \"goal\": null, "
                        + "\"successUrlContains\": null, \"username\": null, \"password\": null}"));

        Mission mission = parser.parse("Go to example.com");

        assertEquals("https://example.com", mission.parameter("baseUrl"));
        assertNull(mission.parameter("successUrlContains"));
        assertNull(mission.parameter("username"));
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
    }

    @Test
    void fallsBackWhenTheModelReturnsNoBaseUrlAtAll() {

        LlmMissionParser parser = new LlmMissionParser(
                stubClient("{\"baseUrl\": null, \"goal\": \"test\"}"));

        Mission mission = parser.parse("Log into https://www.saucedemo.com/ and add an item");

        assertEquals("https://www.saucedemo.com/", mission.parameter("baseUrl"));
    }

    @Test
    void fallsBackWhenTheClientThrows() {

        LlmMissionParser parser = new LlmMissionParser((system, user) -> {
            throw new LlmClientException("connection refused");
        });

        Mission mission = parser.parse("Log into https://www.saucedemo.com/ and add an item");

        assertEquals("https://www.saucedemo.com/", mission.parameter("baseUrl"));
    }

    @Test
    void fallsBackWhenTheResponseHasNoJson() {

        LlmMissionParser parser = new LlmMissionParser(stubClient("I'm not sure what you mean"));

        Mission mission = parser.parse("Log into https://www.saucedemo.com/ and add an item");

        assertEquals("https://www.saucedemo.com/", mission.parameter("baseUrl"));
    }

    private LlmChatClient stubClient(String response) {
        return (systemPrompt, userPrompt) -> response;
    }
}
