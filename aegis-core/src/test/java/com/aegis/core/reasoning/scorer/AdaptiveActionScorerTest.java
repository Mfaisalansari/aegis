package com.aegis.core.reasoning.scorer;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdaptiveActionScorerTest {

    @Test
    void usesPrimaryWhenTheMostRecentObservationIsItselfANewState() {

        MissionContext context = missionContext();

        observe(context, "https://example.com/a");

        CandidateAction fromPrimary = candidate("primary");
        CandidateAction fromFallback = candidate("fallback");

        AdaptiveActionScorer scorer = new AdaptiveActionScorer(
                alwaysReturns(fromPrimary), alwaysReturns(fromFallback));

        CandidateAction chosen = scorer.choose(context, List.of(fromPrimary, fromFallback));

        assertEquals("primary", chosen.action().target());
    }

    @Test
    void usesPrimaryWhenNotEnoughIterationsHavePassedYet() {

        MissionContext context = missionContext();

        // Same state twice — a revisit — but not yet at the stuck
        // threshold (3 iterations since anything new).
        observe(context, "https://example.com/a");
        observe(context, "https://example.com/a");

        CandidateAction fromPrimary = candidate("primary");
        CandidateAction fromFallback = candidate("fallback");

        AdaptiveActionScorer scorer = new AdaptiveActionScorer(
                alwaysReturns(fromPrimary), alwaysReturns(fromFallback));

        CandidateAction chosen = scorer.choose(context, List.of(fromPrimary, fromFallback));

        assertEquals("primary", chosen.action().target());
    }

    @Test
    void switchesToFallbackAfterThreeIterationsWithNoNewState() {

        MissionContext context = missionContext();

        observe(context, "https://example.com/a");
        observe(context, "https://example.com/b"); // last new state, index 1
        observe(context, "https://example.com/b"); // revisit, 1 iteration since new
        observe(context, "https://example.com/b"); // revisit, 2 iterations since new
        observe(context, "https://example.com/b"); // revisit, 3 iterations since new -> stuck

        CandidateAction fromPrimary = candidate("primary");
        CandidateAction fromFallback = candidate("fallback");

        AdaptiveActionScorer scorer = new AdaptiveActionScorer(
                alwaysReturns(fromPrimary), alwaysReturns(fromFallback));

        CandidateAction chosen = scorer.choose(context, List.of(fromPrimary, fromFallback));

        assertEquals("fallback", chosen.action().target());
    }

    @Test
    void switchesBackToPrimaryAssoonAsANewStateReappears() {

        MissionContext context = missionContext();

        observe(context, "https://example.com/a");
        observe(context, "https://example.com/b");
        observe(context, "https://example.com/b");
        observe(context, "https://example.com/b");
        observe(context, "https://example.com/b"); // stuck here
        observe(context, "https://example.com/c"); // brand new again -> unstuck

        CandidateAction fromPrimary = candidate("primary");
        CandidateAction fromFallback = candidate("fallback");

        AdaptiveActionScorer scorer = new AdaptiveActionScorer(
                alwaysReturns(fromPrimary), alwaysReturns(fromFallback));

        CandidateAction chosen = scorer.choose(context, List.of(fromPrimary, fromFallback));

        assertEquals("primary", chosen.action().target());
    }

    private MissionContext missionContext() {
        return new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", Map.of()));
    }

    private void observe(MissionContext context, String url) {
        context.getExecutionState().setCurrentObservation(
                new Observation(url, "Title", List.of(), List.of(), List.of(), List.of(), List.of(), Instant.now()));
    }

    private ActionScorer alwaysReturns(CandidateAction result) {
        return (context, candidates) -> result;
    }

    private CandidateAction candidate(String target) {

        Action action = new Action(
                UUID.randomUUID(), ActionType.CLICK, target, "", "test",
                0.5, "test", Duration.ofSeconds(5), Instant.now(), "a"
        );

        return new CandidateAction(action, action.confidence(), "test");
    }
}
