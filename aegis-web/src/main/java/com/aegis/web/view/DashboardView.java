package com.aegis.web.view;

import com.aegis.core.report.FindingCategory;
import com.aegis.web.mission.MissionJob;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static com.aegis.web.view.Layout.escapeHtml;

/**
 * Renders {@code GET /} — the "2a" bento overview: a live tile for
 * whichever mission is currently running, coverage/findings summaries
 * over recent finished runs, and a curated recent-runs list linking to
 * {@code /runs} for the full history. Reads {@link MissionJob#summary()}
 * rather than {@code job.report()} directly so this renders identically
 * whether a job finished this session or was reloaded from {@code
 * MissionHistoryStore} after a restart.
 */
public final class DashboardView {

    private static final int RECENT_RUNS_SHOWN = 5;
    private static final int COVERAGE_SAMPLE_SIZE = 12;

    private DashboardView() {
    }

    public static String render(List<MissionJob> jobs, Layout.WorkerPoolStatus workerPool) {

        StringBuilder body = new StringBuilder();
        body.append("<div class=\"dash-head\"><div><h1>Overview</h1>")
                .append("<p class=\"lede\" style=\"margin-bottom:0\">Autonomous exploratory runs across your applications</p></div></div>");

        List<MissionJob> done = jobs.stream()
                .filter(j -> j.state() == MissionJob.State.DONE)
                .toList();

        body.append("<div class=\"bento\">");
        body.append(streamTile(jobs));
        body.append(coverageCard(done));
        body.append(findingsCard(done));
        body.append(recentRunsCard(jobs));
        body.append("</div>");

        return Layout.page("AEGIS — Overview", "dashboard", workerPool, body.toString());
    }

    private static String streamTile(List<MissionJob> jobs) {

        return jobs.stream()
                .filter(j -> j.state() == MissionJob.State.RUNNING)
                .findFirst()
                .map(LiveMissionStreamView::render)
                .orElseGet(() -> "<div class=\"bento-card stream-card\">"
                        + "<div class=\"empty-tile\"><p>No mission is currently running.</p>"
                        + "<a class=\"btn-primary\" href=\"/run\" style=\"margin-top:0\">Start a mission</a></div></div>");
    }

    private static String coverageCard(List<MissionJob> done) {

        List<Double> percents = done.stream()
                .limit(COVERAGE_SAMPLE_SIZE)
                .map(j -> j.summary().coveragePercent())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        // done is most-recent-first; render oldest-to-newest left-to-right, matching a normal trend reading direction.
        java.util.Collections.reverse(percents);

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"bento-card\"><div class=\"bento-title\">Coverage &middot; last ")
                .append(percents.size()).append(percents.size() == 1 ? " run" : " runs").append("</div>");

        if (percents.isEmpty()) {
            html.append("<div class=\"bento-stat-row\"><span class=\"bento-stat\">&mdash;</span></div>")
                    .append("<p class=\"help\" style=\"margin-top:14px\">No finished runs yet.</p>");
        } else {
            double latest = percents.get(percents.size() - 1);
            html.append("<div class=\"bento-stat-row\"><span class=\"bento-stat\">")
                    .append(String.format(Locale.ROOT, "%.1f%%", latest)).append("</span>");
            if (percents.size() > 1) {
                double delta = latest - percents.get(percents.size() - 2);
                html.append("<span class=\"bento-delta\">").append(delta >= 0 ? "+" : "")
                        .append(String.format(Locale.ROOT, "%.1f", delta)).append("</span>");
            }
            html.append("</div>");

            double max = percents.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
            html.append("<div class=\"sparkline\">");
            int recentThreshold = Math.max(0, percents.size() - 3);
            for (int i = 0; i < percents.size(); i++) {
                double heightPercent = max <= 0 ? 5 : Math.max(5, (percents.get(i) / max) * 100);
                html.append("<div class=\"spark-bar").append(i >= recentThreshold ? " recent" : "")
                        .append("\" style=\"height:").append(String.format(Locale.ROOT, "%.0f", heightPercent)).append("%\"></div>");
            }
            html.append("</div>");
        }

        html.append("</div>");
        return html.toString();
    }

    private static String findingsCard(List<MissionJob> done) {

        Map<FindingCategory, Integer> counts = new TreeMap<>();
        int total = 0;

        for (MissionJob job : done) {
            for (var entry : job.summary().findingsByCategoryCounts().entrySet()) {
                counts.merge(entry.getKey(), entry.getValue(), Integer::sum);
                total += entry.getValue();
            }
        }

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"bento-card\"><div class=\"bento-title\">Open findings</div>");
        html.append("<div class=\"bento-stat-row\"><span class=\"bento-stat alert\">").append(total).append("</span>");
        html.append("<span class=\"bento-sub\">across ").append(done.size()).append(done.size() == 1 ? " run" : " runs").append("</span></div>");

        if (!counts.isEmpty()) {
            html.append("<div class=\"findings-rows\">");
            counts.entrySet().stream()
                    .sorted(Map.Entry.<FindingCategory, Integer>comparingByValue().reversed())
                    .forEach(e -> html.append("<div class=\"findings-row\"><span>")
                            .append(e.getKey().name().toLowerCase(Locale.ROOT)).append("</span><span>")
                            .append(e.getValue()).append("</span></div>"));
            html.append("</div>");
        }

        html.append("</div>");
        return html.toString();
    }

    private static String recentRunsCard(List<MissionJob> jobs) {

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"bento-card span2\"><div class=\"bento-card-head\">")
                .append("<div class=\"bento-card-title\">Recent runs</div>")
                .append("<a class=\"view-all\" href=\"/runs\">View all</a></div>");

        if (jobs.isEmpty()) {
            html.append("<p class=\"help\" style=\"margin-top:10px\">No missions yet &mdash; <a href=\"/run\">start one</a>.</p>");
        } else {
            html.append("<div class=\"runs-rows\">");
            jobs.stream().limit(RECENT_RUNS_SHOWN).forEach(job -> html.append(runRow(job)));
            html.append("</div>");
        }

        html.append("</div>");
        return html.toString();
    }

    static String runRow(MissionJob job) {

        String badgeClass;
        String badgeLabel;
        String coverageLabel;
        String ring;
        int actions;

        switch (job.state()) {
            case RUNNING -> {
                badgeClass = "running";
                badgeLabel = "RUNNING";
                coverageLabel = "&mdash;";
                ring = "<div class=\"coverage-ring pulse\" style=\"background:rgba(110,231,192,0.35)\"></div>";
                actions = job.iteration();
            }
            case ERROR -> {
                badgeClass = "error";
                badgeLabel = "ERROR";
                coverageLabel = "&mdash;";
                ring = "<div class=\"coverage-ring\" style=\"background:rgba(255,107,87,0.35)\"></div>";
                actions = job.iteration();
            }
            default -> {
                badgeClass = job.summary().status().name().toLowerCase(Locale.ROOT);
                badgeLabel = job.summary().status().name();
                double coverage = job.summary().coveragePercent();
                coverageLabel = String.format(Locale.ROOT, "%.0f%%", coverage);
                String pct = String.format(Locale.ROOT, "%.0f", coverage);
                ring = "<div class=\"coverage-ring\" style=\"background:conic-gradient(var(--accent) 0% "
                        + pct + "%, rgba(255,255,255,0.12) " + pct + "% 100%)\"></div>";
                actions = job.summary().actionsExecuted();
            }
        }

        StringBuilder row = new StringBuilder();
        row.append("<a class=\"run-row2\" href=\"/missions/").append(job.id()).append("\">");
        row.append(ring);
        row.append("<div style=\"min-width:0\"><div class=\"run-row2-name\">").append(escapeHtml(job.mission().name())).append("</div>");
        row.append("<div class=\"run-row2-target\">").append(escapeHtml(job.mission().parameter("baseUrl"))).append("</div></div>");
        row.append("<span class=\"badge ").append(badgeClass).append("\"><span class=\"dot\"></span>").append(badgeLabel).append("</span>");
        row.append("<div class=\"run-row2-meta\">").append(coverageLabel).append(" &middot; ").append(actions).append("a</div>");
        row.append("<div class=\"run-row2-ago\">").append(formatAgo(job.submittedAt())).append("</div>");
        row.append("</a>");

        return row.toString();
    }

    static String formatAgo(Instant instant) {

        long seconds = Duration.between(instant, Instant.now()).getSeconds();

        if (seconds < 60) {
            return "just now";
        }

        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m ago";
        }

        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h ago";
        }

        return (hours / 24) + "d ago";
    }
}
