package com.aegis.core.reasoning.confidence;

import com.aegis.core.reasoning.learning.LearningEngine;
import com.aegis.core.reasoning.learning.LearningResult;
import com.aegis.core.reasoning.value.FieldPurpose;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;

public class HeuristicCandidateConfidenceEstimator implements CandidateConfidenceEstimator {

    private static final double CREDENTIAL_INPUT = 0.85;
    private static final double GENERIC_INPUT = 0.30;
    private static final double SUBMIT_READY = 0.75;
    private static final double SUBMIT_NOT_READY = 0.15;
    private static final double GENERIC_CLICK = 0.40;
    private static final double DEFAULT_CONFIDENCE = 0.50;
    private static final double EXPLORATION_BONUS = 0.05;

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
        String text = safe(element.text()).toLowerCase();

        return type.equals("submit")
                || text.contains("login")
                || text.contains("sign in")
                || text.contains("submit");
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
