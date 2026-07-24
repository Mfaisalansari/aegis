package com.aegis.core.bug;

import com.aegis.model.finding.Finding;
import com.aegis.model.finding.FindingSeverity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultBugClusterAnalyzerTest {

    private final DefaultBugClusterAnalyzer analyzer = new DefaultBugClusterAnalyzer();

    @Test
    void emptyFindingsProduceNoClusters() {
        assertEquals(List.of(), analyzer.analyze(List.of()));
        assertEquals(List.of(), analyzer.analyze(null));
    }

    @Test
    void similarFindingsCollapseIntoOneClusterWithACorrectOccurrenceCount() {

        List<Finding> findings = List.of(
                finding("REQUEST_FAILED: /api/item/4/add-to-cart returned 500", FindingSeverity.MEDIUM, "https://example.com/a"),
                finding("REQUEST_FAILED: /api/item/7/add-to-cart returned 500", FindingSeverity.MEDIUM, "https://example.com/a")
        );

        List<BugCluster> clusters = analyzer.analyze(findings);

        assertEquals(1, clusters.size());
        assertEquals(2, clusters.get(0).occurrenceCount());
        assertTrue(clusters.get(0).isRecurring());
    }

    @Test
    void distinctFindingsProduceSeparateClusters() {

        List<Finding> findings = List.of(
                finding("CRASH: tab crashed", FindingSeverity.CRITICAL, "https://example.com/a"),
                finding("CONSOLE_ERROR: something else entirely", FindingSeverity.MEDIUM, "https://example.com/a")
        );

        assertEquals(2, analyzer.analyze(findings).size());
    }

    @Test
    void clusterSpanningMultiplePagesIsFlagged() {

        List<Finding> findings = List.of(
                finding("REQUEST_FAILED: /api/thing/1 failed", FindingSeverity.MEDIUM, "https://example.com/a"),
                finding("REQUEST_FAILED: /api/thing/2 failed", FindingSeverity.MEDIUM, "https://example.com/b")
        );

        BugCluster cluster = analyzer.analyze(findings).get(0);

        assertTrue(cluster.spansMultiplePages());
        assertEquals(2, cluster.urls().size());
    }

    @Test
    void clusterConfinedToOnePageIsNotFlaggedAsCrossPage() {

        List<Finding> findings = List.of(
                finding("REQUEST_FAILED: /api/thing/1 failed", FindingSeverity.MEDIUM, "https://example.com/a"),
                finding("REQUEST_FAILED: /api/thing/2 failed", FindingSeverity.MEDIUM, "https://example.com/a")
        );

        BugCluster cluster = analyzer.analyze(findings).get(0);

        assertFalse(cluster.spansMultiplePages());
    }

    @Test
    void recurringClusterAtOrAboveThresholdGetsSeverityEscalatedByOneLevel() {

        // 3 occurrences of a MEDIUM-severity finding -> escalated to HIGH.
        List<Finding> findings = List.of(
                finding("CONSOLE_ERROR: thing 1 broke", FindingSeverity.MEDIUM, "https://example.com/a"),
                finding("CONSOLE_ERROR: thing 2 broke", FindingSeverity.MEDIUM, "https://example.com/a"),
                finding("CONSOLE_ERROR: thing 3 broke", FindingSeverity.MEDIUM, "https://example.com/a")
        );

        BugCluster cluster = analyzer.analyze(findings).get(0);

        assertEquals(FindingSeverity.HIGH, cluster.severity());
    }

    @Test
    void nonRecurringClusterKeepsItsOriginalSeverity() {

        List<Finding> findings = List.of(
                finding("CONSOLE_ERROR: a one-off thing broke", FindingSeverity.MEDIUM, "https://example.com/a")
        );

        BugCluster cluster = analyzer.analyze(findings).get(0);

        assertEquals(FindingSeverity.MEDIUM, cluster.severity());
    }

    @Test
    void severityNeverEscalatesPastCritical() {

        List<Finding> findings = List.of(
                finding("CRASH: tab crashed 1", FindingSeverity.CRITICAL, "https://example.com/a"),
                finding("CRASH: tab crashed 2", FindingSeverity.CRITICAL, "https://example.com/a"),
                finding("CRASH: tab crashed 3", FindingSeverity.CRITICAL, "https://example.com/a")
        );

        BugCluster cluster = analyzer.analyze(findings).get(0);

        assertEquals(FindingSeverity.CRITICAL, cluster.severity());
    }

    @Test
    void clustersAreSortedMostSevereFirst() {

        List<Finding> findings = List.of(
                finding("CONSOLE_ERROR: minor thing", FindingSeverity.LOW, "https://example.com/a"),
                finding("CRASH: tab crashed", FindingSeverity.CRITICAL, "https://example.com/a")
        );

        List<BugCluster> clusters = analyzer.analyze(findings);

        assertEquals(FindingSeverity.CRITICAL, clusters.get(0).severity());
        assertEquals(FindingSeverity.LOW, clusters.get(1).severity());
    }

    private Finding finding(String summary, FindingSeverity severity, String url) {
        return new Finding(severity, summary, url, Instant.now().plus(1, ChronoUnit.SECONDS));
    }
}
