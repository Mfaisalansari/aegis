package com.aegis.core.knowledge;

import java.util.List;
import java.util.Map;

/**
 * A declared business-capability grouping of nodes — definition only,
 * no ordering implied and no runtime/execution data. {@code
 * unmatchedNodeKeys} is the subset of {@code nodeKeys} with no
 * corresponding discovered {@link Node} this run — reported so a flow
 * declared for a screen AEGIS hasn't found yet is visible, not silently
 * dropped.
 */
public record Flow(
        String key,
        String name,
        List<String> nodeKeys,
        String description,
        List<String> unmatchedNodeKeys,
        Map<String, String> metadata
) {

    public Flow {
        nodeKeys = nodeKeys == null ? List.of() : List.copyOf(nodeKeys);
        unmatchedNodeKeys = unmatchedNodeKeys == null ? List.of() : List.copyOf(unmatchedNodeKeys);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
