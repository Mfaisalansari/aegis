package com.aegis.core.reasoning.generator;

import com.aegis.core.reasoning.mapper.ElementActionMapper;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates DOUBLE_CLICK candidates for whichever elements the supplied
 * ElementActionMapper already says are CLICK-capable — a real dblclick
 * event, to probe for duplicate-submission bugs (double-charge,
 * double-order, ...) that a well-behaved single-click exploration would
 * never trigger. Delegates the "is this element clickable" question to
 * the mapper instead of re-deriving it, so this can't quietly drift out
 * of sync with what CLICK candidates are actually offered for.
 *
 * Opt-in via the "doubleClicks" mission parameter (value "enabled",
 * default off) so an ordinary login/registration mission isn't disrupted
 * by unexpected double-submissions. Confidence is fixed and deliberately
 * low, same reasoning as PageLevelCandidateActionGenerator: this is a
 * stress probe, not something that should out-compete legitimate
 * progress toward the goal under most exploration strategies.
 */
public class DoubleClickCandidateActionGenerator implements CandidateActionGenerator {

    public static final String PARAMETER_KEY = "doubleClicks";
    public static final String ENABLED_VALUE = "enabled";

    private static final double CONFIDENCE = 0.2;

    private final ElementActionMapper mapper;

    public DoubleClickCandidateActionGenerator(ElementActionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<CandidateAction> generate(MissionContext context) {

        String requested = context.getMission().parameter(PARAMETER_KEY);

        if (!ENABLED_VALUE.equalsIgnoreCase(requested)) {
            return List.of();
        }

        Observation observation = context.getExecutionState().getCurrentObservation();

        if (observation == null) {
            return List.of();
        }

        List<CandidateAction> candidates = new ArrayList<>();

        addCandidatesFor(observation.buttons(), candidates);
        addCandidatesFor(observation.inputs(), candidates);
        addCandidatesFor(observation.links(), candidates);

        return candidates;
    }

    private void addCandidatesFor(List<ElementInfo> elements, List<CandidateAction> candidates) {

        if (elements == null) {
            return;
        }

        for (ElementInfo element : elements) {

            if (mapper.supportedActions(element).contains(ActionType.CLICK)) {
                candidates.add(candidate(element));
            }
        }
    }

    private CandidateAction candidate(ElementInfo element) {

        String reasoning = "Double-click stress test: probes " + element.locator()
                + " for duplicate-submission bugs";

        Action action = new Action(
                UUID.randomUUID(),
                ActionType.DOUBLE_CLICK,
                element.locator(),
                "",
                reasoning,
                CONFIDENCE,
                "Execute DOUBLE_CLICK",
                Duration.ofSeconds(5),
                Instant.now(),
                element.tag()
        );

        return new CandidateAction(action, CONFIDENCE, reasoning);
    }
}
