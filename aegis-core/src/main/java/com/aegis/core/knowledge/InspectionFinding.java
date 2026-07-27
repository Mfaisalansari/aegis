package com.aegis.core.knowledge;

import com.aegis.model.finding.FindingSeverity;

import java.util.Map;

/**
 * One page-inspection defect — console error/exception, network failure,
 * broken link, or a UI check result. Declared independently from {@code
 * com.aegis.core.report.Finding}/{@code BugCluster} and from {@link
 * UxFinding}, same precedent both already established: a different kind
 * of signal deserves its own vocabulary, not a forced fit into an
 * existing one. {@code evidence} is a URL or element locator depending on
 * {@link #type()}.
 */
public record InspectionFinding(
        InspectionCheckType type,
        FindingSeverity severity,
        String summary,
        String evidence,
        Map<String, String> metadata
) {

    public InspectionFinding {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
