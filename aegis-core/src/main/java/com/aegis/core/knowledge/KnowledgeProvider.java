package com.aegis.core.knowledge;

/**
 * Produces one {@link KnowledgeCatalog} from a mission's raw facts plus
 * whatever organization knowledge was declared. The 4 built-in catalogs
 * (state/node/flow/journey) are themselves implemented as providers —
 * no special-casing — so this same mechanism a third party would use to
 * contribute a brand-new catalog (risk metadata, defects, ...) via
 * {@code ServiceLoader} is exercised and proven by AEGIS's own built-ins,
 * not just theoretical.
 */
public interface KnowledgeProvider {

    KnowledgeCatalog provide(KnowledgeBuildContext context);
}
