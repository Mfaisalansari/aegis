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
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlreadyExecutedCandidateFilterTest {

    @Test
    void removesACandidateAlreadyExecutedFromTheCurrentState() {

        ExecutionMemory memory = new ExecutionMemory();

        Observation pageA = observation("https://example.com/a", link("#submit"));
        Action click = click("#submit");

        memory.remember(StateSignature.of(pageA), click);

        MissionContext context = context(pageA);

        List<CandidateAction> result = new AlreadyExecutedCandidateFilter(memory)
                .filter(context, List.of(candidate(click)));

        assertTrue(result.isEmpty());
    }

    @Test
    void keepsACandidateExecutedOnlyFromADifferentState() {

        // Core fix this test guards: a persistent nav link tried once from
        // page A must still be offered as a candidate from page B — only
        // KnownDeadEndCandidateFilter, using WorldModel history, gets to
        // decide whether that's actually worth pruning.
        ExecutionMemory memory = new ExecutionMemory();

        Observation pageA = observation("https://example.com/a", link("#nav-home"));
        Observation pageB = observation("https://example.com/b", link("#nav-home"));

        memory.remember(StateSignature.of(pageA), click("#nav-home"));

        MissionContext context = context(pageB);

        List<CandidateAction> result = new AlreadyExecutedCandidateFilter(memory)
                .filter(context, List.of(candidate(click("#nav-home"))));

        assertEquals(1, result.size());
    }

    @Test
    void keepsCandidatesWhenNoObservationYetExists() {

        ExecutionMemory memory = new ExecutionMemory();

        MissionContext context = new MissionContext(
                new Mission(UUID.randomUUID(), "Test", "Test", Map.of()));

        List<CandidateAction> result = new AlreadyExecutedCandidateFilter(memory)
                .filter(context, List.of(candidate(click("#submit"))));

        assertEquals(1, result.size());
    }

    private MissionContext context(Observation current) {

        MissionContext context = new MissionContext(
                new Mission(UUID.randomUUID(), "Test", "Test", Map.of()));

        context.getExecutionState().setCurrentObservation(current);

        return context;
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
                1.0, "test", Duration.ofSeconds(5), Instant.now(), "a"
        );
    }

    private CandidateAction candidate(Action action) {
        return new CandidateAction(action, action.confidence(), "test");
    }
}
