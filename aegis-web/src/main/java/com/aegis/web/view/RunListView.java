package com.aegis.web.view;

import com.aegis.web.mission.DashboardStats;
import com.aegis.web.mission.MissionJob;

import java.util.List;

/**
 * Renders {@code GET /runs} — the full run history {@code DashboardView}'s
 * Overview used to be before it became a curated bento (see its "View
 * all" link). Reuses {@code DashboardView.runRow} so both pages render a
 * mission the same way, and {@link DashboardStats} for the same summary
 * tiles the old single-page dashboard used to show.
 */
public final class RunListView {

    private RunListView() {
    }

    public static String render(List<MissionJob> jobs, DashboardStats stats) {

        StringBuilder body = new StringBuilder();
        body.append("<div class=\"dash-head\"><div><h1>Runs</h1>")
                .append("<p class=\"lede\" style=\"margin-bottom:0\">Every mission ever submitted</p></div>")
                .append("<a class=\"btn-primary\" href=\"/run\" style=\"margin-top:0\">+ New Run</a></div>");

        body.append("<div class=\"dashboard-stats\">");
        body.append(statTile("Total Runs", String.valueOf(stats.total())));
        body.append(statTile("Pass Rate", stats.passRateLabel()));
        body.append(statTile("Avg. Duration", stats.avgDurationSeconds() + "s"));
        body.append(statTile("Active", String.valueOf(stats.active())));
        body.append("</div>");

        if (jobs.isEmpty()) {
            body.append("<div class=\"card\"><p class=\"help\">No missions yet &mdash; <a href=\"/run\">start one</a>.</p></div>");
        } else {
            body.append("<div class=\"runs-rows\">");
            for (MissionJob job : jobs) {
                body.append(DashboardView.runRow(job));
            }
            body.append("</div>");
        }

        return Layout.page("AEGIS — Runs", "runs", body.toString());
    }

    private static String statTile(String label, String value) {
        return "<div class=\"stat-tile\"><div class=\"stat-label\">" + Layout.escapeHtml(label) + "</div>"
                + "<div class=\"stat-value\">" + Layout.escapeHtml(value) + "</div></div>";
    }
}
