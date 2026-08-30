package com.aegis.core.knowledge;

import com.aegis.model.finding.FindingSeverity;

import java.util.EnumMap;
import java.util.Map;

/**
 * Built-in {@link KnowledgeProvider} for {@link ExperienceScoreCatalog}.
 * Runs last in {@link KnowledgeBaseBuilder#standard()} since it's a pure
 * rollup of three earlier catalogs' own findings, not a new source of
 * facts.
 *
 * <b>Honest caveat, same discipline as Phase 5's scoring heuristic in the
 * platform-evolution plan</b>: the severity weights and per-loop/per-dead-
 * end point values below are a defensible starting point, not an
 * empirically validated formula — nothing in this codebase has real usage
 * data yet to tune them against. They exist so a score is available at
 * all; treat the exact number as directional, not authoritative, until
 * real missions have been run against it.
 */
public final class ExperienceScoreCatalogProvider implements KnowledgeProvider {

    private static final Map<FindingSeverity, Integer> SEVERITY_WEIGHTS = Map.of(
            FindingSeverity.LOW, 1,
            FindingSeverity.MEDIUM, 3,
            FindingSeverity.HIGH, 7,
            FindingSeverity.CRITICAL, 15
    );

    private static final int POINTS_PER_LOOP = 5;
    private static final int POINTS_PER_DEAD_END = 8;

    @Override
    public ExperienceScoreCatalog provide(KnowledgeBuildContext context) {

        UxFindingCatalog uxCatalog = context.partialBase().require(UxFindingCatalog.class);
        InspectionCatalog inspectionCatalog = context.partialBase().require(InspectionCatalog.class);
        NavigationGraphCatalog navigationCatalog = context.partialBase().require(NavigationGraphCatalog.class);

        Map<FindingSeverity, Integer> counts = new EnumMap<>(FindingSeverity.class);
        for (FindingSeverity severity : FindingSeverity.values()) {
            counts.put(severity, 0);
        }

        int frictionPoints = 0;

        for (UxFinding finding : uxCatalog.findings()) {
            counts.merge(finding.severity(), 1, Integer::sum);
            frictionPoints += SEVERITY_WEIGHTS.get(finding.severity());
        }

        for (InspectionFinding finding : inspectionCatalog.findings()) {
            counts.merge(finding.severity(), 1, Integer::sum);
            frictionPoints += SEVERITY_WEIGHTS.get(finding.severity());
        }

        frictionPoints += navigationCatalog.loops().size() * POINTS_PER_LOOP;
        frictionPoints += navigationCatalog.deadEndNodeKeys().size() * POINTS_PER_DEAD_END;

        int totalFindings = uxCatalog.findings().size() + inspectionCatalog.findings().size();
        int score = Math.max(0, 100 - frictionPoints);

        return new ExperienceScoreCatalog(score, totalFindings, frictionPoints, counts);
    }
}
