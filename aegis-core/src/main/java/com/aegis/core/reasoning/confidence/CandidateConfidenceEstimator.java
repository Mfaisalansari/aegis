package com.aegis.core.reasoning.confidence;

import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.observation.ElementInfo;

/**
 * Estimates how promising a candidate action is, given the element it
 * targets and the current mission state. Kept as a swappable component so
 * it can later be replaced by an ML/LLM/RL-based estimator without
 * changing the rest of the reasoning pipeline.
 */
public interface CandidateConfidenceEstimator {

    ConfidenceEstimate estimate(
            MissionContext context,
            ElementInfo element,
            ActionType actionType,
            String value
    );

}
