package com.aegis.core.reasoning.filter;

import com.aegis.core.reasoning.memory.VisitedStateMemory;
import com.aegis.core.world.WorldModel;
import com.aegis.model.context.MissionContext;
import com.aegis.model.reasoning.CandidateAction;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Removes candidates whose action (by type + target — see
 * WorldModel.knownDestinationsOf) is known, from history anywhere in
 * this mission, to only ever lead to states we've already visited — a
 * proven dead end, not a guess. This deliberately doesn't require the
 * candidate to have been tried from the *current* state specifically:
 * AlreadyExecutedCandidateFilter already blocks an exact repeat from the
 * same state, so anything reaching this filter is by definition new
 * *here* — the question is whether history from elsewhere already
 * answers it. Candidates with no history, or with at least one
 * destination not yet visited, are left alone; there's no evidence to
 * prune them on.
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

        return candidates.stream()
                .filter(candidate -> !isProvenDeadEnd(candidate))
                .collect(Collectors.toList());
    }

    private boolean isProvenDeadEnd(CandidateAction candidate) {

        Set<String> destinations = worldModel.knownDestinationsOf(
                candidate.action().type(), candidate.action().target());

        return !destinations.isEmpty()
                && destinations.stream().allMatch(visitedStateMemory::hasVisitedState);
    }
}
