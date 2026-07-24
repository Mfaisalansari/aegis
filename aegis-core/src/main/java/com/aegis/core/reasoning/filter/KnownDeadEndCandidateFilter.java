package com.aegis.core.reasoning.filter;

import com.aegis.core.reasoning.memory.VisitedStateMemory;
import com.aegis.core.world.WorldModel;
import com.aegis.model.context.MissionContext;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Removes candidates whose destination is already known (because this
 * exact action has been tried from this exact state before) and that
 * destination is a state we've already visited — a proven dead end, not
 * a guess. Candidates with an unknown destination (never tried from here)
 * are left alone; there's no evidence to prune them on.
 */
public class KnownDeadEndCandidateFilter implements CandidateFilter {

    private final WorldModel worldModel;
    private final VisitedStateMemory visitedStateMemory;

    public KnownDeadEndCandidateFilter(WorldModel worldModel, VisitedStateMemory visitedStateMemory) {
        this.worldModel = worldModel;
        this.visitedStateMemory = visitedStateMemory;
    }

    @Override
    public List<CandidateAction> filter(MissionContext context, List<CandidateAction> candidates) {

        Observation current = context.getExecutionState().getCurrentObservation();

        if (current == null) {
            return candidates;
        }

        return candidates.stream()
                .filter(candidate -> !isProvenDeadEnd(current, candidate))
                .collect(Collectors.toList());
    }

    private boolean isProvenDeadEnd(Observation current, CandidateAction candidate) {

        Optional<String> destination = worldModel.knownDestinationOf(current, candidate.action());

        return destination.isPresent() && visitedStateMemory.hasVisitedState(destination.get());
    }
}
