package com.aegis.core.reasoning.confidence;

import com.aegis.core.reasoning.learning.LearningEngine;
import com.aegis.core.reasoning.learning.LearningResult;
import com.aegis.core.reasoning.value.FieldPurpose;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;

import java.util.Set;

public class HeuristicCandidateConfidenceEstimator implements CandidateConfidenceEstimator {

    private static final double CREDENTIAL_INPUT = 0.85;
    /**
     * Deliberately above {@link #GENERIC_CLICK}: an empty, visible,
     * enabled text field is the concrete next step of *any* form (a
     * checkout's name/postal-code fields, not just a login's
     * username/password), and {@link #requiredInputsFilled} can only
     * ever unblock a submit button once those fields actually get typed
     * into. With this at or below GENERIC_CLICK, a page with both an
     * unrelated link and an empty required field would always favor the
     * link, and the form's own submit button — capped at
     * {@link #SUBMIT_NOT_READY} until the fields are filled — could
     * never win either, so the fields never get a turn at all.
     */
    private static final double GENERIC_INPUT = 0.45;
    private static final double SUBMIT_READY = 0.75;
    private static final double SUBMIT_NOT_READY = 0.15;
    private static final double GENERIC_CLICK = 0.40;
    private static final double DEFAULT_CONFIDENCE = 0.50;
    private static final double EXPLORATION_BONUS = 0.05;
    /**
     * Subtracted from {@link #GENERIC_CLICK} for an element matching the
     * mission's "undoActionsContain" parameter (same substrings
     * {@code UrlContainsGoalEvaluator} uses to detect a required action
     * being reversed). Found live: "Add to cart" and "Remove" score
     * identically once both are equally novel, so an add is routinely
     * followed by immediately removing the same item, and the mission
     * never finishes with anything actually in the cart. This doesn't
     * forbid clicking an undo-shaped element — removing something is
     * still real functionality worth testing — it just stops undoing
     * being every bit as attractive as making forward progress. Missions
     * that don't set undoActionsContain see no change at all.
     */
    private static final double UNDO_ACTION_PENALTY = 0.10;

    /**
     * Text an entire button/link label must match EXACTLY (after
     * trim+lowercase), never merely contain, to be treated as a
     * step-advancing submit action. A plain {@code contains} check here
     * would also match a compound label that means the opposite thing —
     * e.g. saucedemo.com's own cart page has a real "Continue Shopping"
     * button that navigates BACKWARD to the product list, which
     * {@code text.contains("continue")} would wrongly treat as
     * equivalent to checkout's forward-advancing "Continue".
     */
    private static final Set<String> EXACT_SUBMIT_LABELS = Set.of(
            "continue", "next", "finish", "proceed", "confirm", "place order", "checkout");

    private final LearningEngine learningEngine;

    public HeuristicCandidateConfidenceEstimator(LearningEngine learningEngine) {
        this.learningEngine = learningEngine;
    }

    @Override
    public ConfidenceEstimate estimate(
            MissionContext context,
            ElementInfo element,
            ActionType actionType,
            String value) {

        ConfidenceEstimate base = switch (actionType) {
            case TYPE -> estimateType(element);
            case CLICK -> estimateClick(context, element);
            default -> new ConfidenceEstimate(
                    DEFAULT_CONFIDENCE,
                    "No heuristic for " + actionType);
        };

        return applyLearning(context, element, actionType, base);
    }

    /**
     * Nudges the heuristic score by whatever Phase 2's LearningEngine has
     * inferred about this exact (type, locator) so far this mission — e.g.
     * a click that's failed repeatedly gets deprioritized without needing
     * a hand-written rule for why. No Action object exists yet at this
     * point (this runs before DefaultActionFactory builds one), so the
     * lookup goes by ActionType + the element's own locator, which is
     * exactly what becomes the Action's target once one is built.
     *
     * An action with zero recorded Experience gets a small flat
     * EXPLORATION_BONUS instead (Phase 3) — it's not the same as landing
     * in the neutral, 0.0-adjustment success-rate bucket: we have
     * genuinely no information about it, which is itself a reason to try
     * it, distinct from having tried it and seen nothing remarkable.
     */
    private ConfidenceEstimate applyLearning(
            MissionContext context, ElementInfo element, ActionType actionType, ConfidenceEstimate base) {

        LearningResult learned = learningEngine.learn(context);
        String locator = element.locator();

        if (!learned.hasExperience(actionType, locator)) {
            return adjust(base, EXPLORATION_BONUS, "never tried yet this mission");
        }

        double adjustment = learned.adjustmentFor(actionType, locator);

        if (adjustment == 0.0) {
            return base;
        }

        return adjust(base, adjustment, null);
    }

    private ConfidenceEstimate adjust(ConfidenceEstimate base, double amount, String extraReason) {

        double adjusted = clamp(base.confidence() + amount, 0.0, 1.0);

        String sign = amount > 0 ? "+" : "";
        String suffix = String.format("%.2f", amount) + (extraReason != null ? ", " + extraReason : "");

        return new ConfidenceEstimate(
                adjusted,
                base.reason() + " (learning-adjusted " + sign + suffix + ")"
        );
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private ConfidenceEstimate estimateType(ElementInfo element) {

        FieldPurpose.Purpose purpose = FieldPurpose.of(element);

        if (purpose != FieldPurpose.Purpose.GENERIC) {
            return new ConfidenceEstimate(
                    CREDENTIAL_INPUT,
                    "Credential-like field (" + purpose + ")");
        }

        return new ConfidenceEstimate(
                GENERIC_INPUT,
                "Generic input, no credential match");
    }

    private ConfidenceEstimate estimateClick(MissionContext context, ElementInfo element) {

        if (!looksLikeSubmit(element)) {

            if (looksLikeUndoAction(context, element)) {
                return new ConfidenceEstimate(
                        GENERIC_CLICK - UNDO_ACTION_PENALTY,
                        "Generic click target, but matches the mission's undoActionsContain");
            }

            return new ConfidenceEstimate(GENERIC_CLICK, "Generic click target");
        }

        if (requiredInputsFilled(context)) {
            return new ConfidenceEstimate(
                    SUBMIT_READY,
                    "Submit-like button and visible inputs are filled");
        }

        return new ConfidenceEstimate(
                SUBMIT_NOT_READY,
                "Submit-like button but visible inputs are not filled yet");
    }

    private boolean looksLikeSubmit(ElementInfo element) {

        String type = safe(element.type()).toLowerCase();
        String text = safe(element.text()).toLowerCase().trim();

        // A real type="submit" attribute or login-style wording covers
        // the original login-only case. Beyond that, a multi-step flow's
        // own step-advancing button (a checkout's "Continue"/"CHECKOUT",
        // a wizard's "Next") is just as much a submit action as a login
        // button — restricting this to login-only wording left every
        // other form's own submit button permanently stuck at
        // GENERIC_CLICK, never reaching SUBMIT_READY/SUBMIT_NOT_READY at
        // all. Matched by exact label, not substring — see
        // EXACT_SUBMIT_LABELS's Javadoc for why.
        return type.equals("submit")
                || text.contains("login")
                || text.contains("sign in")
                || text.contains("submit")
                || EXACT_SUBMIT_LABELS.contains(text);
    }

    private boolean looksLikeUndoAction(MissionContext context, ElementInfo element) {

        String undoParameter = context.getMission().parameter("undoActionsContain");

        if (undoParameter == null || undoParameter.isBlank()) {
            return false;
        }

        String locator = safe(element.locator()).toLowerCase();

        for (String undo : undoParameter.split(",")) {

            String trimmed = undo.trim().toLowerCase();

            if (!trimmed.isEmpty() && locator.contains(trimmed)) {
                return true;
            }
        }

        return false;
    }

    private boolean requiredInputsFilled(MissionContext context) {

        Observation observation = context.getExecutionState().getCurrentObservation();

        if (observation == null || observation.inputs() == null || observation.inputs().isEmpty()) {
            return true;
        }

        return observation.inputs().stream()
                .filter(ElementInfo::visible)
                .filter(ElementInfo::enabled)
                .allMatch(input -> input.value() != null && !input.value().isBlank());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
