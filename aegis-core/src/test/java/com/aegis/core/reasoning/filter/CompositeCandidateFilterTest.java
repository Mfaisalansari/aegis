package com.aegis.core.reasoning.filter;

import com.aegis.core.reasoning.memory.ExecutionMemory;
import com.aegis.core.reasoning.memory.StateSignature;
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
import static org.junit.jupiter.api.Assertions.assertFalse;

class CompositeCandidateFilterTest {

    @Test
    void passesThroughANonEmptyFilteredResultUnchanged() {

        CompositeCandidateFilter composite = new CompositeCandidateFilter(
                List.of(dropsNothing()), new ExecutionMemory());

        CandidateAction only = candidate(ActionType.CLICK, "#a");

        List<CandidateAction> result = composite.filter(context(), List.of(only));

        assertEquals(1, result.size());
        assertEquals("#a", result.get(0).action().target());
    }

    @Test
    void fallsBackToTheOriginalListWhenNoActionHasEverBeenExecuted() {

        CompositeCandidateFilter composite = new CompositeCandidateFilter(
                List.of(dropsEverything()), new ExecutionMemory());

        CandidateAction refresh = candidate(ActionType.REFRESH, "");
        CandidateAction back = candidate(ActionType.BACK, "");

        List<CandidateAction> result = composite.filter(context(), List.of(refresh, back));

        assertEquals(2, result.size());
    }

    @Test
    void excludesOnlyTheJustExecutedActionFromTheFallback() {

        // Regression test: once every candidate has already been tried
        // from a state (AlreadyExecutedCandidateFilter empties the
        // chain), the old fallback handed back the FULL original list —
        // including the exact action just executed — letting a
        // max-confidence scorer immediately re-pick it and loop forever.
        // The fallback must drop that one action if an alternative
        // remains.
        MissionContext context = context();
        context.getExecutionState().addAction(action(ActionType.REFRESH, ""));

        CompositeCandidateFilter composite = new CompositeCandidateFilter(
                List.of(dropsEverything()), new ExecutionMemory());

        CandidateAction refresh = candidate(ActionType.REFRESH, "");
        CandidateAction back = candidate(ActionType.BACK, "");

        List<CandidateAction> result = composite.filter(context, List.of(refresh, back));

        assertEquals(1, result.size());
        assertEquals(ActionType.BACK, result.get(0).action().type());
    }

    @Test
    void fallsBackToTheFullListWhenExcludingTheLastActionWouldLeaveNothing() {

        MissionContext context = context();
        context.getExecutionState().addAction(action(ActionType.REFRESH, ""));

        CompositeCandidateFilter composite = new CompositeCandidateFilter(
                List.of(dropsEverything()), new ExecutionMemory());

        CandidateAction refreshOnly = candidate(ActionType.REFRESH, "");

        List<CandidateAction> result = composite.filter(context, List.of(refreshOnly));

        assertFalse(result.isEmpty());
        assertEquals(ActionType.REFRESH, result.get(0).action().type());
    }

    @Test
    void surfacesTheOneCandidateNeverExecutedFromThisStateEvenWhenOthersWereJustRetried() {

        // A candidate filter earlier in the chain (e.g.
        // KnownDeadEndCandidateFilter) can empty the list on its own even
        // when one candidate — here, CLICK login-button — was never
        // actually executed from this exact state. The fallback's first
        // layer (excluding only genuine already-executed repeats) must
        // surface that one candidate directly, without needing the
        // staleness-rotation layer below at all.
        Observation loginPage = observation("https://example.com/login",
                input("#user-name"), input("#password"), link("#login-button"));

        ExecutionMemory memory = new ExecutionMemory();
        String stateSignature = StateSignature.of(loginPage);
        memory.remember(stateSignature, action(ActionType.TYPE, "#user-name"));
        memory.remember(stateSignature, action(ActionType.TYPE, "#password"));

        MissionContext context = context();
        context.getExecutionState().setCurrentObservation(loginPage);
        context.getExecutionState().addAction(action(ActionType.TYPE, "#password"));

        CompositeCandidateFilter composite = new CompositeCandidateFilter(
                List.of(dropsEverything()), memory);

        CandidateAction retypeUsername = candidate(ActionType.TYPE, "#user-name");
        CandidateAction retypePassword = candidate(ActionType.TYPE, "#password");
        CandidateAction clickLogin = candidate(ActionType.CLICK, "#login-button");

        List<CandidateAction> result = composite.filter(
                context, List.of(retypeUsername, retypePassword, clickLogin));

        assertEquals(1, result.size());
        assertEquals(ActionType.CLICK, result.get(0).action().type());
        assertEquals("#login-button", result.get(0).action().target());
    }

    @Test
    void whenEveryCandidateWasAlreadyExecutedItRetriesWhicheverHasGoneLongestUntried() {

        // Regression test for a real-world stuck mission: a login page
        // reached again after logging out has the SAME StateSignature as
        // the original login (URL + locators only), so by the time it's
        // revisited, TYPE username, TYPE password, AND CLICK login-button
        // have ALL already been executed from this exact state — the
        // original login tried every field and the submit button. Layer 1
        // (excluding only never-executed candidates) is empty here; the
        // old single-last-action fallback would then leave username and
        // password free to oscillate forever, since re-clicking login is
        // never literally "the last action". Login-button was executed
        // least recently (order 2) — long before username/password were
        // retried afterward (orders 3 and 4) — so the LRU-style fallback
        // must surface it alone.
        Observation loginPage = observation("https://example.com/login",
                input("#user-name"), input("#password"), link("#login-button"));

        ExecutionMemory memory = new ExecutionMemory();
        String stateSignature = StateSignature.of(loginPage);
        memory.remember(stateSignature, action(ActionType.TYPE, "#user-name"));   // order 0
        memory.remember(stateSignature, action(ActionType.TYPE, "#password"));    // order 1
        memory.remember(stateSignature, action(ActionType.CLICK, "#login-button")); // order 2
        memory.remember(stateSignature, action(ActionType.TYPE, "#user-name"));   // order 3 (retyped)
        memory.remember(stateSignature, action(ActionType.TYPE, "#password"));    // order 4 (retyped)

        MissionContext context = context();
        context.getExecutionState().setCurrentObservation(loginPage);
        context.getExecutionState().addAction(action(ActionType.TYPE, "#password"));

        CompositeCandidateFilter composite = new CompositeCandidateFilter(
                List.of(dropsEverything()), memory);

        CandidateAction retypeUsername = candidate(ActionType.TYPE, "#user-name");
        CandidateAction retypePassword = candidate(ActionType.TYPE, "#password");
        CandidateAction clickLogin = candidate(ActionType.CLICK, "#login-button");

        List<CandidateAction> result = composite.filter(
                context, List.of(retypeUsername, retypePassword, clickLogin));

        assertEquals(1, result.size());
        assertEquals(ActionType.CLICK, result.get(0).action().type());
        assertEquals("#login-button", result.get(0).action().target());
    }

    private CandidateFilter dropsEverything() {
        return (context, candidates) -> List.of();
    }

    private CandidateFilter dropsNothing() {
        return (context, candidates) -> candidates;
    }

    private MissionContext context() {
        return new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", Map.of()));
    }

    private Observation observation(String url, ElementInfo... elements) {
        return new Observation(url, "Title", List.of(elements), List.of(), List.of(), List.of(elements), List.of(), Instant.now());
    }

    private ElementInfo input(String locator) {
        return new ElementInfo("input", "", "", "", "text", "", true, true, locator);
    }

    private ElementInfo link(String locator) {
        return new ElementInfo("a", "", "", "", "link", "", true, true, locator);
    }

    private Action action(ActionType type, String target) {
        return new Action(
                UUID.randomUUID(), type, target, "", "test",
                0.2, "test", Duration.ofSeconds(5), Instant.now(), ""
        );
    }

    private CandidateAction candidate(ActionType type, String target) {

        Action action = action(type, target);

        return new CandidateAction(action, action.confidence(), "test");
    }
}
