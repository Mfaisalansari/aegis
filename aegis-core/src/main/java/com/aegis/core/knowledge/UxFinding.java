package com.aegis.core.knowledge;

import com.aegis.model.finding.FindingSeverity;

import java.util.Map;

/**
 * One observation from {@link UxAnalysisCatalogProvider} about how good
 * the mission's navigation experience actually was — declared
 * independently from {@code com.aegis.core.report.Finding}/{@code
 * BugCluster} rather than forced through that "something broke" pipeline,
 * same precedent as {@code FindingRule} being declared independently of
 * the frozen {@code AnomalyDetector}. {@code evidence} is a node key,
 * journey definition key, or element locator depending on {@link #type()}.
 */
public record UxFinding(
        UxFindingType type,
        FindingSeverity severity,
        String summary,
        String evidence,
        Map<String, String> metadata
) {

    public UxFinding {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
