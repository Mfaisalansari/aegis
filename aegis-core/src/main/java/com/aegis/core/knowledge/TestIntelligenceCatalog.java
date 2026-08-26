package com.aegis.core.knowledge;

import java.util.List;

/**
 * AEGIS 2.0 Phase 6 — Test Intelligence: a cross-reference of this run's
 * own coverage (Phases 1-3's Observation Sources feed every catalog here
 * identically, whichever source produced them) against {@link
 * NodeCatalog}, {@link NavigationGraphCatalog}, {@link
 * ExperienceScoreCatalog} (Phase 4), and persisted cross-run coverage
 * (Phase 5's {@link CoverageStore}) — "what to test next, what's new,
 * what needs a fix" instead of any single catalog's own narrower view.
 *
 * Built by {@link TestIntelligenceCatalogProvider}, which — unlike every
 * other catalog in this package — is deliberately NOT part of {@link
 * KnowledgeBaseBuilder#standard()}: it needs a {@link CoverageStore},
 * and giving it a silent no-arg default would mean every {@code
 * standard().build(...)} call (including every existing test, and every
 * live mission's own report) started touching the real filesystem as a
 * side effect. A caller who wants this adds it explicitly:
 * {@code KnowledgeBaseBuilder.standard().withProvider(new TestIntelligenceCatalogProvider(coverageStore))}.
 *
 * {@link #recommendations()} is rule-based, not LLM-backed — same
 * "a correct fallback always exists" discipline {@code
 * RuleBasedRecommendationEngine} already established — and, like {@code
 * ExperienceScoreCatalogProvider}'s severity weights, a defensible v1,
 * not tuned against real usage yet.
 */
public record TestIntelligenceCatalog(
        List<String> untestedNodeKeys,
        List<String> newlyDiscoveredNodeKeys,
        List<String> deadEndNodeKeys,
        int experienceScore,
        List<String> recommendations
) implements KnowledgeCatalog {

    public TestIntelligenceCatalog {
        untestedNodeKeys = untestedNodeKeys == null ? List.of() : List.copyOf(untestedNodeKeys);
        newlyDiscoveredNodeKeys = newlyDiscoveredNodeKeys == null ? List.of() : List.copyOf(newlyDiscoveredNodeKeys);
        deadEndNodeKeys = deadEndNodeKeys == null ? List.of() : List.copyOf(deadEndNodeKeys);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
    }

    @Override
    public String name() {
        return "test-intelligence";
    }
}
