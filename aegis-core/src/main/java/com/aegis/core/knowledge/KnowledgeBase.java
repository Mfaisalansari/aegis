package com.aegis.core.knowledge;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A type-safe heterogeneous container of {@link KnowledgeCatalog}s —
 * deliberately not a fixed-field record. Today's 4 built-in catalogs
 * (state/node/flow/journey) and every future one (risk, business
 * metadata, requirements, defects, historical learning — see
 * ARCHITECTURE.md's Knowledge Enrichment Layer section) are all stored
 * and retrieved the same way, keyed by concrete type, so adding a new
 * catalog later never means redesigning this class.
 */
public final class KnowledgeBase {

    private final Map<Class<? extends KnowledgeCatalog>, KnowledgeCatalog> catalogsByType;

    KnowledgeBase(Map<Class<? extends KnowledgeCatalog>, KnowledgeCatalog> catalogsByType) {
        this.catalogsByType = new LinkedHashMap<>(catalogsByType);
    }

    public <T extends KnowledgeCatalog> Optional<T> get(Class<T> type) {
        return Optional.ofNullable(type.cast(catalogsByType.get(type)));
    }

    /** Same as {@link #get}, for a catalog callers know must already be present (the 4 built-ins, once built). */
    public <T extends KnowledgeCatalog> T require(Class<T> type) {
        return get(type).orElseThrow(() -> new IllegalStateException("Catalog not present in this KnowledgeBase: " + type.getSimpleName()));
    }

    public Collection<KnowledgeCatalog> all() {
        return catalogsByType.values();
    }
}
