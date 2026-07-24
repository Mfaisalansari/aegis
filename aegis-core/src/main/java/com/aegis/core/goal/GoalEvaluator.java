package com.aegis.core.goal;

import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.MissionStatus;

import java.util.Optional;

/**
 * Decides whether a mission has reached a terminal outcome based on the
 * current context. Returns empty while the mission should keep running.
 */
public interface GoalEvaluator {

    Optional<MissionStatus> evaluate(MissionContext context);

}
