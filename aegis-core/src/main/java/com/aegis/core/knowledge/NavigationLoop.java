package com.aegis.core.knowledge;

import java.util.List;

/**
 * A cycle actually walked this run — the chronological node-key path from
 * a node's first visit back to its next visit (first key equals last key).
 * Deliberately reports what was actually traversed, not every
 * theoretically-possible cycle in the abstract edge graph — same "facts,
 * not invented meaning" discipline the rest of this package follows.
 */
public record NavigationLoop(List<String> nodeKeySequence) {

    public NavigationLoop {
        nodeKeySequence = nodeKeySequence == null ? List.of() : List.copyOf(nodeKeySequence);
    }
}
