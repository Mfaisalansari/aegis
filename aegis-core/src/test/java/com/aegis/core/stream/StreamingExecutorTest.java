package com.aegis.core.stream;

import com.aegis.core.executor.Executor;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingExecutorTest {

    private final Action action = new Action(
            UUID.randomUUID(), ActionType.CLICK, "[id='login-button']", null,
            "test", 0.8, "test", Duration.ofSeconds(5), Instant.now(), "button");

    @Test
    void emitsAnExecuteEventOnSuccess() {

        List<MissionStreamEvent> recorded = new ArrayList<>();
        Executor fake = (a, context) -> { };
        StreamingExecutor executor = new StreamingExecutor(fake, recorded::add);

        executor.execute(action, new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", Map.of())));

        assertEquals(1, recorded.size());
        MissionStreamEvent event = recorded.get(0);
        assertEquals(MissionStreamEvent.Kind.EXECUTE, event.kind());
        assertEquals("Execution successful", event.headline());
        assertTrue(event.detail().contains("CLICK [id='login-button']"));
        assertTrue(event.detail().contains("ms"));
    }

    @Test
    void stillEmitsAnExecuteEventOnFailureAndRethrows() {

        List<MissionStreamEvent> recorded = new ArrayList<>();
        Executor fake = (a, context) -> { throw new RuntimeException("boom"); };
        StreamingExecutor executor = new StreamingExecutor(fake, recorded::add);

        MissionContext context = new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", Map.of()));

        assertThrows(RuntimeException.class, () -> executor.execute(action, context));

        assertEquals(1, recorded.size());
        assertEquals("Execution failed", recorded.get(0).headline());
    }
}
