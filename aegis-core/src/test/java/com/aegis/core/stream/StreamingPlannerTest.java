package com.aegis.core.stream;

import com.aegis.core.planner.Planner;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingPlannerTest {

    @Test
    void emitsAReasonEventCarryingTheChosenActionsOwnReasoning() {

        List<MissionStreamEvent> recorded = new ArrayList<>();
        Action action = new Action(
                UUID.randomUUID(), ActionType.CLICK, "[id='login-button']", null,
                "Submit-like button and visible inputs are filled", 0.8, "navigates to dashboard",
                Duration.ofSeconds(5), Instant.now(), "button");

        Planner fake = context -> action;
        StreamingPlanner planner = new StreamingPlanner(fake, recorded::add);

        Action returned = planner.plan(new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", Map.of())));

        assertSame(action, returned, "the real planner's chosen action must still be returned unchanged");
        assertEquals(1, recorded.size());
        MissionStreamEvent event = recorded.get(0);
        assertEquals(MissionStreamEvent.Kind.REASON, event.kind());
        assertEquals("Selected CLICK [id='login-button']", event.headline());
        assertTrue(event.detail().contains("Submit-like button and visible inputs are filled"));
        assertTrue(event.detail().contains("80%"));
    }

    @Test
    void relabelsAFlatExplorationBonusHonestlyInsteadOfCallingItLearned() {

        List<MissionStreamEvent> recorded = new ArrayList<>();
        Action action = new Action(
                UUID.randomUUID(), ActionType.TYPE, "[id='email']", "demo",
                "Generic input, no credential match (learning-adjusted +0.05, never tried yet this mission)",
                0.5, "fills the field", Duration.ofSeconds(5), Instant.now(), "input");

        Planner fake = context -> action;
        StreamingPlanner planner = new StreamingPlanner(fake, recorded::add);

        planner.plan(new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", Map.of())));

        String detail = recorded.get(0).detail();
        assertFalse(detail.contains("never tried yet this mission"), detail);
        assertTrue(detail.contains("exploring new ground"), detail);
    }

    @Test
    void labelsAGenuinePriorExperienceAdjustmentAsLearned() {

        List<MissionStreamEvent> recorded = new ArrayList<>();
        Action action = new Action(
                UUID.randomUUID(), ActionType.CLICK, "[id='nav-logout']", null,
                "Persistent nav link (learning-adjusted +0.20)",
                0.9, "logs out", Duration.ofSeconds(5), Instant.now(), "a");

        Planner fake = context -> action;
        StreamingPlanner planner = new StreamingPlanner(fake, recorded::add);

        planner.plan(new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", Map.of())));

        assertTrue(recorded.get(0).detail().contains("learned: tends to work, +0.20"));
    }
}
