package com.aegis.core.reasoning.filter;

import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Removes TYPE candidates targeting an input that already holds a value,
 * so the reasoner doesn't keep retyping into fields it has already filled.
 */
public class AlreadyFilledInputCandidateFilter implements CandidateFilter {

    @Override
    public List<CandidateAction> filter(MissionContext context, List<CandidateAction> candidates) {

        Observation observation = context.getExecutionState().getCurrentObservation();

        if (observation == null || observation.inputs() == null) {
            return candidates;
        }

        Map<String, ElementInfo> inputsByLocator = observation.inputs().stream()
                .collect(Collectors.toMap(
                        ElementInfo::locator,
                        element -> element,
                        (first, second) -> first
                ));

        return candidates.stream()
                .filter(candidate -> !isAlreadyFilled(candidate, inputsByLocator))
                .collect(Collectors.toList());
    }

    private boolean isAlreadyFilled(
            CandidateAction candidate,
            Map<String, ElementInfo> inputsByLocator) {

        if (candidate.action().type() != ActionType.TYPE) {
            return false;
        }

        ElementInfo input = inputsByLocator.get(candidate.action().target());

        return input != null
                && input.value() != null
                && !input.value().isBlank();
    }
}
