package com.aegis.core.mission;

import com.aegis.model.mission.Mission;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedMissionPlannerTest {

    private final RuleBasedMissionPlanner planner = new RuleBasedMissionPlanner();

    @Test
    void includesANavigateStepWhenBaseUrlIsSet() {

        Mission mission = mission(Map.of("baseUrl", "https://example.com"));

        MissionPlan plan = planner.plan(mission);

        assertTrue(plan.steps().stream().anyMatch(step -> step.contains("https://example.com")));
    }

    @Test
    void includesALoginStepOnlyWhenUsernameIsSet() {

        Mission withCredentials = mission(Map.of("baseUrl", "https://example.com", "username", "bob"));
        Mission withoutCredentials = mission(Map.of("baseUrl", "https://example.com"));

        assertTrue(planner.plan(withCredentials).steps().stream().anyMatch(step -> step.contains("login")));
        assertFalse(planner.plan(withoutCredentials).steps().stream().anyMatch(step -> step.contains("login")));
    }

    @Test
    void includesTheSuccessConditionWhenSet() {

        Mission mission = mission(Map.of("baseUrl", "https://example.com", "successUrlContains", "done"));

        MissionPlan plan = planner.plan(mission);

        assertTrue(plan.steps().stream().anyMatch(step -> step.contains("\"done\"")));
    }

    @Test
    void alwaysProducesAtLeastOneStepEvenWithNoParameters() {

        Mission mission = mission(Map.of());

        MissionPlan plan = planner.plan(mission);

        assertFalse(plan.steps().isEmpty());
    }

    private Mission mission(Map<String, String> parameters) {
        return new Mission(UUID.randomUUID(), "Test", "Test", parameters);
    }
}
