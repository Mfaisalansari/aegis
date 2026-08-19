package com.aegis.web.view;

import com.aegis.web.mission.DashboardStats;
import com.aegis.web.mission.MissionJob;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static com.aegis.web.view.Layout.escapeHtml;

/** Renders {@code GET /} — every submitted mission plus summary stat tiles. */
public final class DashboardView {

    private DashboardView() {
    }

    public static String render(List<MissionJob> jobs, DashboardStats stats) {

        StringBuilder body = new StringBuilder();

        body.append("<div class=\"dash-head\"><div><h1>Test Runs</h1>")
                .append("<p class=\"lede\" style=\"margin-bottom:0\">Autonomous exploratory runs across your applications</p></div>")
                .append("<a class=\"btn-primary\" href=\"/run\">+ New Run</a></div>");

        body.append("<div class=\"dashboard-stats\">");
        body.append(statTile("Total Runs", String.valueOf(stats.total())));
        body.append(statTile("Pass Rate", stats.passRateLabel()));
        body.append(statTile("Avg. Duration", stats.avgDurationSeconds() + "s"));
        body.append(statTile("Active", String.valueOf(stats.active())));
        body.append("</div>");

        if (jobs.isEmpty()) {
            body.append("<div class=\"card\"><p class=\"help\">No missions yet &mdash; <a href=\"/run\">start one</a>.</p></div>");
        } else {
            body.append("<div class=\"run-list\">");
            for (MissionJob job : jobs) {
                body.append(renderRow(job));
            }
            body.append("</div>");
        }

        return Layout.page("AEGIS — Dashboard", "dashboard", body.toString());
    }

    private static String statTile(String label, String value) {
        return "<div class=\"stat-tile\"><div class=\"stat-label\">" + escapeHtml(label) + "</div>"
                + "<div class=\"stat-value\">" + escapeHtml(value) + "</div></div>";
    }

    private static String renderRow(MissionJob job) {

        String badgeClass;
        String badgeLabel;
        String duration;

        switch (job.state()) {
            case RUNNING -> {
                badgeClass = "running";
                badgeLabel = "RUNNING";
                duration = Duration.between(job.submittedAt(), Instant.now()).toSeconds() + "s";
            }
            case ERROR -> {
                badgeClass = "error";
                badgeLabel = "ERROR";
                duration = Duration.between(job.submittedAt(), job.finishedAt()).toSeconds() + "s";
            }
            default -> {
                badgeClass = job.report().status().name().toLowerCase(Locale.ROOT);
                badgeLabel = job.report().status().name();
                duration = Duration.between(job.submittedAt(), job.finishedAt()).toSeconds() + "s";
            }
        }

        StringBuilder row = new StringBuilder();
        row.append("<a class=\"run-row\" href=\"/missions/").append(job.id()).append("\">");
        row.append("<div class=\"run-main\"><div class=\"run-name\">").append(escapeHtml(job.mission().name())).append("</div>");
        row.append("<div class=\"run-target mono\">").append(escapeHtml(job.mission().parameter("baseUrl"))).append("</div></div>");
        row.append("<span class=\"badge ").append(badgeClass).append("\"><span class=\"dot\"></span>").append(badgeLabel).append("</span>");
        row.append("<div class=\"run-duration mono\">").append(duration).append("</div>");
        row.append("<div class=\"run-ago\">").append(formatAgo(job.submittedAt())).append("</div>");
        row.append("</a>");

        return row.toString();
    }

    private static String formatAgo(Instant instant) {

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
