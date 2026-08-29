package com.aegis.web.view;

import com.aegis.web.mission.CoverageMapData;

import java.util.Locale;

import static com.aegis.web.view.Layout.escapeHtml;

/**
 * Renders {@code GET /coverage-map} — every page AEGIS has ever touched,
 * grouped by which app (mission {@code baseUrl}) it belongs to, by
 * element coverage. Not a site graph — there's no sitemap AEGIS builds
 * one from, just per-page coverage already computed per finished mission
 * (see {@link CoverageMapData}).
 */
public final class CoverageMapView {

    private CoverageMapView() {
    }

    public static String render(CoverageMapData data) {

        StringBuilder body = new StringBuilder();
        body.append("<div class=\"dash-head\"><div><h1>Coverage map</h1>")
                .append("<p class=\"lede\">Every distinct page AEGIS has ever touched, grouped by which app it belongs to, ")
                .append("by how much of what it found on each page actually got exercised.</p></div></div>");

        body.append("<div class=\"legend\">")
                .append("<span class=\"legend-swatch\"><span class=\"legend-dot low\"></span>&lt; 40%</span>")
                .append("<span class=\"legend-swatch\"><span class=\"legend-dot mid\"></span>40&ndash;75%</span>")
                .append("<span class=\"legend-swatch\"><span class=\"legend-dot high\"></span>&gt; 75%</span>")
                .append("</div>");

        if (data.sites().isEmpty()) {
            body.append("<div class=\"card\"><p class=\"help\">No finished missions yet &mdash; <a href=\"/run\">start one</a>.</p></div>");
        } else {
            for (CoverageMapData.SiteGroup site : data.sites()) {
                body.append(siteGroupHtml(site));
            }
        }

        return Layout.page("AEGIS — Coverage Map", "coverage-map", body.toString());
    }

    private static String siteGroupHtml(CoverageMapData.SiteGroup site) {

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"site-group\"><div class=\"site-head\">");
        html.append("<div class=\"site-name\">").append(escapeHtml(site.baseUrl())).append("</div>");
        html.append("<div class=\"site-meta\">").append(site.pages().size()).append(site.pages().size() == 1 ? " page" : " pages")
                .append(" &middot; ").append(site.missionCount()).append(site.missionCount() == 1 ? " mission" : " missions").append("</div>");
        html.append("<div class=\"site-avg\"><span class=\"site-avg-value ").append(band(site.averageCoveragePercent())).append("\">")
                .append(String.format(Locale.ROOT, "%.0f%%", site.averageCoveragePercent())).append("</span>")
                .append("<span class=\"site-avg-label\">avg coverage</span></div>");
        html.append("</div>");

        html.append("<div class=\"grid\">");
        for (CoverageMapData.PageSummary page : site.pages()) {
            html.append(tileHtml(page));
        }
        html.append("</div></div>");

        return html.toString();
    }

    private static String tileHtml(CoverageMapData.PageSummary page) {

        String band = band(page.coveragePercent());
        String pct = String.format(Locale.ROOT, "%.0f", page.coveragePercent());

        return "<div class=\"tile " + band + "\">"
                + "<div class=\"tile-path\">" + escapeHtml(page.url()) + "</div>"
                + "<div class=\"tile-stat-row\"><span class=\"tile-pct\">" + pct + "%</span>"
                + "<span class=\"tile-elements\">" + page.elementsInteracted() + " / " + page.elementsDiscovered() + " elements</span></div>"
                + "<div class=\"tile-track\"><div class=\"tile-fill\" style=\"width:" + pct + "%\"></div></div>"
                + "<div class=\"tile-meta\"><span>" + page.missionCount() + (page.missionCount() == 1 ? " mission" : " missions") + "</span>"
                + "<span>" + DashboardView.formatAgo(page.lastRunAt()) + "</span></div>"
                + "</div>";
    }

    private static String band(double coveragePercent) {
        if (coveragePercent < 40) {
            return "low";
        }
        return coveragePercent <= 75 ? "mid" : "high";
    }
}
