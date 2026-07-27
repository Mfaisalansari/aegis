package com.aegis.core.knowledge;

import java.util.List;

/**
 * An execution instance — the actual, observed order of distinct nodes
 * this run visited (first-visit order, revisits collapsed). Purely a
 * fact AEGIS derives from what happened; {@code matchedDefinitionKeys}
 * lists every declared {@link JourneyDefinition} whose sequence is a
 * subsequence of what actually happened. Comparing against a
 * human-declared definition is assistance, not invented meaning — AEGIS
 * never names or defines the journey itself, only reports whether the
 * declared one was actually followed.
 */
public record Journey(List<String> actualNodeKeySequence, List<String> matchedDefinitionKeys) {

    public Journey {
        actualNodeKeySequence = actualNodeKeySequence == null ? List.of() : List.copyOf(actualNodeKeySequence);
        matchedDefinitionKeys = matchedDefinitionKeys == null ? List.of() : List.copyOf(matchedDefinitionKeys);
    }

    public boolean matchesAnyDefinition() {
        return !matchedDefinitionKeys.isEmpty();
    }
}
