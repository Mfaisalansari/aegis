package com.aegis.core.reasoning.memory;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionMemoryTest {

    @Test
    void unrememberedActionHasNotBeenExecuted() {

        ExecutionMemory memory = new ExecutionMemory();

        assertFalse(memory.hasExecuted("stateA", click("#submit")));
    }

    @Test
    void rememberedActionIsExecutedFromTheSameState() {

        ExecutionMemory memory = new ExecutionMemory();

        memory.remember("stateA", click("#submit"));

        assertTrue(memory.hasExecuted("stateA", click("#submit")));
    }

    @Test
    void sameActionFromADifferentStateIsNotConsideredExecuted() {

        // Core fix this test guards: execution is scoped per state, not
        // global — a locator present on more than one page (a persistent
        // nav link) can be reconsidered from a state it hasn't been tried
        // from yet.
        ExecutionMemory memory = new ExecutionMemory();

        memory.remember("stateA", click("#nav-home"));

        assertFalse(memory.hasExecuted("stateB", click("#nav-home")));
    }

    @Test
    void differentActionFromTheSameStateIsNotConsideredExecuted() {

        ExecutionMemory memory = new ExecutionMemory();

        memory.remember("stateA", click("#submit"));

        assertFalse(memory.hasExecuted("stateA", click("#cancel")));
    }

    @Test
    void clearRemovesAllRecordedExecutions() {

        ExecutionMemory memory = new ExecutionMemory();

        memory.remember("stateA", click("#submit"));
        memory.clear();

        assertFalse(memory.hasExecuted("stateA", click("#submit")));
    }

    private Action click(String target) {
        return new Action(
                UUID.randomUUID(), ActionType.CLICK, target, "", "test",
                1.0, "test", Duration.ofSeconds(5), Instant.now(), ""
        );
    }
}
