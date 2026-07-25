package com.aegis.cli;

import com.aegis.model.mission.MissionStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure argument-parsing/error-path coverage for the Stage 3 logic moved
 * into this class during Stage 4's dispatcher refactor — the "runs a
 * real mission" path is exercised live (no browser in a unit test), same
 * as before this refactor.
 */
class RunCommandTest {

    @Test
    void missingConfigFlagFails() {
        assertEquals(1, RunCommand.run(new String[0]));
    }

    @Test
    void unknownFlagFails() {
        assertEquals(1, RunCommand.run(new String[] {"--bogus", "value"}));
    }

    @Test
    void flagMissingItsValueFails() {
        assertEquals(1, RunCommand.run(new String[] {"--config"}));
    }

    @Test
    void exitCodeMapping() {
        assertEquals(0, RunCommand.exitCodeFor(MissionStatus.SUCCESS));
        assertEquals(1, RunCommand.exitCodeFor(MissionStatus.FAILED));
        assertEquals(2, RunCommand.exitCodeFor(MissionStatus.PARTIAL));
    }
}
