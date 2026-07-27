package com.aegis.core.knowledge;

import java.util.List;
import java.util.Map;

/**
 * A declared, ordered reference path through the app — the definition
 * side of the flow/journey split: what a journey called "New Customer
 * Onboarding" is supposed to look like. Never a claim about what
 * actually happened this run — see {@link Journey} for that.
 */
public record JourneyDefinition(
        String key,
        String name,
        List<String> nodeKeys,
        String description,
        List<String> unmatchedNodeKeys,
        Map<String, String> metadata
) {

    public JourneyDefinition {
        nodeKeys = nodeKeys == null ? List.of() : List.copyOf(nodeKeys);
        unmatchedNodeKeys = unmatchedNodeKeys == null ? List.of() : List.copyOf(unmatchedNodeKeys);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
