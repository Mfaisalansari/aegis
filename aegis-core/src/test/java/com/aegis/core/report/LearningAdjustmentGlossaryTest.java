package com.aegis.core.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningAdjustmentGlossaryTest {

    private static final String EXPLORATION_BONUS =
            "Generic input, no credential match (learning-adjusted +0.05, never tried yet this mission)";
    private static final String GENUINE_POSITIVE = "Persistent nav link (learning-adjusted +0.20)";
    private static final String GENUINE_NEGATIVE = "Persistent nav link (learning-adjusted -0.20)";
    private static final String NO_CLAUSE = "Generic click target";

    @Test
    void explorationBonusIsNotAGenuineLearnedAdjustment() {
        assertFalse(LearningAdjustmentGlossary.isGenuineLearnedAdjustment(EXPLORATION_BONUS));
    }

    @Test
    void positiveAndNegativeRealAdjustmentsAreGenuine() {
        assertTrue(LearningAdjustmentGlossary.isGenuineLearnedAdjustment(GENUINE_POSITIVE));
        assertTrue(LearningAdjustmentGlossary.isGenuineLearnedAdjustment(GENUINE_NEGATIVE));
    }

    @Test
    void noClauseAndNullAreNotGenuine() {
        assertFalse(LearningAdjustmentGlossary.isGenuineLearnedAdjustment(NO_CLAUSE));
        assertFalse(LearningAdjustmentGlossary.isGenuineLearnedAdjustment(null));
    }

    @Test
    void explorationBonusGetsHonestlyRelabeled() {

        String result = LearningAdjustmentGlossary.honestReasoning(EXPLORATION_BONUS);

        assertTrue(result.contains("exploring new ground"), result);
        assertFalse(result.contains("never tried yet this mission"), result);
        assertFalse(result.contains("learned"), result);
    }

    @Test
    void genuinePositiveAdjustmentIsLabeledAsLearnedSuccess() {

        String result = LearningAdjustmentGlossary.honestReasoning(GENUINE_POSITIVE);

        assertTrue(result.contains("learned: tends to work, +0.20"), result);
    }

    @Test
    void genuineNegativeAdjustmentIsLabeledAsLearnedFailure() {

        String result = LearningAdjustmentGlossary.honestReasoning(GENUINE_NEGATIVE);

        assertTrue(result.contains("learned: tends to fail, -0.20"), result);
    }

    @Test
    void noClauseIsANoOp() {
        assertEquals(NO_CLAUSE, LearningAdjustmentGlossary.honestReasoning(NO_CLAUSE));
    }

    @Test
    void nullIsANoOp() {
        assertNull(LearningAdjustmentGlossary.honestReasoning(null));
    }

    @Test
    void doesNotMangleUnrelatedParenthesesInTheBaseReason() {

        String reasoning = "Credential-like field (PASSWORD) (learning-adjusted +0.05, never tried yet this mission)";

        String result = LearningAdjustmentGlossary.honestReasoning(reasoning);

        assertTrue(result.contains("(PASSWORD)"), result);
        assertTrue(result.contains("exploring new ground"), result);
    }
}
