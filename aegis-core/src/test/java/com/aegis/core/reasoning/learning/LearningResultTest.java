package com.aegis.core.reasoning.learning;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningResultTest {

    @Test
    void emptyResultHasNoExperienceForAnything() {

        LearningResult result = LearningResult.empty();

        assertTrue(result.isEmpty());
        assertFalse(result.hasExperience(ActionType.CLICK, "#submit"));
        assertEquals(0.0, result.adjustmentFor(ActionType.CLICK, "#submit"));
    }

    @Test
    void hasExperienceIsTrueEvenWhenTheAdjustmentItselfIsZero() {

        // This is the whole point of hasExperience() existing separately
        // from adjustmentFor(): a recorded-but-neutral action (50-75%
        // success rate bucket) must be distinguishable from "never tried".
        LearningResult result = new LearningResult(Map.of("CLICK|#submit", 0.0));

        assertFalse(result.isEmpty());
        assertTrue(result.hasExperience(ActionType.CLICK, "#submit"));
        assertEquals(0.0, result.adjustmentFor(ActionType.CLICK, "#submit"));
    }

    @Test
    void hasExperienceIsScopedByExactTypeAndTarget() {

        LearningResult result = new LearningResult(Map.of("CLICK|#submit", 0.20));

        assertTrue(result.hasExperience(ActionType.CLICK, "#submit"));
        assertFalse(result.hasExperience(ActionType.TYPE, "#submit"));
        assertFalse(result.hasExperience(ActionType.CLICK, "#other"));
    }

    @Test
    void adjustmentForActionDelegatesToTypeAndTarget() {

        LearningResult result = new LearningResult(Map.of("CLICK|#submit", 0.20));

        Action action = new Action(
                UUID.randomUUID(), ActionType.CLICK, "#submit", "", "test",
                1.0, "test", Duration.ofSeconds(5), Instant.now(), ""
        );

        assertEquals(0.20, result.adjustmentFor(action), 0.0001);
    }
}
