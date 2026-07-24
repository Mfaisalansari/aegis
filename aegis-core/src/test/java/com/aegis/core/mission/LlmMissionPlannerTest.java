package com.aegis.core.mission;

import com.aegis.core.llm.LlmChatClient;
import com.aegis.core.llm.LlmClientException;
import com.aegis.model.mission.Mission;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmMissionPlannerTest {

    private final Mission mission = new Mission(
            UUID.randomUUID(), "Test mission", "Log in and check inventory",
            Map.of("baseUrl", "https://www.saucedemo.com/"));

    @Test
    void buildsAPlanFromTheModelsJsonArrayResponse() {

        LlmMissionPlanner planner = new LlmMissionPlanner(stubClient(
                "[\"Navigate to the login page\", \"Log in with valid credentials\", \"Verify the inventory page loads\"]"));

        MissionPlan plan = planner.plan(mission);

        assertEquals(3, plan.steps().size());
        assertEquals("Navigate to the login page", plan.steps().get(0));
    }

    @Test
    void tolerantOfMarkdownFencesAroundTheJson() {

        LlmMissionPlanner planner = new LlmMissionPlanner(stubClient(
                "```json\n[\"Step one\", \"Step two\"]\n```"));

        MissionPlan plan = planner.plan(mission);

        assertEquals(2, plan.steps().size());
    }

    @Test
    void skipsBlankOrNonTextEntries() {

        LlmMissionPlanner planner = new LlmMissionPlanner(stubClient(
                "[\"Real step\", \"\", \"   \", 42]"));

        MissionPlan plan = planner.plan(mission);

        assertEquals(1, plan.steps().size());
        assertEquals("Real step", plan.steps().get(0));
    }

    @Test
    void fallsBackWhenTheClientThrows() {

        LlmMissionPlanner planner = new LlmMissionPlanner((system, user) -> {
            throw new LlmClientException("connection refused");
        });

        MissionPlan plan = planner.plan(mission);

        // Falls through to RuleBasedMissionPlanner's output.
        assertTrue(plan.steps().stream().anyMatch(step -> step.contains("https://www.saucedemo.com/")));
    }

    @Test
    void fallsBackWhenTheResponseHasNoJsonArray() {

        LlmMissionPlanner planner = new LlmMissionPlanner(stubClient("I'm not sure what steps to suggest"));

        MissionPlan plan = planner.plan(mission);

        assertTrue(plan.steps().stream().anyMatch(step -> step.contains("https://www.saucedemo.com/")));
    }

    @Test
    void fallsBackWhenTheArrayIsEmptyAfterFiltering() {

        LlmMissionPlanner planner = new LlmMissionPlanner(stubClient("[\"\", \"   \"]"));

        MissionPlan plan = planner.plan(mission);

        assertTrue(plan.steps().stream().anyMatch(step -> step.contains("https://www.saucedemo.com/")));
    }

    private LlmChatClient stubClient(String response) {
        return (systemPrompt, userPrompt) -> response;
    }
}
