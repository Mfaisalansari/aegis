package com.aegis.core.knowledge;

import java.util.List;
import java.util.Map;

/**
 * A discovered state, dressed with a name. References its {@link
 * State} by {@code stateId}, never duplicates the raw signature/url —
 * {@link NodeCatalog} always resolves back through {@link StateCatalog}
 * for those. {@code key} is the stable, cross-run identity (unlike
 * {@code stateId}'s "S1"-style per-run label) — see the Knowledge
 * Enrichment Layer section of ARCHITECTURE.md for why the two are kept
 * separate.
 */
public record Node(
        String stateId,
        String key,
        String displayName,
        String technicalName,
        List<String> aliases,
        NameSource nameSource,
        Map<String, String> metadata
) {

    public Node {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
