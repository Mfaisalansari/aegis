package com.aegis.web.view;

import com.aegis.core.AegisReport;
import com.aegis.web.mission.MissionJob;

import java.time.Duration;
import java.util.Locale;

import static com.aegis.web.view.Layout.escapeHtml;

/** Renders {@code GET /missions/{id}} — auto-refreshing while RUNNING, a timeline-style summary once DONE/ERROR. */
public final class MissionStatusView {

    private MissionStatusView() {
    }

    public static String render(MissionJob job) {

        StringBuilder body = new StringBuilder();

        body.append("<a class=\"crumb-back\" href=\"/\">&larr; New mission</a>");

        body.append("<div class=\"detail-head\"><div>");
        body.append("<h1>").append(escapeHtml(job.mission().name())).append("</h1>");
        body.append("<div class=\"detail-sub\"><code>").append(escapeHtml(job.mission().parameter("baseUrl"))).append("</code>")
                .append("<span class=\"dot\"></span>Submitted ").append(job.submittedAt()).append("</div>");
        body.append("</div>");

        String head;
        switch (job.state()) {
            case RUNNING -> {
                body.append("<span class=\"badge running\"><span class=\"dot\"></span>RUNNING</span></div>");
                body.append("<p class=\"help\">This page refreshes automatically every 3 seconds.</p>");
                head = "<meta http-equiv=\"refresh\" content=\"3\">";
            }
            case ERROR -> {
                body.append("<span class=\"badge error\"><span class=\"dot\"></span>ERROR</span></div>");
                body.append("<div class=\"card\"><p>The mission failed to run:</p><p class=\"mono\" style=\"color:var(--err)\">")
                        .append(escapeHtml(job.errorMessage())).append("</p></div>");
                head = "";
            }
            default -> {
                AegisReport report = job.report();
                String statusClass = report.status().name().toLowerCase(Locale.ROOT);
                Duration elapsed = Duration.between(job.submittedAt(), job.finishedAt());

                body.append("<span class=\"badge ").append(statusClass).append("\"><span class=\"dot\"></span>")
                        .append(report.status()).append("</span></div>");

                body.append("<div class=\"detail-grid\"><div class=\"timeline\">");
                body.append("<h2>Plan</h2>");

                if (report.plan().steps().isEmpty()) {
                    body.append("<p class=\"help\">No plan preview was generated for this run.</p>");
                } else {
                    int n = 1;
                    int total = report.plan().steps().size();
                    for (String step : report.plan().steps()) {
                        boolean last = n == total;
                        body.append("<div class=\"tl-item\"><div class=\"tl-marker\"><div class=\"tl-num\">").append(n).append("</div>");
                        if (!last) {
                            body.append("<div class=\"tl-line\"></div>");
                        }
                        body.append("</div><div class=\"tl-card\">").append(escapeHtml(step)).append("</div></div>");
                        n++;
                    }
                }

                body.append("</div>");

                body.append("<div class=\"side-col\"><div class=\"card\"><h3>Run Details</h3>");
                body.append("<div class=\"stat-row\"><span class=\"k\">Status</span><span class=\"v\">").append(report.status()).append("</span></div>");
                body.append("<div class=\"stat-row\"><span class=\"k\">Duration</span><span class=\"v\">").append(elapsed.toSeconds()).append("s</span></div>");
                body.append("<div class=\"stat-row\"><span class=\"k\">Engine</span><span class=\"v\">").append(escapeHtml(job.browserType())).append("</span></div>");
                body.append("</div>");

                body.append("<div class=\"card\"><h3>Report</h3>");
                // body.append("<a class=\"btn-secondary\" href=\"/missions/").append(job.id()).append("/report\">View HTML</a>");
                body.append("<a class=\"btn-secondary\" href=\"/missions/").append(job.id()).append("/report/redesigned\">View HTML</a>");
                // body.append("<a class=\"btn-secondary\" href=\"/missions/").append(job.id()).append("/report?download=1\">Download HTML</a>");
                body.append("<a class=\"btn-secondary\" href=\"/missions/").append(job.id()).append("/report/redesigned?download=1\">Download HTML</a>");
                body.append("<a class=\"btn-secondary\" href=\"/missions/").append(job.id()).append("/report.json?download=1\">Download JSON</a>");
                body.append("<a class=\"btn-secondary\" href=\"/missions/").append(job.id()).append("/report.txt?download=1\">Download Text</a>");
                body.append("</div></div>");

                body.append("</div>");

                head = "";
            }
        }

        String html = Layout.page("AEGIS — Mission " + job.id(), body.toString());
        return head.isEmpty() ? html : html.replace("<head>", "<head>" + head);
    }
}
