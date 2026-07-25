package com.aegis.model.context;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.finding.Finding;
import com.aegis.model.finding.FindingSeverity;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;
import com.aegis.model.reasoning.ReasoningStep;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionStateTest {

    @Test
    void startsAtIterationZeroWithNoHistory() {

        ExecutionState state = new ExecutionState();

        assertEquals(0, state.getIteration());
        assertEquals(0, state.getCurrentStep());
        assertTrue(state.getObservations().isEmpty());
        assertTrue(state.getActions().isEmpty());
        assertTrue(state.getFindings().isEmpty());
        assertTrue(state.getReasoningSteps().isEmpty());
    }

    @Test
    void setCurrentObservationUpdatesCurrentAndAppendsToHistory() {

        ExecutionState state = new ExecutionState();
        Observation first = observation("https://example.com/");
        Observation second = observation("https://example.com/next");

        state.setCurrentObservation(first);
        state.setCurrentObservation(second);

        assertEquals(second, state.getCurrentObservation());
        assertEquals(List.of(first, second), state.getObservations());
    }

    @Test
    void addActionAppendsAndIncrementsCurrentStep() {

        ExecutionState state = new ExecutionState();

        state.addAction(action());
        state.addAction(action());

        assertEquals(2, state.getActions().size());
        assertEquals(2, state.getCurrentStep());
    }

    @Test
    void addFindingAppends() {

        ExecutionState state = new ExecutionState();
        Finding finding = new Finding(FindingSeverity.HIGH, "something broke", "https://example.com/", Instant.now());

        state.addFinding(finding);

        assertEquals(List.of(finding), state.getFindings());
    }

    @Test
    void addReasoningStepAppends() {

        ExecutionState state = new ExecutionState();
        ReasoningStep step = new ReasoningStep(1, List.<CandidateAction>of(), null, Instant.now());

        state.addReasoningStep(step);

        assertEquals(List.of(step), state.getReasoningSteps());
    }

    @Test
    void incrementIterationAdvancesIndependentlyOfCurrentStep() {

        ExecutionState state = new ExecutionState();

        state.incrementIteration();
        state.incrementIteration();
        state.addAction(action());

        assertEquals(2, state.getIteration());
        assertEquals(1, state.getCurrentStep());
    }

    // Stage 5 hardening: the 4 list getters now return unmodifiable
    // views (see ExecutionState) — a caller reaching this through the
    // public AegisReport.missionResult().context() chain must not be
    // able to mutate AEGIS's own internal state through the getter.
    @Test
    void gettersReturnUnmodifiableViews() {

        ExecutionState state = new ExecutionState();

        assertThrows(UnsupportedOperationException.class, () -> state.getObservations().add(observation("https://x/")));
        assertThrows(UnsupportedOperationException.class, () -> state.getActions().add(action()));
        assertThrows(UnsupportedOperationException.class,
                () -> state.getFindings().add(new Finding(FindingSeverity.LOW, "x", "x", Instant.now())));
        assertThrows(UnsupportedOperationException.class,
                () -> state.getReasoningSteps().add(new ReasoningStep(1, List.of(), null, Instant.now())));
    }

    @Test
    void unmodifiableGettersStillReflectLiveGrowth() {

        ExecutionState state = new ExecutionState();
        List<Action> view = state.getActions();

        assertTrue(view.isEmpty());

        state.addAction(action());

        assertEquals(1, view.size());
    }

    private Observation observation(String url) {
        return new Observation(url, "title", List.of(), List.of(), List.of(), List.of(), List.of(), Instant.now());
    }

    private Action action() {
        return new Action(
                UUID.randomUUID(), ActionType.CLICK, "[id='submit']", null, "test action",
                0.5, "advances the mission", Duration.ofSeconds(5), Instant.now(), "button");
    }
}
