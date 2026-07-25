package com.aegis.model.mission;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MissionTest {

    // Stage 5 hardening: Mission is public API a caller can construct
    // directly (not just via MissionBuilder, which already copied) —
    // mutating the caller's own map after construction must not change
    // an already-handed-off Mission. Matters most for
    // ParallelMissionRunner, where two concurrently running missions
    // must never observe each other's state.
    @Test
    void parametersAreDefensivelyCopiedAtConstruction() {

        Map<String, String> callerMap = new HashMap<>(Map.of("baseUrl", "https://example.com/"));
        Mission mission = new Mission(UUID.randomUUID(), "Test", "Test", callerMap);

        callerMap.put("baseUrl", "https://mutated.example.com/");
        callerMap.put("extra", "should not appear");

        assertEquals("https://example.com/", mission.parameter("baseUrl"));
        assertEquals(null, mission.parameter("extra"));
    }

    @Test
    void parametersMapIsImmutable() {

        Mission mission = new Mission(UUID.randomUUID(), "Test", "Test", Map.of("baseUrl", "https://example.com/"));

        assertThrows(UnsupportedOperationException.class, () -> mission.parameters().put("x", "y"));
    }

    @Test
    void nullParametersNormalizesToEmptyMap() {

        Mission mission = new Mission(UUID.randomUUID(), "Test", "Test", null);

        assertEquals(null, mission.parameter("anything"));
        assertEquals(0, mission.parameters().size());
    }
}
