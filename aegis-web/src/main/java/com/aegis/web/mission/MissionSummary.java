package com.aegis.web.mission;

import com.aegis.core.AegisReport;
import com.aegis.core.report.FindingCategory;
import com.aegis.core.report.PageCoverage;
import com.aegis.model.mission.MissionStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the dashboard/coverage-map/runs-list/status-page need from a
 * finished mission, computed once from the live {@link AegisReport} at
 * completion time — never re-derived from a lossy round-trip through the
 * human-facing JSON report. This is what makes a {@link MissionJob}
 * reloaded from {@link MissionHistoryStore} indistinguishable from a live
 * one to every dashboard-facing view: both expose a {@code summary()},
 * one computed live, one deserialized from disk.
 */
public record MissionSummary(
        MissionStatus status,
        double coveragePercent,
        int elementsDiscovered,
        int elementsInteracted,
        List<PageCoverageEntry> pageCoverage,
        int actionsExecuted,
        Map<FindingCategory, Integer> findingsByCategoryCounts,
        List<String> planSteps) {

    public record PageCoverageEntry(String url, int elementsDiscovered, int elementsInteracted, double coveragePercent) {
    }

    public static MissionSummary from(AegisReport report) {

        var reportData = report.reportData();
        var coverage = reportData.coverage();

        List<PageCoverageEntry> pageCoverage = new ArrayList<>();
        for (PageCoverage page : reportData.pageCoverage()) {
            pageCoverage.add(new PageCoverageEntry(
                    page.url(), page.elementsDiscovered(), page.elementsInteracted(), page.coveragePercent()));
        }

        Map<FindingCategory, Integer> findingsByCategoryCounts = new LinkedHashMap<>();
        for (var entry : reportData.findingsByCategory().entrySet()) {
            int occurrences = entry.getValue().stream().mapToInt(cluster -> cluster.occurrenceCount()).sum();
            findingsByCategoryCounts.put(entry.getKey(), occurrences);
        }

        return new MissionSummary(
                report.status(),
                coverage.coveragePercent(),
                coverage.elementsDiscovered(),
                coverage.elementsInteracted(),
                pageCoverage,
                reportData.actionsExecuted(),
                findingsByCategoryCounts,
                List.copyOf(report.plan().steps()));
    }
}
