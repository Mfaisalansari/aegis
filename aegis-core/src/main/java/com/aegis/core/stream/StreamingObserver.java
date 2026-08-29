package com.aegis.core.stream;

import com.aegis.core.observer.Observer;
import com.aegis.model.context.MissionContext;
import com.aegis.model.observation.Observation;

import java.time.Instant;

/**
 * Decorates {@link Observer} to emit an {@link MissionStreamEvent.Kind#OBSERVE}
 * event after each real observation — same decorate-don't-modify pattern
 * {@code SelfHealingBrowser} already uses for {@link com.aegis.core.browser.Browser}.
 * {@link Observer} is a frozen Stable Component; this never touches it or
 * {@code DefaultObserver}, only wraps whichever delegate {@code
 * EngineFactory} already built.
 */
public final class StreamingObserver implements Observer {

    private final Observer delegate;
    private final MissionEventListener listener;

    public StreamingObserver(Observer delegate, MissionEventListener listener) {
        this.delegate = delegate;
        this.listener = listener;
    }

    @Override
    public void observe(MissionContext context) {

        delegate.observe(context);

        Observation observation = context.getExecutionState().getCurrentObservation();
        if (observation == null) {
            return;
        }

        int elementCount = observation.elements().size();
        String headline = "Observed " + observation.url();
        String detail = elementCount + " interactive element" + (elementCount == 1 ? "" : "s")
                + (observation.pageTitle() != null && !observation.pageTitle().isBlank() ? " — " + observation.pageTitle() : "");

        listener.onEvent(new MissionStreamEvent(
                Instant.now(), MissionStreamEvent.Kind.OBSERVE, headline, detail,
                context.getExecutionState().getIteration()));
    }
}
