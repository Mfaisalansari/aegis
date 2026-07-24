package com.aegis.core.mission;

import com.aegis.model.mission.Mission;

import java.util.ArrayList;
import java.util.List;

/**
 * Default, always-available MissionPlanner: no model call, just a plan
 * mechanically derived from which mission parameters are actually set —
 * "there's a baseUrl, so navigate there; there's a username, so expect a
 * login step; there's a successUrlContains, so that's the goal check."
 * Generic and a little obvious compared to what a model could infer
 * from the mission's own description, but always available and always
 * accurate about what it does know.
 */
public class RuleBasedMissionPlanner implements MissionPlanner {

    @Override
    public MissionPlan plan(Mission mission) {

        List<String> steps = new ArrayList<>();

        String baseUrl = mission.parameter("baseUrl");

        if (baseUrl != null && !baseUrl.isBlank()) {
            steps.add("Navigate to " + baseUrl);
        }

        if (mission.parameter("username") != null && !mission.parameter("username").isBlank()) {
            steps.add("Fill in the provided credentials and submit the login form");
        }

        steps.add("Explore available interactive elements, favoring actions likely to make progress toward the goal");

        String successMarker = mission.parameter("successUrlContains");

        if (successMarker != null && !successMarker.isBlank()) {
            steps.add("Confirm the goal is reached: a URL containing \"" + successMarker + "\"");
        } else {
            steps.add("Continue exploring until the iteration limit is reached (no explicit success condition set)");
        }

        return new MissionPlan(List.copyOf(steps));
    }
}
