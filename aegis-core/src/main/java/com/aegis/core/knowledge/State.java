package com.aegis.core.knowledge;

import java.util.Map;

/**
 * A single discovered application state — pure fact, zero naming or
 * human input. The Catalog-shaped restatement of exactly what {@code
 * WorldModel}/{@code ExecutionState} already observed: a distinct
 * {@code StateSignature} (url + interactive-element fingerprint), first
 * seen at this {@code id} ("S1", "S2", ... — first-discovery order
 * within this run only, see {@link StateCatalog}).
 */
public record State(
        String id,
        String stateSignature,
        String url,
        String pageTitle,
        int elementCount,
        Map<String, String> metadata
) {

    public State {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
