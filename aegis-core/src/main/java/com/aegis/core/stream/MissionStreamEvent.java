package com.aegis.core.stream;

import java.time.Instant;

/**
 * One line of a mission's live action stream — what {@link StreamingObserver},
 * {@link StreamingPlanner}, and {@link StreamingExecutor} each emit as a
 * mission runs. {@code iteration} is read straight from {@code
 * ExecutionState.getIteration()} at the moment of the event, not a
 * separately-tracked counter, so it always matches the real engine's own
 * count.
 */
public record MissionStreamEvent(Instant timestamp, Kind kind, String headline, String detail, int iteration) {

    public enum Kind {
        OBSERVE, REASON, EXECUTE
    }
}
