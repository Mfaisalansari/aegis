package com.aegis.core.bug;

import com.aegis.model.finding.FindingSeverity;

import java.time.Instant;
import java.util.Set;

/**
 * A group of Findings that share the same BugFingerprint — Phase 6's
 * "bug clustering" / "duplicate detection" output. occurrenceCount and
 * urls are the raw evidence "root cause grouping" and "severity
 * prediction" are read off of: a cluster spanning multiple distinct
 * pages suggests a shared root cause (a broken shared component) rather
 * than a page-specific glitch, and a cluster that keeps recurring gets
 * its severity escalated relative to any single member (see
 * DefaultBugClusterAnalyzer.predictSeverity) — both inferred from
 * co-occurrence, not real causal or ML analysis.
 */
public record BugCluster(
        String fingerprint,
        String representativeSummary,
        FindingSeverity severity,
        int occurrenceCount,
        Set<String> urls,
        Instant firstSeen,
        Instant lastSeen
) {

    public boolean spansMultiplePages() {
        return urls.size() > 1;
    }

    public boolean isRecurring() {
        return occurrenceCount > 1;
    }
}
