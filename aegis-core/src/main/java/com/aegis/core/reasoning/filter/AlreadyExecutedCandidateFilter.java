package com.aegis.core.reasoning.filter;

import com.aegis.core.reasoning.memory.ExecutionMemory;
import com.aegis.core.reasoning.memory.StateSignature;
import com.aegis.model.context.MissionContext;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Removes candidates whose action has already been executed from this
 * exact state this mission, preventing the reasoner from repeating the
 * same action forever when multiple candidates tie on confidence. Scoped
 * per state (see ExecutionMemory) — the same locator reached from a
 * different state is a fresh question, not a repeat.
 */
public class AlreadyExecutedCandidateFilter implements CandidateFilter {

    private final ExecutionMemory memory;

    public AlreadyExecutedCandidateFilter(ExecutionMemory memory) {
        this.memory = memory;
    }

    @Override
    public List<CandidateAction> filter(MissionContext context, List<CandidateAction> candidates) {

        Observation current = context.getExecutionState().getCurrentObservation();

        if (current == null) {
            return candidates;
        }

        String stateSignature = StateSignature.of(current);

        return candidates.stream()
                .filter(candidate -> !memory.hasExecuted(stateSignature, candidate.action()))
                .collect(Collectors.toList());
    }
}
