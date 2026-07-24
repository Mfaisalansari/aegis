package com.aegis.core.controller;

import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultMissionControllerTest {

    private final DefaultMissionController controller = new DefaultMissionController();

    @Test
    void defaultsToTenIterationsWhenNotConfigured() {

        MissionContext context = context(Map.of());

        for (int i = 0; i < 10; i++) {
            assertTrue(controller.shouldContinue(context), "should still continue at iteration " + i);
            context.getExecutionState().incrementIteration();
        }

        assertFalse(controller.shouldContinue(context), "should stop once the default cap is reached");
    }

    @Test
    void respectsAConfiguredMaxIterations() {

        MissionContext context = context(Map.of("maxIterations", "3"));

        for (int i = 0; i < 3; i++) {
            assertTrue(controller.shouldContinue(context));
            context.getExecutionState().incrementIteration();
        }

        assertFalse(controller.shouldContinue(context));
    }

    @Test
    void fallsBackToDefaultWhenConfiguredValueIsNotANumber() {

        MissionContext context = context(Map.of("maxIterations", "not-a-number"));

        for (int i = 0; i < 10; i++) {
            assertTrue(controller.shouldContinue(context));
            context.getExecutionState().incrementIteration();
        }

        assertFalse(controller.shouldContinue(context));
    }

    private MissionContext context(Map<String, String> parameters) {
        return new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", parameters));
    }
}
