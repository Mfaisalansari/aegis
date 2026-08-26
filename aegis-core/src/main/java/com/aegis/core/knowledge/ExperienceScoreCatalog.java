package com.aegis.core.knowledge;

import com.aegis.model.finding.FindingSeverity;

import java.util.Map;

/**
 * AEGIS 2.0 Phase 4 — one aggregate read on how good this run's
 * navigation experience was: a 0-100 {@code score} (100 = no friction
 * detected), the raw weighted {@code frictionPoints} it was derived from,
 * and how many findings landed at each severity. Built by {@link
 * ExperienceScoreCatalogProvider} by combining {@link UxFindingCatalog},
 * {@link InspectionCatalog}, and {@link NavigationGraphCatalog} — no new
 * data collection of its own, purely a rollup of what those three
 * catalogs already found.
 */
public record ExperienceScoreCatalog(
        int score,
        int totalFindings,
        int frictionPoints,
        Map<FindingSeverity, Integer> findingCountsBySeverity
) implements KnowledgeCatalog {

    public ExperienceScoreCatalog {
        findingCountsBySeverity = findingCountsBySeverity == null ? Map.of() : Map.copyOf(findingCountsBySeverity);
    }

    @Override
    public String name() {
        return "experience-score";
    }
}
