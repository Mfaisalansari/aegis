package com.aegis.core.reasoning.scorer;

import com.aegis.core.reasoning.memory.VisitedStateMemory;
import com.aegis.core.world.WorldModel;
import com.aegis.model.context.MissionContext;
import com.aegis.model.reasoning.CandidateAction;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * State Prioritization (Phase 4): prefers a candidate confirmed — by
 * WorldModel history, from any state it's been tried from before — to
 * lead somewhere not yet visited, over one with no such evidence.
 *
 * Deliberately two-tier, not a full ranking by "how unexplored": a
 * candidate with at least one known-unvisited destination gets a fixed
 * bonus; everything else (no history at all, most candidates most of the
 * time) stays at its plain heuristic confidence. Candidates whose *every*
 * known destination is already visited never reach a scorer at all —
 * KnownDeadEndCandidateFilter prunes those first — so there's no third,
 * "known bad", tier to rank here.
 *
 * This is the scoring counterpart to that filter: the filter proves a
 * candidate is worthless and removes it; this rewards a candidate proven
 * to still be worth something, using the exact same WorldModel evidence.
 */
public class CoverageAwareActionScorer implements ActionScorer {

    private static final double UNEXPLORED_BONUS = 0.15;

    private final WorldModel worldModel;
    private final VisitedStateMemory visitedStateMemory;

    public CoverageAwareActionScorer(WorldModel worldModel, VisitedStateMemory visitedStateMemory) {
        this.worldModel = worldModel;
        this.visitedStateMemory = visitedStateMemory;
    }

    @Override
    public CandidateAction choose(MissionContext context, List<CandidateAction> candidates) {

        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("No candidate actions available.");
        }

        return candidates.stream()
                .max(Comparator.comparingDouble(this::scoreOf))
                .orElseThrow();
    }

    private double scoreOf(CandidateAction candidate) {

        return leadsSomewhereUnvisited(candidate)
                ? candidate.confidence() + UNEXPLORED_BONUS
                : candidate.confidence();
    }

    private boolean leadsSomewhereUnvisited(CandidateAction candidate) {

        Set<String> destinations = worldModel.knownDestinationsOf(
                candidate.action().type(), candidate.action().target());

        return destinations.stream().anyMatch(destination -> !visitedStateMemory.hasVisitedState(destination));
    }
}
