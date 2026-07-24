package com.aegis.core.mission;

import com.aegis.model.mission.Mission;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedMissionParserTest {

    private final RuleBasedMissionParser parser = new RuleBasedMissionParser();

    @Test
    void extractsABareUrlFromTheInstruction() {

        Mission mission = parser.parse("Log into https://www.saucedemo.com/ and add an item to the cart");

        assertEquals("https://www.saucedemo.com/", mission.parameter("baseUrl"));
    }

    @Test
    void stripsTrailingPunctuationFromTheExtractedUrl() {

        Mission mission = parser.parse("Go check out https://example.com/login, then log in.");

        assertEquals("https://example.com/login", mission.parameter("baseUrl"));
    }

    @Test
    void leavesBaseUrlUnsetWhenNoUrlIsPresent() {

        Mission mission = parser.parse("Log into the site and add an item to the cart");

        assertNull(mission.parameter("baseUrl"));
    }

    @Test
    void usesTheFullTextAsTheDescription() {

        String text = "Log into https://www.saucedemo.com/ and add an item to the cart";

        Mission mission = parser.parse(text);

        assertEquals(text, mission.description());
    }

    @Test
    void truncatesAVeryLongInstructionForTheMissionName() {

        String longText = "Log into https://www.saucedemo.com/ and ".repeat(5);

        Mission mission = parser.parse(longText);

        assertTrue(mission.name().length() <= 60);
        assertTrue(mission.name().endsWith("..."));
    }
}
