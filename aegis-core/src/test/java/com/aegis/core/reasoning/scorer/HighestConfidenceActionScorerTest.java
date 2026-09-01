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
import static org.junit.jupiter.api.Assertions.assertThrows;

class HighestConfidenceActionScorerTest {

    private final HighestConfidenceActionScorer scorer = new HighestConfidenceActionScorer();

    @Test
    void picksTheSingleHighestConfidenceCandidate() {

        MissionContext context = context();

        CandidateAction low = candidate(ActionType.CLICK, "#a", 0.3);
        CandidateAction high = candidate(ActionType.CLICK, "#b", 0.9);

        CandidateAction chosen = scorer.choose(context, List.of(low, high));

        assertEquals("#b", chosen.action().target());
    }

    @Test
    void withNoExecutionHistoryATieDeterministicallyPicksTheFirstCandidate() {

        MissionContext context = context();

        CandidateAction refresh = candidate(ActionType.REFRESH, "", 0.2);
        CandidateAction back = candidate(ActionType.BACK, "", 0.2);

        CandidateAction chosen = scorer.choose(context, List.of(refresh, back));

        assertEquals(ActionType.REFRESH, chosen.action().type());
    }

    @Test
    void breaksATieAwayFromTheActionThatWasJustExecuted() {

        // Regression test: a real mission stuck on a fully-explored page
        // saw REFRESH/BACK tie at confidence 0.2 every single iteration,
        // and a plain Stream.max always re-picked REFRESH (the first one
        // seen) forever, burning the whole iteration budget doing
        // nothing. The scorer must break the tie toward whichever tied
        // candidate is NOT the action just executed.
        MissionContext context = context();

        context.getExecutionState().addAction(action(ActionType.REFRESH, ""));

        CandidateAction refresh = candidate(ActionType.REFRESH, "", 0.2);
        CandidateAction back = candidate(ActionType.BACK, "", 0.2);

        CandidateAction chosen = scorer.choose(context, List.of(refresh, back));

        assertEquals(ActionType.BACK, chosen.action().type());
    }

    @Test
    void aTieAmongThreeStillAvoidsExactlyRepeatingTheLastAction() {

        MissionContext context = context();

        context.getExecutionState().addAction(action(ActionType.CLICK, "#menu"));

        CandidateAction repeat = candidate(ActionType.CLICK, "#menu", 0.6);
        CandidateAction alsoTied = candidate(ActionType.CLICK, "#other", 0.6);
        CandidateAction lower = candidate(ActionType.CLICK, "#low", 0.1);

        CandidateAction chosen = scorer.choose(context, List.of(repeat, alsoTied, lower));

        assertEquals("#other", chosen.action().target());
    }

    @Test
    void aTieWhereEveryCandidateMatchesTheLastActionStillReturnsSomething() {

        // Only one distinct action is tied for the max and it happens to
        // be the one just executed — there is nothing else to break the
        // tie toward, so the scorer must not throw.
        MissionContext context = context();

        context.getExecutionState().addAction(action(ActionType.REFRESH, ""));

        CandidateAction refreshAgain = candidate(ActionType.REFRESH, "", 0.2);

        CandidateAction chosen = scorer.choose(context, List.of(refreshAgain));

        assertEquals(ActionType.REFRESH, chosen.action().type());
    }

    @Test
    void throwsOnEmptyCandidates() {

        MissionContext context = context();

        assertThrows(IllegalArgumentException.class, () -> scorer.choose(context, List.of()));
    }

    @Test
    void tiedCandidatesRotateByElementTagInsteadOfOneTypeAlwaysWinning() {

        // Regression test for a real mission where a page's shopping-cart
        // link never got clicked until every equally-generic button on
        // the page had already been tried once: DefaultObserver appends
        // links after buttons, so on every tie a plain "first in list"
        // rule always favored a button, and AlreadyExecutedCandidateFilter
        // only removed the winning button each time, letting the next
        // button keep winning too. The scorer must rotate by elementTag
        // so a never-picked type (the link) outranks a type it has
        // already picked once, not just lose to it forever.
        MissionContext context = context();

        CandidateAction button1 = candidate(ActionType.CLICK, "#button-1", 0.6, "button");
        CandidateAction button2 = candidate(ActionType.CLICK, "#button-2", 0.6, "button");
        CandidateAction link = candidate(ActionType.CLICK, "#cart-link", 0.6, "a");

        CandidateAction first = scorer.choose(context, List.of(button1, button2, link));

        assertEquals("button", first.action().elementTag());

        // button1 (the winner above) is now gone from the pool, as
        // AlreadyExecutedCandidateFilter would remove it after execution.
        CandidateAction second = scorer.choose(context, List.of(button2, link));

        assertEquals("a", second.action().elementTag());
    }

    private MissionContext context() {
        return new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", Map.of()));
    }

    private Action action(ActionType type, String target) {
        return new Action(
                UUID.randomUUID(), type, target, "", "test",
                0.5, "test", Duration.ofSeconds(5), Instant.now(), ""
        );
    }

    private CandidateAction candidate(ActionType type, String target, double confidence) {
        return candidate(type, target, confidence, "");
    }

    private CandidateAction candidate(ActionType type, String target, double confidence, String elementTag) {

        Action action = new Action(
                UUID.randomUUID(), type, target, "", "test",
                confidence, "test", Duration.ofSeconds(5), Instant.now(), elementTag
        );

        return new CandidateAction(action, confidence, "test");
    }
}
