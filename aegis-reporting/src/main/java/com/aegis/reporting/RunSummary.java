package com.aegis.reporting;

import java.util.List;

/**
 * A business-readable distillation of one scenario/run's {@code
 * KnowledgeBase} — the one input both {@link ExecutiveSummaryReportGenerator}
 * and {@link DigestGenerator} render from, whether for a single run or a
 * whole suite (a {@code List<RunSummary>}). Built by {@link
 * RunSummaryBuilder} from catalogs/actions that already exist — no new
 * detection logic, purely extraction.
 *
 * {@code newlyDiscoveredCount}/{@code untestedCount}/{@code learningNotes}
 * carry this run's cross-run comparison against every prior run of the
 * same application (via {@code CoverageStore}) — what's new since last
 * time, what used to be reachable and wasn't reached this run. All three
 * are zero/empty when the caller didn't opt into {@code
 * TestIntelligenceCatalogProvider} (see its Javadoc for why that's opt-in).
 */
public record RunSummary(
        String name,
        boolean passed,
        int experienceScore,
        int healCount,
        int selfInputCount,
        int criticalFindingCount,
        int totalFindingCount,
        List<String> notableFindings,
        int newlyDiscoveredCount,
        int untestedCount,
        List<String> learningNotes
) {

    public RunSummary {
        notableFindings = notableFindings == null ? List.of() : List.copyOf(notableFindings);
        learningNotes = learningNotes == null ? List.of() : List.copyOf(learningNotes);
    }
}
