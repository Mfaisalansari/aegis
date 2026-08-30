package com.aegis.web.form;

import com.aegis.model.mission.Mission;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionFormRequestTest {

    @Test
    void fullParseResultMapsEveryProducedFieldOntoTheForm() {

        Mission mission = new Mission(UUID.randomUUID(), "Log into the demo shop", "raw instruction",
                Map.of("baseUrl", "https://example.com/", "username", "demo", "password", "demo123",
                        "successUrlContains", "dashboard", "maxIterations", "15",
                        "strategy", "coverage-aware", "inputStrategy", "edge-case"));

        MissionFormRequest form = MissionFormRequest.fromParsedMission(mission, "raw instruction");

        assertEquals("Log into the demo shop", form.name());
        assertEquals("raw instruction", form.description());
        assertEquals("https://example.com/", form.baseUrl());
        assertEquals("demo", form.username());
        assertEquals("demo123", form.password());
        assertEquals("dashboard", form.successUrlContains());
        assertEquals("15", form.maxIterations());
        assertEquals("coverage-aware", form.strategy());
        assertEquals("edge-case", form.inputStrategy());
        assertEquals("raw instruction", form.instruction());
    }

    @Test
    void partialParseResultLeavesUnproducedFieldsAtBlankFormDefaults() {

        MissionFormRequest blank = MissionFormRequest.defaults();

        Mission mission = new Mission(UUID.randomUUID(), "Some instruction", "Some instruction",
                Map.of("baseUrl", "https://example.com/"));

        MissionFormRequest form = MissionFormRequest.fromParsedMission(mission, "Some instruction");

        assertEquals("https://example.com/", form.baseUrl());
        assertEquals("", form.username());
        assertEquals("", form.password());
        assertEquals("", form.successUrlContains());
        assertEquals(blank.browserType(), form.browserType());
        assertEquals(blank.strategy(), form.strategy());
        assertEquals(blank.maxIterations(), form.maxIterations());
        assertEquals(blank.inputStrategy(), form.inputStrategy());
        assertEquals(blank.reportDirectory(), form.reportDirectory());
        assertEquals(blank.contrastThreshold(), form.contrastThreshold());
        assertTrue(!form.headless() && !form.interruptions() && !form.captureDom());
    }

    @Test
    void blankParsedMissionNameFallsBackToTheDefaultName() {

        Mission mission = new Mission(UUID.randomUUID(), "", "", Map.of());

        MissionFormRequest form = MissionFormRequest.fromParsedMission(mission, "");

        assertEquals(MissionFormRequest.defaults().name(), form.name());
        assertEquals(MissionFormRequest.defaults().description(), form.description());
        assertEquals("", form.instruction());
    }
}
