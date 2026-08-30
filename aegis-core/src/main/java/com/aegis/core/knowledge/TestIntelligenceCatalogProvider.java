package com.aegis.core.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Built-in (but opt-in — see {@link TestIntelligenceCatalog}'s Javadoc)
 * {@link KnowledgeProvider} for {@link TestIntelligenceCatalog}. Runs
 * after {@link NodeCatalog}, {@link NavigationGraphCatalog}, and {@link
 * ExperienceScoreCatalog} are available in {@link
 * KnowledgeBuildContext#partialBase()} — pure cross-referencing, no new
 * data collection of its own, same discipline {@link
 * ExperienceScoreCatalogProvider} already established for Phase 4.
 */
public final class TestIntelligenceCatalogProvider implements KnowledgeProvider {

    private final CoverageStore coverageStore;

    public TestIntelligenceCatalogProvider(CoverageStore coverageStore) {
        this.coverageStore = coverageStore;
    }

    @Override
    public TestIntelligenceCatalog provide(KnowledgeBuildContext context) {

        NodeCatalog nodeCatalog = context.partialBase().require(NodeCatalog.class);
        NavigationGraphCatalog navigationGraphCatalog = context.partialBase().require(NavigationGraphCatalog.class);
        ExperienceScoreCatalog experienceScoreCatalog = context.partialBase().require(ExperienceScoreCatalog.class);

        Set<String> thisRunNodeKeys = new LinkedHashSet<>();
        for (Node node : nodeCatalog.nodes()) {
            thisRunNodeKeys.add(node.key());
        }

        Set<String> previouslyCovered = coverageStore.load(context.applicationName()).visitedNodeKeys();

        List<String> untested = previouslyCovered.stream()
                .filter(key -> !thisRunNodeKeys.contains(key)).sorted().toList();

        List<String> newlyDiscovered = thisRunNodeKeys.stream()
                .filter(key -> !previouslyCovered.contains(key)).sorted().toList();

        List<String> deadEnds = navigationGraphCatalog.deadEndNodeKeys();
        int score = experienceScoreCatalog.score();

        List<String> recommendations = buildRecommendations(untested, newlyDiscovered, deadEnds, score);

        return new TestIntelligenceCatalog(untested, newlyDiscovered, deadEnds, score, recommendations);
    }

    private List<String> buildRecommendations(List<String> untested, List<String> newlyDiscovered, List<String> deadEnds, int score) {

        List<String> lines = new ArrayList<>();

        if (!untested.isEmpty()) {
            lines.add(untested.size() + " previously-tested screen(s) were not reached this run: " + String.join(", ", untested));
        }

        if (!deadEnds.isEmpty()) {
            lines.add(deadEnds.size() + " screen(s) had no way forward this run: " + String.join(", ", deadEnds));
        }

        if (score < 70) {
            lines.add("Experience score is " + score + "/100 — review the UX Quality and Page Inspection findings before this app ships.");
        }

        if (!newlyDiscovered.isEmpty()) {
            lines.add(newlyDiscovered.size() + " screen(s) discovered for the first time this run: " + String.join(", ", newlyDiscovered));
        }

        if (lines.isEmpty()) {
            lines.add("No coverage gaps or dead ends found relative to prior runs.");
        }

        return lines;
    }
}
