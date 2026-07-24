package com.aegis.core.reasoning.confidence;

import com.aegis.core.reasoning.learning.LearningEngine;
import com.aegis.core.reasoning.learning.LearningResult;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.experience.Experience;
import com.aegis.model.mission.Mission;
import com.aegis.model.observation.ElementInfo;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeuristicCandidateConfidenceEstimatorTest {

    private final MissionContext context =
            new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", Map.of()));

    @Test
    void neverTriedActionGetsTheExplorationBonus() {

        // No experience recorded for anything (empty map) -> this exact
        // action has zero history -> Phase 3 exploration bonus applies.
        HeuristicCandidateConfidenceEstimator estimator =
                new HeuristicCandidateConfidenceEstimator(fixedAdjustment(Map.of()));

        ConfidenceEstimate estimate = estimator.estimate(context, genericInput(), ActionType.TYPE, "value");

        // base GENERIC_INPUT (0.30) + 0.05 exploration bonus
        assertEquals(0.35, estimate.confidence(), 0.0001);
        assertTrue(estimate.reason().contains("learning-adjusted +0.05"));
        assertTrue(estimate.reason().contains("never tried yet"));
    }

    @Test
    void actionWithNeutralHistoryGetsNoAdjustment() {

        // Experience DOES exist for this exact action (unlike the "never
        // tried" case above), but its success rate landed in the neutral
        // 50-75% bucket -> adjustmentFor is exactly 0.0, distinct from
        // "no data" -> no exploration bonus, no learning effect at all.
        HeuristicCandidateConfidenceEstimator estimator = new HeuristicCandidateConfidenceEstimator(
                fixedAdjustment(Map.of("TYPE|#comment", 0.0)));

        ConfidenceEstimate estimate = estimator.estimate(context, genericInput(), ActionType.TYPE, "value");

        assertEquals(0.30, estimate.confidence(), 0.0001);
        assertFalse(estimate.reason().contains("learning-adjusted"));
    }

    @Test
    void positiveLearnedAdjustmentRaisesTheScore() {

        HeuristicCandidateConfidenceEstimator estimator = new HeuristicCandidateConfidenceEstimator(
                fixedAdjustment(Map.of("TYPE|#comment", 0.20)));

        ConfidenceEstimate estimate = estimator.estimate(context, genericInput(), ActionType.TYPE, "value");

        // base GENERIC_INPUT (0.30) + 0.20 learned adjustment
        assertEquals(0.50, estimate.confidence(), 0.0001);
        assertTrue(estimate.reason().contains("learning-adjusted +0.20"));
    }

    @Test
    void negativeLearnedAdjustmentLowersTheScore() {

        HeuristicCandidateConfidenceEstimator estimator = new HeuristicCandidateConfidenceEstimator(
                fixedAdjustment(Map.of("TYPE|#comment", -0.20)));

        ConfidenceEstimate estimate = estimator.estimate(context, genericInput(), ActionType.TYPE, "value");

        assertEquals(0.10, estimate.confidence(), 0.0001);
        assertTrue(estimate.reason().contains("learning-adjusted -0.20"));
    }

    @Test
    void adjustmentIsClampedToTheValidConfidenceRange() {

        HeuristicCandidateConfidenceEstimator estimator = new HeuristicCandidateConfidenceEstimator(
                fixedAdjustment(Map.of("TYPE|#credential", 0.90)));

        // CREDENTIAL_INPUT base is 0.85; +0.90 would overflow past 1.0 without clamping.
        ConfidenceEstimate estimate = estimator.estimate(context, credentialInput(), ActionType.TYPE, "value");

        assertEquals(1.0, estimate.confidence(), 0.0001);
    }

    @Test
    void adjustmentIsScopedByActionTypeAndLocatorNotJustLocator() {

        HeuristicCandidateConfidenceEstimator estimator = new HeuristicCandidateConfidenceEstimator(
                fixedAdjustment(Map.of("CLICK|#comment", 0.20)));

        // Learned adjustment is keyed to CLICK|#comment; a TYPE on the same
        // locator has no experience of its OWN and must not pick up
        // CLICK's +0.20 — it should only get the flat exploration bonus
        // (+0.05) for having zero history under its own key.
        ConfidenceEstimate estimate = estimator.estimate(context, genericInput(), ActionType.TYPE, "value");

        assertEquals(0.35, estimate.confidence(), 0.0001);
    }

    private ElementInfo genericInput() {
        return new ElementInfo("input", "comment", "comment", "", "text", "", true, true, "#comment");
    }

    private ElementInfo credentialInput() {
        return new ElementInfo("input", "password", "password", "", "password", "", true, true, "#credential");
    }

    private LearningEngine fixedAdjustment(Map<String, Double> adjustments) {
        LearningResult result = new LearningResult(adjustments);
        return new LearningEngine() {
            @Override
            public LearningResult learn(MissionContext missionContext) {
                return result;
            }

            @Override
            public void recordExperience(Experience experience) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
