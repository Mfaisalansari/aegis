package com.aegis.core.reasoning.generator;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.reasoning.CandidateAction;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Generates REFRESH/BACK candidates — deliberate interruptions to the
 * current workflow, not tied to any element on the page. Unlike every
 * other candidate source, these don't go through the element-centric
 * ActionFactory/CandidateConfidenceEstimator pipeline, since there's no
 * ElementInfo to estimate confidence from.
 *
 * Opt-in via the "interruptions" mission parameter (value "enabled") so
 * existing missions that just need to complete a flow (login,
 * registration) aren't disrupted by a refresh/back-navigation they never
 * asked for.
 *
 * Confidence is fixed and deliberately low: interruption is a stress
 * probe of workflow recovery, not something that should out-compete
 * legitimate progress toward the goal under most exploration strategies.
 */
public class PageLevelCandidateActionGenerator implements CandidateActionGenerator {

    public static final String PARAMETER_KEY = "interruptions";
    public static final String ENABLED_VALUE = "enabled";

    private static final double CONFIDENCE = 0.2;

    @Override
    public List<CandidateAction> generate(MissionContext context) {

        String requested = context.getMission().parameter(PARAMETER_KEY);

        if (!ENABLED_VALUE.equalsIgnoreCase(requested)) {
            return List.of();
        }

        return List.of(
                candidate(ActionType.REFRESH, "Interrupts the current workflow by reloading the page"),
                candidate(ActionType.BACK, "Interrupts the current workflow by navigating back")
        );
    }

    private CandidateAction candidate(ActionType type, String reasoning) {

        Action action = new Action(
                UUID.randomUUID(),
                type,
                "",
                "",
                reasoning,
                CONFIDENCE,
                "Execute " + type,
                Duration.ofSeconds(5),
                Instant.now(),
                ""
        );

        return new CandidateAction(action, CONFIDENCE, reasoning);
    }
}
