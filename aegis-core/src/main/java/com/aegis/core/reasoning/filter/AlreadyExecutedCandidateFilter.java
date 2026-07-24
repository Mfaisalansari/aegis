package com.aegis.core.reasoning.filter;

import com.aegis.core.reasoning.memory.ExecutionMemory;
import com.aegis.model.context.MissionContext;
import com.aegis.model.reasoning.CandidateAction;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Removes candidates whose action has already been executed this mission,
 * preventing the reasoner from repeating the same action forever when
 * multiple candidates tie on confidence.
 */
public class AlreadyExecutedCandidateFilter implements CandidateFilter {

    private final ExecutionMemory memory;

    public AlreadyExecutedCandidateFilter(ExecutionMemory memory) {
        this.memory = memory;
    }

    @Override
    public List<CandidateAction> filter(MissionContext context, List<CandidateAction> candidates) {

        return candidates.stream()
                .filter(candidate -> !memory.hasExecuted(candidate.action()))
                .collect(Collectors.toList());
    }
}
