package com.aegis.core.reasoning.memory;

import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;

import java.util.stream.Collectors;

/**
 * The shared definition of a "state": a page URL plus the set of
 * interactive element locators on it. Element values are intentionally
 * ignored, so filling in a field isn't a new state — only navigating
 * somewhere new is. Used by both VisitedStateMemory and WorldModel so
 * they agree on what counts as "the same place".
 */
public final class StateSignature {

    private StateSignature() {
    }

    public static String of(Observation observation) {

        String elementSignature = observation.elements().stream()
                .map(ElementInfo::locator)
                .sorted()
                .collect(Collectors.joining(","));

        return observation.url() + "|" + elementSignature;
    }
}
