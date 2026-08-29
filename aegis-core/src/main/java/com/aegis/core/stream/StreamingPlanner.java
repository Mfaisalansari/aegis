package com.aegis.core.stream;

import com.aegis.core.planner.Planner;
import com.aegis.core.report.LearningAdjustmentGlossary;
import com.aegis.model.action.Action;
import com.aegis.model.context.MissionContext;

import java.time.Instant;
import java.util.Locale;

/**
 * Decorates {@link Planner} to emit an {@link MissionStreamEvent.Kind#REASON}
 * event after each real planning decision, using the chosen {@link Action}'s
 * own {@code reasoning()}/{@code confidence()} — data the planner already
 * produces, nothing new computed here. Same decorate-don't-modify pattern as
 * {@link StreamingObserver}; {@link Planner} is a frozen Stable Component.
 */
public final class StreamingPlanner implements Planner {

    private final Planner delegate;
    private final MissionEventListener listener;

    public StreamingPlanner(Planner delegate, MissionEventListener listener) {
        this.delegate = delegate;
        this.listener = listener;
    }

    @Override
    public Action plan(MissionContext context) {

        Action action = delegate.plan(context);

        String headline = "Selected " + action.type()
                + (action.target() != null && !action.target().isBlank() ? " " + action.target() : "");
        String honestReasoning = LearningAdjustmentGlossary.honestReasoning(action.reasoning());
        String detail = (honestReasoning != null ? honestReasoning : "")
                + String.format(Locale.ROOT, " (confidence %.0f%%)", action.confidence() * 100);

        listener.onEvent(new MissionStreamEvent(
                Instant.now(), MissionStreamEvent.Kind.REASON, headline, detail,
                context.getExecutionState().getIteration()));

        return action;
    }
}
