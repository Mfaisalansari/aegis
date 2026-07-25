package com.aegis.model.context;

import com.aegis.model.mission.Mission;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MissionContextTest {

    @Test
    void wrapsTheMissionAndOwnsAFreshExecutionState() {

        Mission mission = new Mission(UUID.randomUUID(), "Test Mission", "desc", Map.of("baseUrl", "https://example.com/"));

        MissionContext context = new MissionContext(mission);

        assertEquals(mission, context.getMission());
        assertNotNull(context.getExecutionState());
        assertEquals(0, context.getExecutionState().getIteration());
    }

    @Test
    void everyContextGetsItsOwnExecutionState() {

        Mission mission = new Mission(UUID.randomUUID(), "Test Mission", "desc", Map.of());

        MissionContext first = new MissionContext(mission);
        MissionContext second = new MissionContext(mission);

        first.getExecutionState().incrementIteration();

        assertEquals(1, first.getExecutionState().getIteration());
        assertEquals(0, second.getExecutionState().getIteration());
        assertSame(first.getExecutionState(), first.getExecutionState());
    }
}
