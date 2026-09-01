package com.aegis.core.reasoning.confidence;

import com.aegis.core.reasoning.learning.LearningEngine;
import com.aegis.core.reasoning.learning.LearningResult;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.experience.Experience;
import com.aegis.model.mission.Mission;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
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

        // base GENERIC_INPUT (0.45) + 0.05 exploration bonus
        assertEquals(0.50, estimate.confidence(), 0.0001);
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

        assertEquals(0.45, estimate.confidence(), 0.0001);
        assertFalse(estimate.reason().contains("learning-adjusted"));
    }

    @Test
    void positiveLearnedAdjustmentRaisesTheScore() {

        HeuristicCandidateConfidenceEstimator estimator = new HeuristicCandidateConfidenceEstimator(
                fixedAdjustment(Map.of("TYPE|#comment", 0.20)));

        ConfidenceEstimate estimate = estimator.estimate(context, genericInput(), ActionType.TYPE, "value");

        // base GENERIC_INPUT (0.45) + 0.20 learned adjustment
        assertEquals(0.65, estimate.confidence(), 0.0001);
        assertTrue(estimate.reason().contains("learning-adjusted +0.20"));
    }

    @Test
    void negativeLearnedAdjustmentLowersTheScore() {

        HeuristicCandidateConfidenceEstimator estimator = new HeuristicCandidateConfidenceEstimator(
                fixedAdjustment(Map.of("TYPE|#comment", -0.20)));

        ConfidenceEstimate estimate = estimator.estimate(context, genericInput(), ActionType.TYPE, "value");

        assertEquals(0.25, estimate.confidence(), 0.0001);
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

        assertEquals(0.50, estimate.confidence(), 0.0001);
    }

    @Test
    void anEmptyGenericInputOutranksAGenericClick() {

        // Regression test for a real-world stuck mission: a checkout
        // form's own first-name/last-name/postal-code fields (none of
        // them "credential-like") never got filled in because an
        // unrelated generic click (e.g. Cancel) always outranked typing
        // into them, and the Continue button — capped below both until
        // the fields were filled — could never break that deadlock
        // either. An empty, fillable field must be prioritized over an
        // unrelated click so multi-field, non-login forms actually get
        // completed.
        HeuristicCandidateConfidenceEstimator estimator =
                new HeuristicCandidateConfidenceEstimator(fixedAdjustment(Map.of()));

        ConfidenceEstimate typeEstimate = estimator.estimate(context, genericInput(), ActionType.TYPE, "value");
        ConfidenceEstimate clickEstimate = estimator.estimate(context, genericButton(""), ActionType.CLICK, "");

        assertTrue(typeEstimate.confidence() > clickEstimate.confidence());
    }

    @Test
    void aContinueButtonIsRecognizedAsSubmitLikeOnceInputsAreFilled() {

        // Regression test: looksLikeSubmit() only recognized login-style
        // wording ("login"/"sign in"/"submit"), so a checkout's own
        // "Continue" button was permanently treated as a generic click —
        // even once every field was actually filled in, it never rose to
        // SUBMIT_READY the way a login button does.
        HeuristicCandidateConfidenceEstimator estimator =
                new HeuristicCandidateConfidenceEstimator(fixedAdjustment(Map.of()));

        MissionContext filledContext = contextWithFilledInput();

        ConfidenceEstimate estimate =
                estimator.estimate(filledContext, genericButton("Continue"), ActionType.CLICK, "");

        assertEquals(0.80, estimate.confidence(), 0.0001);
        assertTrue(estimate.reason().contains("Submit-like button and visible inputs are filled"));
    }

    @Test
    void continueShoppingIsNotTreatedAsASubmitButtonDespiteContainingContinue() {

        // Regression test: saucedemo.com's cart page has a real
        // "Continue Shopping" button that navigates BACKWARD to the
        // product list — the opposite of checkout's forward-advancing
        // "Continue". A naive text.contains("continue") match (this
        // class's first attempt at recognizing "Continue" as
        // submit-like) treated the two as equivalent, so "Continue
        // Shopping" got boosted to SUBMIT_READY confidence (since a page
        // with no inputs vacuously satisfies requiredInputsFilled) and
        // kept pulling exploration backward into the product list
        // instead of forward toward checkout. Only an exact "continue"
        // label — not a superstring of it — may match.
        HeuristicCandidateConfidenceEstimator estimator =
                new HeuristicCandidateConfidenceEstimator(fixedAdjustment(Map.of()));

        ConfidenceEstimate estimate =
                estimator.estimate(context, genericButton("Continue Shopping"), ActionType.CLICK, "");

        // GENERIC_CLICK (0.40) + exploration bonus (0.05), NOT SUBMIT_READY.
        assertEquals(0.45, estimate.confidence(), 0.0001);
        assertTrue(estimate.reason().contains("Generic click target"));
    }

    @Test
    void aCheckoutButtonIsRecognizedAsSubmitLike() {

        // The actual button that advances saucedemo.com's cart page
        // toward checkout is labelled exactly "CHECKOUT" — must be
        // recognized as submit-like too, not just "Continue"/"Next".
        HeuristicCandidateConfidenceEstimator estimator =
                new HeuristicCandidateConfidenceEstimator(fixedAdjustment(Map.of()));

        ConfidenceEstimate estimate = estimator.estimate(context, genericButton("CHECKOUT"), ActionType.CLICK, "");

        assertTrue(estimate.reason().contains("Submit-like button"));
    }

    @Test
    void aContinueButtonIsNotSubmitReadyWhileInputsAreStillBlank() {

        HeuristicCandidateConfidenceEstimator estimator =
                new HeuristicCandidateConfidenceEstimator(fixedAdjustment(Map.of()));

        MissionContext blankContext = contextWithInput("");

        ConfidenceEstimate estimate =
                estimator.estimate(blankContext, genericButton("Continue"), ActionType.CLICK, "");

        // SUBMIT_NOT_READY (0.15) + exploration bonus (0.05)
        assertEquals(0.20, estimate.confidence(), 0.0001);
        assertTrue(estimate.reason().contains("Submit-like button but visible inputs are not filled yet"));
    }

    // Regression test for a real-world "add then undo" loop: "Add to
    // cart" and "Remove" score identically once both are equally novel
    // (both GENERIC_CLICK), so an add is routinely followed by
    // immediately removing the same item, and the mission never
    // finishes with anything actually in the cart. An element whose
    // locator matches the mission's undoActionsContain must score below
    // a plain generic click, so genuine forward progress (a still-novel
    // add-to-cart on a different product) keeps winning ties against it.
    @Test
    void anElementMatchingUndoActionsContainScoresBelowAPlainGenericClick() {

        MissionContext undoContext = new MissionContext(
                new Mission(UUID.randomUUID(), "Test", "Test", Map.of("undoActionsContain", "remove")));

        HeuristicCandidateConfidenceEstimator estimator =
                new HeuristicCandidateConfidenceEstimator(fixedAdjustment(Map.of()));

        ConfidenceEstimate undoEstimate =
                estimator.estimate(undoContext, elementWithLocator("#remove-sauce-labs-backpack"), ActionType.CLICK, "");
        ConfidenceEstimate genericEstimate =
                estimator.estimate(undoContext, elementWithLocator("#add-to-cart-sauce-labs-bike-light"), ActionType.CLICK, "");

        assertTrue(undoEstimate.confidence() < genericEstimate.confidence());
        assertTrue(undoEstimate.reason().contains("undoActionsContain"));
    }

    @Test
    void undoActionsContainHasNoEffectOnClicksThatDoNotMatchIt() {

        MissionContext undoContext = new MissionContext(
                new Mission(UUID.randomUUID(), "Test", "Test", Map.of("undoActionsContain", "remove")));

        HeuristicCandidateConfidenceEstimator estimator =
                new HeuristicCandidateConfidenceEstimator(fixedAdjustment(Map.of()));

        ConfidenceEstimate estimate =
                estimator.estimate(undoContext, elementWithLocator("#add-to-cart-sauce-labs-backpack"), ActionType.CLICK, "");

        // GENERIC_CLICK (0.40) + exploration bonus (0.05), unaffected.
        assertEquals(0.45, estimate.confidence(), 0.0001);
    }

    @Test
    void undoActionsContainUnsetLeavesEveryGenericClickUnaffected() {

        // The shared `context` field has no undoActionsContain set at all.
        HeuristicCandidateConfidenceEstimator estimator =
                new HeuristicCandidateConfidenceEstimator(fixedAdjustment(Map.of()));

        ConfidenceEstimate estimate =
                estimator.estimate(context, elementWithLocator("#remove-sauce-labs-backpack"), ActionType.CLICK, "");

        // GENERIC_CLICK (0.40) + exploration bonus (0.05) — no penalty
        // applied since the mission never opted in.
        assertEquals(0.45, estimate.confidence(), 0.0001);
    }

    private ElementInfo elementWithLocator(String locator) {
        return new ElementInfo("button", "", "", "", "button", "", true, true, locator);
    }

    private MissionContext contextWithFilledInput() {
        return contextWithInput("12345");
    }

    private MissionContext contextWithInput(String value) {

        MissionContext inputContext =
                new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", Map.of()));

        ElementInfo input = new ElementInfo("input", "postal-code", "postal-code", "", "text", value, true, true, "#postal-code");

        inputContext.getExecutionState().setCurrentObservation(
                new Observation(
                        "https://example.com/checkout", "Checkout",
                        List.of(input), List.of(), List.of(input),
                        List.of(), List.of(), Instant.now()));

        return inputContext;
    }

    private ElementInfo genericButton(String text) {
        return new ElementInfo("button", "", "", text, "button", "", true, true, "#action");
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
