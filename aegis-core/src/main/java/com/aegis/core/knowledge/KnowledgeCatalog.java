package com.aegis.core.knowledge;

/**
 * Marker interface for one catalog inside a {@link KnowledgeBase} — a
 * named, typed collection of knowledge about the application under test
 * (discovered states, human-named screens, business flows, journeys,
 * and — in future — risk metadata, requirements, defects, historical
 * learning). Every catalog, built-in or third-party, implements this so
 * {@link KnowledgeBase} can hold an open-ended set of them without ever
 * being redesigned to add a new one.
 */
public interface KnowledgeCatalog {

    /** A short, human-readable name for this catalog — e.g. "states", "nodes", "flows". */
    String name();
}
