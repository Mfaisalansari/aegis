package com.aegis.core.reasoning.scorer;

import com.aegis.core.reasoning.memory.VisitedStateMemory;
import com.aegis.core.world.WorldModel;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoverageAwareActionScorerTest {

    private final MissionContext context = new MissionContext(
            new Mission(UUID.randomUUID(), "Test", "Test", Map.of()));

    @Test
    void prefersACandidateConfirmedToLeadSomewhereUnvisitedOverAHigherConfidenceOneWithNoHistory() {

        WorldModel worldModel = new WorldModel();
        VisitedStateMemory visitedStateMemory = new VisitedStateMemory();

        Observation pageA = observation("https://example.com/a", link("#explore"));
        Observation newPage = observation("https://example.com/new", link("#explore"));

        // "#explore", tried once before (from a different state than the
        // current candidate), is confirmed to lead somewhere unvisited.
        worldModel.recordTransition(pageA, click("#explore"), newPage);
        visitedStateMemory.remember(pageA);
        // newPage deliberately not remembered — it's unvisited

        CandidateAction lowerConfidenceButConfirmedNew = candidate(click("#explore"), 0.40);
        CandidateAction higherConfidenceButUnknown = candidate(click("#unknown"), 0.50);

        CandidateAction chosen = new CoverageAwareActionScorer(worldModel, visitedStateMemory)
                .choose(context, List.of(lowerConfidenceButConfirmedNew, higherConfidenceButUnknown));

        assertEquals("#explore", chosen.action().target());
    }

    @Test
    void fallsBackToConfidenceWhenNoCandidateHasConfirmedNewTerritory() {

        WorldModel worldModel = new WorldModel();
        VisitedStateMemory visitedStateMemory = new VisitedStateMemory();

        CandidateAction low = candidate(click("#a"), 0.30);
        CandidateAction high = candidate(click("#b"), 0.70);

        CandidateAction chosen = new CoverageAwareActionScorer(worldModel, visitedStateMemory)
                .choose(context, List.of(low, high));

        assertEquals("#b", chosen.action().target());
    }

    @Test
    void aCandidateWhoseOnlyKnownDestinationIsAlreadyVisitedGetsNoBonus() {

        WorldModel worldModel = new WorldModel();
        VisitedStateMemory visitedStateMemory = new VisitedStateMemory();

        Observation pageA = observation("https://example.com/a", link("#loop"));
        Observation visited = observation("https://example.com/visited", link("#loop"));

        worldModel.recordTransition(pageA, click("#loop"), visited);
        visitedStateMemory.remember(pageA);
        visitedStateMemory.remember(visited);

        CandidateAction provenLoop = candidate(click("#loop"), 0.60);
        CandidateAction unknown = candidate(click("#fresh"), 0.55);

        CandidateAction chosen = new CoverageAwareActionScorer(worldModel, visitedStateMemory)
                .choose(context, List.of(provenLoop, unknown));

        // No bonus for #loop (its only known destination is visited), so
        // plain confidence decides: 0.60 still edges out 0.55.
        assertEquals("#loop", chosen.action().target());
    }

    @Test
    void throwsOnEmptyCandidateList() {

        WorldModel worldModel = new WorldModel();
        VisitedStateMemory visitedStateMemory = new VisitedStateMemory();

        CoverageAwareActionScorer scorer = new CoverageAwareActionScorer(worldModel, visitedStateMemory);

        try {
            scorer.choose(context, List.of());
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private Observation observation(String url, ElementInfo... elements) {
        return new Observation(url, "Title", List.of(elements), List.of(), List.of(), List.of(elements), List.of(), Instant.now());
    }

    private ElementInfo link(String locator) {
        return new ElementInfo("a", "", "", "", "link", "", true, true, locator);
    }

    private Action click(String target) {
        return new Action(
                UUID.randomUUID(), ActionType.CLICK, target, "", "test",
                0.5, "test", Duration.ofSeconds(5), Instant.now(), "a"
        );
    }

    private CandidateAction candidate(Action action, double confidence) {
        return new CandidateAction(action, confidence, "test");
    }
}
