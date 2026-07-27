package com.aegis.core.knowledge;

import java.util.List;

/**
 * One state's worth of {@link ElementSnapshot}s, captured by {@code
 * SignalCapturingObserver} right after {@code DefaultObserver} builds the
 * real {@code Observation} for it — {@code stateSignature} is the exact
 * same {@code StateSignature.of(observation)} every other provider in
 * this package already keys off, so correlation is precise here (unlike
 * {@link ConsoleSignal}/{@link NetworkSignal}, which only have a raw page
 * URL to go on).
 */
public record DomSignal(String stateSignature, List<ElementSnapshot> elements) {

    public DomSignal {
        elements = elements == null ? List.of() : List.copyOf(elements);
    }
}
