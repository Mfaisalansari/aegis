package com.aegis.core.observer;

import com.aegis.core.browser.Browser;
import com.aegis.core.browser.SignalRecorder;
import com.aegis.core.knowledge.ElementSnapshot;
import com.aegis.core.reasoning.memory.StateSignature;
import com.aegis.model.context.MissionContext;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a delegate {@link Observer} (the frozen {@code DefaultObserver} in
 * real use) to additionally capture a DOM snapshot for each settled
 * state — bounding box, computed style, accessible name, the data {@link
 * ElementInfo} was never designed to carry. {@code Observer} is frozen,
 * so this wraps it rather than modifying it, same pattern {@code
 * CompositeAnomalyDetector} already established for the frozen {@code
 * AnomalyDetector}. Only constructed (by {@code EngineFactory}) when DOM
 * capture is enabled — the delegate's own behavior is completely
 * unchanged either way, this only ever adds a recording step after it.
 */
public final class SignalCapturingObserver implements Observer {

    private final Observer delegate;
    private final Browser browser;
    private final SignalRecorder recorder;

    public SignalCapturingObserver(Observer delegate, Browser browser, SignalRecorder recorder) {
        this.delegate = delegate;
        this.browser = browser;
        this.recorder = recorder;
    }

    @Override
    public void observe(MissionContext context) {

        delegate.observe(context);

        Observation observation = context.getExecutionState().getCurrentObservation();

        if (observation == null) {
            return;
        }

        List<ElementSnapshot> snapshots = new ArrayList<>();

        for (ElementInfo element : observation.elements()) {

            ElementSnapshot snapshot = browser.captureElementSnapshot(element.locator());

            if (snapshot != null) {
                snapshots.add(snapshot);
            }
        }

        recorder.recordDomSnapshot(StateSignature.of(observation), snapshots);
    }
}
