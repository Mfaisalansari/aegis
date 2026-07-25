package com.aegis.core.mission;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MissionPlanTest {

    // Stage 5 hardening — same defensive-copy pattern as AuthenticatedSession (Stage 2).
    @Test
    void stepsIsDefensivelyCopiedAndImmutable() {

        List<String> callerList = new ArrayList<>(List.of("Navigate to https://example.com/"));
        MissionPlan plan = new MissionPlan(callerList);

        callerList.add("mutated after construction");

        assertEquals(1, plan.steps().size());
        assertThrows(UnsupportedOperationException.class, () -> plan.steps().add("x"));
    }

    @Test
    void nullStepsNormalizesToEmptyList() {
        assertEquals(List.of(), new MissionPlan(null).steps());
    }
}
