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
 * Generates RACE_CLICK candidates for whichever elements the supplied
 * ElementActionMapper already says are CLICK-capable — same delegation
 * reasoning as DoubleClickCandidateActionGenerator, so this can't drift
 * out of sync with what CLICK is actually offered for. Offered broadly
 * across all clickable elements rather than only "high-value" ones
 * (submit/checkout/pay); scoping to just those would need the same kind
 * of keyword heuristic RiskBasedActionScorer uses, which is a reasonable
 * future refinement but not built here — left to exploration strategy
 * and mission opt-in to decide what actually gets exercised.
 *
 * Opt-in via the "raceConditions" mission parameter (value "enabled",
 * default off). Same fixed low confidence (0.2) as the other stress
 * generators, for the same reason: a probe, not something that should
 * out-compete legitimate progress toward the goal.
 */
public class RaceClickCandidateActionGenerator implements CandidateActionGenerator {

    public static final String PARAMETER_KEY = "raceConditions";
    public static final String ENABLED_VALUE = "enabled";

    private static final double CONFIDENCE = 0.2;

    private final ElementActionMapper mapper;

    public RaceClickCandidateActionGenerator(ElementActionMapper mapper) {
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

        String reasoning = "Race-condition stress test: fires two back-to-back clicks on "
                + element.locator() + " with no wait between them";

        Action action = new Action(
                UUID.randomUUID(),
                ActionType.RACE_CLICK,
                element.locator(),
                "",
                reasoning,
                CONFIDENCE,
                "Execute RACE_CLICK",
                Duration.ofSeconds(5),
                Instant.now(),
                element.tag()
        );

        return new CandidateAction(action, CONFIDENCE, reasoning);
    }
}
