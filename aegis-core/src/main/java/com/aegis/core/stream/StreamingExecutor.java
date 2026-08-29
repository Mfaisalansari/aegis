package com.aegis.core.stream;

import com.aegis.core.executor.Executor;
import com.aegis.model.action.Action;
import com.aegis.model.context.MissionContext;

import java.time.Instant;

/**
 * Decorates {@link Executor} to emit an {@link MissionStreamEvent.Kind#EXECUTE}
 * event after every action, success or failure — a failure is emitted then
 * rethrown, never swallowed, so the real mission's error handling is
 * unaffected. Same decorate-don't-modify pattern as {@link StreamingObserver};
 * {@link Executor} is a frozen Stable Component.
 */
public final class StreamingExecutor implements Executor {

    private final Executor delegate;
    private final MissionEventListener listener;

    public StreamingExecutor(Executor delegate, MissionEventListener listener) {
        this.delegate = delegate;
        this.listener = listener;
    }

    @Override
    public void execute(Action action, MissionContext context) {

        long startMillis = System.currentTimeMillis();
        int iteration = context.getExecutionState().getIteration();

        try {
            delegate.execute(action, context);
            emit(action, iteration, startMillis, true);
        } catch (RuntimeException e) {
            emit(action, iteration, startMillis, false);
            throw e;
        }
    }

    private void emit(Action action, int iteration, long startMillis, boolean success) {

        long elapsedMs = System.currentTimeMillis() - startMillis;
        String headline = success ? "Execution successful" : "Execution failed";
        String detail = action.type()
                + (action.target() != null && !action.target().isBlank() ? " " + action.target() : "")
                + " — " + elapsedMs + "ms";

        listener.onEvent(new MissionStreamEvent(
                Instant.now(), MissionStreamEvent.Kind.EXECUTE, headline, detail, iteration));
    }
}
