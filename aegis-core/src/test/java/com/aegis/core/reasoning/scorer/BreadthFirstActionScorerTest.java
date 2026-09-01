package com.aegis.core.reasoning.scorer;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import com.aegis.model.reasoning.CandidateAction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BreadthFirstActionScorerTest {

    private final BreadthFirstActionScorer scorer = new BreadthFirstActionScorer();

    @Test
    void prefersAGenuineLocalElementOverALink() {

        CandidateAction button = candidate(ActionType.CLICK, "#button", 0.5, "button");
        CandidateAction link = candidate(ActionType.CLICK, "#link", 0.9, "a");

        CandidateAction chosen = scorer.choose(context(), List.of(button, link));

        assertEquals("#button", chosen.action().target());
    }

    @Test
    void fallsBackToALinkWhenNoLocalElementExists() {

        CandidateAction link = candidate(ActionType.CLICK, "#link", 0.45, "a");

        CandidateAction chosen = scorer.choose(context(), List.of(link));

        assertEquals("#link", chosen.action().target());
    }

    // Regression test for a real-world deadlock: REFRESH/BACK have no
    // element tag at all (elementTag=""), and are regenerated fresh
    // every iteration. A version of this class that treated "anything
    // that isn't a link" as local would keep preferring REFRESH/BACK
    // over an actual link forever once every genuine local element on
    // the page was exhausted — confirmed live on saucedemo.com as 19+
    // REFRESH/BACK cycles with zero progress. REFRESH/BACK must sit in
    // the same last-resort tier as links, not the preferred "local" tier.
    @Test
    void prefersALinkOverRefreshOrBackOnceNoGenuineLocalElementRemains() {

        CandidateAction refresh = candidate(ActionType.REFRESH, "", 0.2, "");
        CandidateAction back = candidate(ActionType.BACK, "", 0.2, "");
        CandidateAction link = candidate(ActionType.CLICK, "#nav-link", 0.45, "a");

        CandidateAction chosen = scorer.choose(context(), List.of(refresh, back, link));

        assertEquals("#nav-link", chosen.action().target());
    }

    @Test
    void stillPicksSomethingWhenOnlyRefreshAndBackAreAvailable() {

        CandidateAction refresh = candidate(ActionType.REFRESH, "", 0.2, "");
        CandidateAction back = candidate(ActionType.BACK, "", 0.15, "");

        CandidateAction chosen = scorer.choose(context(), List.of(refresh, back));

        assertEquals(ActionType.REFRESH, chosen.action().type());
    }

    private MissionContext context() {
        return new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", Map.of()));
    }

    private CandidateAction candidate(ActionType type, String target, double confidence, String elementTag) {

        Action action = new Action(
                UUID.randomUUID(), type, target, "", "test",
                confidence, "test", Duration.ofSeconds(5), Instant.now(), elementTag
        );

        return new CandidateAction(action, confidence, "test");
    }
}
