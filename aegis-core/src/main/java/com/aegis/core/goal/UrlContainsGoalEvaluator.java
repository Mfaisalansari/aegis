package com.aegis.core.goal;

import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.MissionStatus;
import com.aegis.model.observation.Observation;

import java.util.Optional;

/**
 * Declares SUCCESS once the current URL contains the mission's
 * "successUrlContains" parameter. Missions that don't set this
 * parameter never resolve via this evaluator.
 */
public class UrlContainsGoalEvaluator implements GoalEvaluator {

    private static final String PARAMETER_KEY = "successUrlContains";

    @Override
    public Optional<MissionStatus> evaluate(MissionContext context) {

        String expected = context.getMission().parameter(PARAMETER_KEY);

        if (expected == null || expected.isBlank()) {
            return Optional.empty();
        }

        Observation observation = context.getExecutionState().getCurrentObservation();

        if (observation == null || observation.url() == null) {
            return Optional.empty();
        }

        if (observation.url().contains(expected)) {
            return Optional.of(MissionStatus.SUCCESS);
        }

        return Optional.empty();
    }
}
