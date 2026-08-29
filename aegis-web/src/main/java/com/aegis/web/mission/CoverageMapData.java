package com.aegis.web.mission;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every distinct page AEGIS has ever touched, grouped by which app
 * (mission's {@code baseUrl}) it belongs to — pure computation over a
 * snapshot of jobs, same separation-of-concerns as {@link
 * DashboardStats}. Built entirely from {@link MissionSummary#pageCoverage()},
 * computed once per mission at completion time (and surviving a restart
 * via {@code MissionHistoryStore}); the only work here is grouping and
 * picking one snapshot per page.
 *
 * When the same page was visited by more than one mission, the most
 * recently finished mission's numbers are shown as that page's current
 * state (jobs is expected most-recent-first, per {@code
 * MissionJobStore.list()}) — {@code missionCount} and {@code lastRunAt}
 * still reflect every visit, not just the one shown.
 */
public record CoverageMapData(List<SiteGroup> sites) {

    public record PageSummary(
            String url, double coveragePercent, int elementsInteracted, int elementsDiscovered,
            int missionCount, Instant lastRunAt) {
    }

    public record SiteGroup(String baseUrl, List<PageSummary> pages, double averageCoveragePercent, int missionCount) {
    }

    public static CoverageMapData compute(List<MissionJob> jobs) {

        Map<String, List<MissionJob>> jobsBySite = new LinkedHashMap<>();
        for (MissionJob job : jobs) {
            if (job.state() != MissionJob.State.DONE) {
                continue;
            }
            String baseUrl = job.mission().parameter("baseUrl");
            jobsBySite.computeIfAbsent(baseUrl == null || baseUrl.isBlank() ? "(no base URL)" : baseUrl, k -> new ArrayList<>())
                    .add(job);
        }

        List<SiteGroup> sites = new ArrayList<>();
        for (Map.Entry<String, List<MissionJob>> entry : jobsBySite.entrySet()) {
            sites.add(computeSiteGroup(entry.getKey(), entry.getValue()));
        }

        return new CoverageMapData(sites);
    }

    private static SiteGroup computeSiteGroup(String baseUrl, List<MissionJob> siteJobs) {

        Map<String, PageSummary> pageByUrl = new LinkedHashMap<>();
        Map<String, Integer> visitCounts = new LinkedHashMap<>();
        Map<String, Instant> lastRunByUrl = new LinkedHashMap<>();

        for (MissionJob job : siteJobs) {
            for (MissionSummary.PageCoverageEntry pc : job.summary().pageCoverage()) {

                visitCounts.merge(pc.url(), 1, Integer::sum);
                Instant finishedAt = job.finishedAt();
                lastRunByUrl.merge(pc.url(), finishedAt, (a, b) -> a.isAfter(b) ? a : b);

                // siteJobs is most-recent-first, so the first time a URL is seen is its most recent snapshot.
                pageByUrl.putIfAbsent(pc.url(), new PageSummary(
                        pc.url(), pc.coveragePercent(), pc.elementsInteracted(), pc.elementsDiscovered(),
                        0, null));
            }
        }

        List<PageSummary> pages = new ArrayList<>();
        for (PageSummary snapshot : pageByUrl.values()) {
            pages.add(new PageSummary(
                    snapshot.url(), snapshot.coveragePercent(), snapshot.elementsInteracted(), snapshot.elementsDiscovered(),
                    visitCounts.get(snapshot.url()), lastRunByUrl.get(snapshot.url())));
        }
        pages.sort(Comparator.comparingDouble(PageSummary::coveragePercent));

        double average = pages.stream().mapToDouble(PageSummary::coveragePercent).average().orElse(0.0);

        return new SiteGroup(baseUrl, pages, average, siteJobs.size());
    }
}
