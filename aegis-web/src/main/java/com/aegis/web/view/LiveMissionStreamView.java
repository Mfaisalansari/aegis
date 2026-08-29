package com.aegis.web.view;

import com.aegis.core.stream.MissionStreamEvent;
import com.aegis.web.mission.MissionJob;

import java.util.List;

import static com.aegis.web.view.Layout.escapeHtml;

/**
 * Renders a running (or just-finished) mission's live action stream —
 * shared by {@link DashboardView}'s Overview tile and {@link
 * MissionStatusView}'s full-page RUNNING branch, so the markup and
 * polling JS exist in exactly one place. Replaces the old {@code
 * <meta http-equiv="refresh">} full-page-reload mechanism entirely: this
 * polls {@code GET /missions/{id}/status} on an interval and patches the
 * DOM in place — see {@link com.aegis.web.handler.MissionsHandler}.
 *
 * {@code .stream-events} is laid out {@code flex-direction:column-reverse}
 * (see Layout's CSS) specifically so the polling JS can always just
 * append new event elements at the end of the DOM in the order they
 * arrive and have them appear newest-first on screen, with no need to
 * reorder or prepend.
 */
public final class LiveMissionStreamView {

    private LiveMissionStreamView() {
    }

    public static String render(MissionJob job) {

        boolean running = job.state() == MissionJob.State.RUNNING;
        List<MissionStreamEvent> events = job.events();

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"bento-card stream-card\" data-mission-id=\"").append(job.id()).append("\">");

        html.append("<div class=\"stream-head\">");
        html.append("<span class=\"live-pill\" data-role=\"live-pill\"><span class=\"dot\"></span><span data-role=\"state-label\">")
                .append(running ? "RUNNING" : escapeHtml(job.state().toString())).append("</span></span>");
        html.append("<span class=\"stream-meta\" data-role=\"stream-meta\">").append(streamMeta(job)).append("</span>");
        if (running) {
            html.append("<button class=\"stream-pause\" data-role=\"pause-btn\" type=\"button\">Pause live view</button>");
        }
        html.append("</div>");

        html.append("<div class=\"stream-title\">").append(escapeHtml(job.mission().name())).append("</div>");
        html.append("<div class=\"stream-target\">").append(target(job)).append("</div>");

        html.append("<div class=\"stream-events\" data-role=\"stream-events\">");
        if (events.isEmpty()) {
            html.append("<p class=\"stream-empty\" data-role=\"stream-empty\">Waiting for the first observation&hellip;</p>");
        } else {
            for (MissionStreamEvent event : events) {
                html.append(eventCardHtml(event));
            }
        }
        html.append("</div>");

        html.append("</div>");

        if (running) {
            html.append(pollingScript(job.id()));
        }

        return html.toString();
    }

    private static String streamMeta(MissionJob job) {
        return "iteration " + job.iteration() + " / " + job.maxIterations() + " &middot; " + job.elapsedSeconds() + "s";
    }

    /** Builds its own {@code &middot;} separator, so this must NOT be passed through {@link Layout#escapeHtml} again by the caller (that would double-escape the entity into literal text). */
    private static String target(MissionJob job) {

        String baseUrl = job.mission().parameter("baseUrl");
        String successUrlContains = job.mission().parameter("successUrlContains");

        String goal = (successUrlContains == null || successUrlContains.isBlank())
                ? "explore (no success condition set)"
                : "reach a URL containing &quot;" + escapeHtml(successUrlContains) + "&quot;";

        return escapeHtml(baseUrl == null ? "" : baseUrl) + " &middot; goal: " + goal;
    }

    private static String eventCardHtml(MissionStreamEvent event) {

        return "<div class=\"stream-event\">"
                + "<div class=\"stream-event-head\">"
                + "<span class=\"stream-event-kind " + event.kind() + "\">" + event.kind() + "</span>"
                + "<span class=\"stream-event-time\">" + timeOnly(event.timestamp().toString()) + "</span>"
                + "</div>"
                + "<div class=\"stream-event-headline\">" + escapeHtml(event.headline()) + "</div>"
                + "<div class=\"stream-event-detail\">" + escapeHtml(event.detail()) + "</div>"
                + "</div>";
    }

    /** {@code Instant.toString()} is a full ISO-8601 timestamp (e.g. 2026-08-28T13:47:41.915070Z) — just the HH:mm:ss reads better in a compact event card. */
    private static String timeOnly(String isoInstant) {
        int t = isoInstant.indexOf('T');
        int dot = isoInstant.indexOf('.', t);
        if (t < 0) {
            return isoInstant;
        }
        return isoInstant.substring(t + 1, dot > 0 ? dot : isoInstant.length() - 1);
    }

    /**
     * Plain {@code setInterval}/{@code fetch} polling, not SSE/WebSocket —
     * a whole mission caps out around {@code maxIterations() * 3} events,
     * far too little traffic to justify hand-rolling {@code
     * text/event-stream} framing on top of the JDK's plain {@code
     * HttpServer} (which has no native SSE support). Stops itself once
     * the mission leaves RUNNING; "Pause live view" only clears this
     * client-side interval — the real mission keeps running server-side
     * regardless, this never claims otherwise.
     */
    private static String pollingScript(String missionId) {
        return "<script>\n"
                + "(function () {\n"
                + "    var root = document.querySelector('[data-mission-id=\"" + missionId + "\"]');\n"
                + "    if (!root) { return; }\n"
                + "    var eventsEl = root.querySelector('[data-role=\"stream-events\"]');\n"
                + "    var metaEl = root.querySelector('[data-role=\"stream-meta\"]');\n"
                + "    var stateEl = root.querySelector('[data-role=\"state-label\"]');\n"
                + "    var pauseBtn = root.querySelector('[data-role=\"pause-btn\"]');\n"
                + "    var emptyEl = root.querySelector('[data-role=\"stream-empty\"]');\n"
                + "    var shown = eventsEl.querySelectorAll('.stream-event').length;\n"
                + "    var paused = false;\n"
                + "    var timer = null;\n"
                + "\n"
                + "    function escapeHtml(s) {\n"
                + "        return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');\n"
                + "    }\n"
                + "\n"
                + "    function timeOnly(iso) {\n"
                + "        var t = iso.indexOf('T');\n"
                + "        if (t < 0) { return iso; }\n"
                + "        var rest = iso.slice(t + 1);\n"
                + "        var dot = rest.indexOf('.');\n"
                + "        return dot > 0 ? rest.slice(0, dot) : rest.slice(0, -1);\n"
                + "    }\n"
                + "\n"
                + "    function appendEvent(e) {\n"
                + "        if (emptyEl) { emptyEl.remove(); emptyEl = null; }\n"
                + "        var el = document.createElement('div');\n"
                + "        el.className = 'stream-event';\n"
                + "        el.innerHTML = '<div class=\"stream-event-head\">'\n"
                + "            + '<span class=\"stream-event-kind ' + e.kind + '\">' + e.kind + '</span>'\n"
                + "            + '<span class=\"stream-event-time\">' + timeOnly(e.timestamp) + '</span>'\n"
                + "            + '</div>'\n"
                + "            + '<div class=\"stream-event-headline\">' + escapeHtml(e.headline) + '</div>'\n"
                + "            + '<div class=\"stream-event-detail\">' + escapeHtml(e.detail) + '</div>';\n"
                + "        eventsEl.appendChild(el);\n"
                + "    }\n"
                + "\n"
                + "    function poll() {\n"
                + "        fetch('/missions/" + missionId + "/status').then(function (r) { return r.json(); }).then(function (data) {\n"
                + "            if (metaEl) { metaEl.innerHTML = 'iteration ' + data.iteration + ' / ' + data.maxIterations + ' &middot; ' + data.elapsedSeconds + 's'; }\n"
                + "            for (var i = shown; i < data.events.length; i++) { appendEvent(data.events[i]); }\n"
                + "            shown = data.events.length;\n"
                + "            if (data.state !== 'RUNNING') {\n"
                + "                if (stateEl) { stateEl.textContent = data.state; }\n"
                + "                if (pauseBtn) { pauseBtn.remove(); }\n"
                + "                stop();\n"
                + "                if (window.location.pathname.indexOf('/missions/') === 0) { window.location.reload(); }\n"
                + "            }\n"
                + "        }).catch(function () { });\n"
                + "    }\n"
                + "\n"
                + "    function start() { timer = setInterval(poll, 1800); }\n"
                + "    function stop() { if (timer) { clearInterval(timer); timer = null; } }\n"
                + "\n"
                + "    if (pauseBtn) {\n"
                + "        pauseBtn.addEventListener('click', function () {\n"
                + "            paused = !paused;\n"
                + "            pauseBtn.textContent = paused ? 'Resume live view' : 'Pause live view';\n"
                + "            if (paused) { stop(); } else { poll(); start(); }\n"
                + "        });\n"
                + "    }\n"
                + "\n"
                + "    start();\n"
                + "})();\n"
                + "</script>\n";
    }
}
