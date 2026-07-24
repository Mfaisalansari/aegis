package com.aegis.core.reasoning.confidence;

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

    @Override
    public ConfidenceEstimate estimate(
            MissionContext context,
            ElementInfo element,
            ActionType actionType,
            String value) {

        return switch (actionType) {
            case TYPE -> estimateType(element);
            case CLICK -> estimateClick(context, element);
            default -> new ConfidenceEstimate(
                    DEFAULT_CONFIDENCE,
                    "No heuristic for " + actionType);
        };
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
