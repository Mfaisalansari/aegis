package com.aegis.core.report;

import com.aegis.model.finding.Finding;
import com.aegis.model.mission.MissionStatus;
import com.aegis.model.reasoning.CandidateAction;
import com.aegis.model.reasoning.NavigationEdge;
import com.aegis.model.reasoning.ReasoningStep;
import com.aegis.model.context.MissionContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a mission's explainability data as a single self-contained HTML
 * file — inline CSS/JS only, no external assets, so it works fully
 * offline. Same underlying data as ExplainabilityReportGenerator
 * (MissionReportData); this just renders it visually instead of as text.
 */
public class HtmlExplainabilityReportGenerator {

    public String generate(MissionContext context, MissionStatus status) {

        MissionReportData data = MissionReportData.from(context, status);

        StringBuilder html = new StringBuilder();

        html.append("<!doctype html><html><head><meta charset=\"utf-8\">");
        html.append("<title>AEGIS Report — ").append(escape(data.missionName())).append("</title>");
        html.append("<style>").append(css()).append("</style>");
        html.append("</head><body>");

        html.append(renderHeader(data));
        html.append(renderSummary(data));
        html.append(renderStats(data));
        html.append(renderGraph(data));
        html.append(renderFindings(data));
        html.append(renderSteps(data));

        html.append("<script>").append(js()).append("</script>");
        html.append("</body></html>");

        return html.toString();
    }

    private String renderHeader(MissionReportData data) {

        String statusClass = data.status() == MissionStatus.SUCCESS ? "success" : "failure";

        return "<header>"
                + "<h1>" + escape(data.missionName()) + "</h1>"
                + "<span class=\"badge " + statusClass + "\">" + data.status() + "</span>"
                + "</header>";
    }

    /**
     * The thing a reader wants before any raw data: what AEGIS was trying
     * to do and what actually happened, in one place.
     */
    private String renderSummary(MissionReportData data) {

        return "<section class=\"summary\">"
                + "<p class=\"goal\"><strong>Goal:</strong> " + escape(data.missionGoal()) + "</p>"
                + "<p class=\"outcome\">" + escape(data.outcomeSummary()) + "</p>"
                + "</section>";
    }

    private String renderStats(MissionReportData data) {

        long critical = data.findings().stream()
                .filter(f -> f.severity().name().equals("CRITICAL")).count();
        long high = data.findings().stream()
                .filter(f -> f.severity().name().equals("HIGH")).count();

        StringBuilder tiles = new StringBuilder("<section class=\"stats\">");
        tiles.append(tile("Pages Visited", String.valueOf(data.visitedPages().size())));
        tiles.append(tile("States Discovered", String.valueOf(data.states().size())));
        tiles.append(tile("Transitions", String.valueOf(data.edges().size())));
        tiles.append(tile("Reasoning Steps", String.valueOf(data.reasoningSteps().size())));
        tiles.append(tile("Findings", data.findings().size()
                + (critical + high > 0 ? " (" + (critical + high) + " critical/high)" : "")));
        tiles.append("</section>");

        return tiles.toString();
    }

    private String tile(String label, String value) {
        return "<div class=\"tile\"><div class=\"tile-value\">" + escape(value) + "</div>"
                + "<div class=\"tile-label\">" + escape(label) + "</div></div>";
    }

    private String renderGraph(MissionReportData data) {

        StringBuilder section = new StringBuilder("<section><h2>World Model</h2>");

        List<String> states = data.states();

        if (states.isEmpty()) {
            section.append("<p class=\"empty\">No states observed.</p></section>");
            return section.toString();
        }

        Map<String, double[]> positions = new LinkedHashMap<>();
        double cx = 340;
        double cy = 320;
        double radius = Math.max(140, 34.0 * states.size());

        for (int i = 0; i < states.size(); i++) {
            double angle = 2 * Math.PI * i / states.size() - Math.PI / 2;
            positions.put(states.get(i), new double[]{
                    cx + radius * Math.cos(angle),
                    cy + radius * Math.sin(angle)
            });
        }

        Map<String, List<NavigationEdge>> grouped = new LinkedHashMap<>();

        for (NavigationEdge edge : data.edges()) {
            grouped.computeIfAbsent(edge.fromState() + " " + edge.toState(), key -> new ArrayList<>())
                    .add(edge);
        }

        StringBuilder edgesSvg = new StringBuilder();

        // A quadratic/cubic Bezier curve always stays within the convex hull
        // of its own control points, so tracking every anchor/control/label
        // point actually used to draw the graph gives an exact viewBox.
        // The previous viewBox was a fixed guess (cy * 2 tall) that ignored
        // how far the node radius grows with the state count, so once there
        // were more than a handful of states the graph rendered far outside
        // its own viewBox and looked clipped/distorted.
        double[] bounds = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};

        for (double[] pos : positions.values()) {
            extend(bounds, pos[0] - 26, pos[1] - 26);
            extend(bounds, pos[0] + 26, pos[1] + 26);
        }

        for (List<NavigationEdge> group : grouped.values()) {
            for (int i = 0; i < group.size(); i++) {

                double[][] geometry = edgeGeometry(group.get(i), positions, i);

                edgesSvg.append(renderEdge(group.get(i), geometry));

                for (double[] point : geometry) {
                    extend(bounds, point[0], point[1]);
                }
            }
        }

        double pad = 30;
        double minX = bounds[0] - pad;
        double minY = bounds[1] - pad;
        double viewWidth = bounds[2] - bounds[0] + 2 * pad;
        double viewHeight = bounds[3] - bounds[1] + 2 * pad;

        section.append("<svg class=\"graph\" viewBox=\"")
                .append((int) minX).append(' ').append((int) minY).append(' ')
                .append((int) viewWidth).append(' ').append((int) viewHeight)
                .append("\">");

        section.append("<defs><marker id=\"arrow\" viewBox=\"0 0 10 10\" refX=\"9\" refY=\"5\" "
                + "markerWidth=\"6\" markerHeight=\"6\" orient=\"auto-start-reverse\">"
                + "<path d=\"M0,0 L10,5 L0,10 z\" class=\"arrowhead\"/></marker></defs>");

        section.append(edgesSvg);

        int index = 1;

        for (String state : states) {
            section.append(renderNode(state, positions.get(state), index++));
        }

        section.append("</svg></section>");

        return section.toString();
    }

    private void extend(double[] bounds, double x, double y) {
        bounds[0] = Math.min(bounds[0], x);
        bounds[1] = Math.min(bounds[1], y);
        bounds[2] = Math.max(bounds[2], x);
        bounds[3] = Math.max(bounds[3], y);
    }

    /**
     * Returns the points defining an edge's curve — [start, control(s)...,
     * labelAnchor] — used both to draw the path (renderEdge) and to bound
     * the SVG viewBox (renderGraph), so the two can never drift out of sync.
     */
    private double[][] edgeGeometry(NavigationEdge edge, Map<String, double[]> positions, int parallelIndex) {

        double[] from = positions.get(edge.fromState());
        double[] to = positions.get(edge.toState());

        if (edge.fromState().equals(edge.toState())) {

            double loopOffset = 40 + parallelIndex * 18;

            double[] start = {from[0], from[1] - 26};
            double[] ctrl1 = {from[0] - loopOffset, from[1] - loopOffset};
            double[] ctrl2 = {from[0] + loopOffset, from[1] - loopOffset};
            double[] label = {from[0], from[1] - 26 - loopOffset};

            return new double[][]{start, ctrl1, ctrl2, label};
        }

        double curve = parallelIndex * 22;
        double midX = (from[0] + to[0]) / 2 - (to[1] - from[1]) * 0.001 * curve * 10;
        double midY = (from[1] + to[1]) / 2 + (to[0] - from[0]) * 0.001 * curve * 10;

        return new double[][]{{from[0], from[1]}, {midX, midY}, {to[0], to[1]}};
    }

    private String renderNode(String state, double[] pos, int index) {

        String shortLabel = shortStateLabel(state, index);

        return String.format(
                "<g class=\"node\" data-state=\"%s\">"
                        + "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"26\"/>"
                        + "<text x=\"%.1f\" y=\"%.1f\">S%d</text>"
                        + "<title>%s</title>"
                        + "</g>",
                escapeAttr(state), pos[0], pos[1], pos[0], pos[1] + 5, index, escape(state)
        );
    }

    private String renderEdge(NavigationEdge edge, double[][] geometry) {

        String label = escape(edge.actionType() + " " + shortenTarget(edge.actionTarget()));

        if (edge.fromState().equals(edge.toState())) {

            double[] start = geometry[0];
            double[] ctrl1 = geometry[1];
            double[] ctrl2 = geometry[2];
            double[] labelPos = geometry[3];

            String path = String.format(
                    "M %.1f %.1f C %.1f %.1f, %.1f %.1f, %.1f %.1f",
                    start[0], start[1], ctrl1[0], ctrl1[1], ctrl2[0], ctrl2[1], start[0], start[1]
            );

            return "<g class=\"edge\" data-from=\"" + escapeAttr(edge.fromState())
                    + "\" data-to=\"" + escapeAttr(edge.toState()) + "\">"
                    + "<path d=\"" + path + "\" class=\"edge-line self-loop\" marker-end=\"url(#arrow)\"/>"
                    + "<text x=\"" + labelPos[0] + "\" y=\"" + labelPos[1] + "\">" + label + "</text>"
                    + "</g>";
        }

        double[] from = geometry[0];
        double[] mid = geometry[1];
        double[] to = geometry[2];

        String path = String.format(
                "M %.1f %.1f Q %.1f %.1f, %.1f %.1f",
                from[0], from[1], mid[0], mid[1], to[0], to[1]
        );

        return "<g class=\"edge\" data-from=\"" + escapeAttr(edge.fromState())
                + "\" data-to=\"" + escapeAttr(edge.toState()) + "\">"
                + "<path d=\"" + path + "\" class=\"edge-line\" marker-end=\"url(#arrow)\"/>"
                + "<text x=\"" + mid[0] + "\" y=\"" + mid[1] + "\">" + label + "</text>"
                + "</g>";
    }

    private String shortStateLabel(String state, int index) {
        return "S" + index;
    }

    private String shortenTarget(String target) {
        return target.length() > 24 ? target.substring(0, 21) + "..." : target;
    }

    private String renderFindings(MissionReportData data) {

        StringBuilder section = new StringBuilder("<section><h2>Findings (most severe first)</h2>");

        if (data.findings().isEmpty()) {
            section.append("<p class=\"empty\">No findings.</p></section>");
            return section.toString();
        }

        for (Finding finding : data.rankedFindings()) {

            section.append("<div class=\"finding ").append(finding.severity().name().toLowerCase()).append("\">")
                    .append("<span class=\"severity\">").append(finding.severity()).append("</span> ")
                    .append("<span class=\"summary\">").append(escape(finding.summary())).append("</span>")
                    .append("<div class=\"meaning\">").append(escape(data.explain(finding))).append("</div>")
                    .append("<div class=\"meta\">").append(escape(finding.url()))
                    .append(" — ").append(finding.detectedAt()).append("</div>")
                    .append("</div>");
        }

        section.append("</section>");

        return section.toString();
    }

    private String renderSteps(MissionReportData data) {

        StringBuilder section = new StringBuilder("<section><h2>Reasoning Steps</h2>");

        if (data.reasoningSteps().isEmpty()) {
            section.append("<p class=\"empty\">No reasoning steps recorded.</p></section>");
            return section.toString();
        }

        for (ReasoningStep step : data.reasoningSteps()) {

            CandidateAction selected = step.selected();
            double margin = data.runnerUpMargin(step);
            String marginText = Double.isNaN(margin)
                    ? "only candidate"
                    : String.format("+%.2f over %d alternative%s", margin, step.candidates().size() - 1,
                            step.candidates().size() - 1 == 1 ? "" : "s");

            section.append("<details class=\"step\">")
                    .append("<summary>Step ").append(step.step()).append(": ")
                    .append(escape(selected.action().type() + " " + selected.action().target()))
                    .append(" <span class=\"confidence\">confidence=")
                    .append(String.format("%.2f", selected.confidence()))
                    .append(" (").append(marginText).append(")</span></summary>");

            section.append("<div class=\"candidates\"><table><thead><tr>"
                    + "<th>Type</th><th>Target</th><th>Confidence</th><th>Reasoning</th></tr></thead><tbody>");

            for (CandidateAction candidate : step.candidates()) {

                boolean isSelected = candidate.action().id().equals(selected.action().id());

                section.append("<tr class=\"").append(isSelected ? "selected" : "").append("\">")
                        .append("<td>").append(candidate.action().type()).append("</td>")
                        .append("<td>").append(escape(candidate.action().target())).append("</td>")
                        .append("<td><div class=\"bar\"><div class=\"bar-fill\" style=\"width:")
                        .append((int) (candidate.confidence() * 100)).append("%\"></div></div> ")
                        .append(String.format("%.2f", candidate.confidence())).append("</td>")
                        .append("<td>").append(escape(candidate.reasoning())).append("</td>")
                        .append("</tr>");
            }

            section.append("</tbody></table></div></details>");
        }

        section.append("</section>");

        return section.toString();
    }

    private String css() {
        return """
                :root {
                    color-scheme: light dark;
                    --bg: #ffffff; --fg: #1a1a1a; --muted: #6b7280;
                    --card: #f7f7f8; --border: #e2e2e5; --accent: #2563eb;
                    --success: #16a34a; --failure: #dc2626;
                    --critical: #dc2626; --high: #ea580c; --medium: #ca8a04; --low: #64748b;
                }
                @media (prefers-color-scheme: dark) {
                    :root { --bg: #15161a; --fg: #e6e6e6; --muted: #9aa0aa;
                        --card: #1d1f26; --border: #2b2d35; }
                }
                * { box-sizing: border-box; }
                body { margin: 0; padding: 2rem; background: var(--bg); color: var(--fg);
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
                header { display: flex; align-items: center; gap: 1rem; margin-bottom: 1.5rem; }
                h1 { font-size: 1.4rem; margin: 0; }
                h2 { font-size: 1.05rem; margin: 0 0 .75rem; }
                .badge { padding: .25rem .75rem; border-radius: 999px; font-weight: 600; font-size: .85rem; color: #fff; }
                .badge.success { background: var(--success); }
                .badge.failure { background: var(--failure); }
                section { margin-bottom: 2rem; }
                .summary { background: var(--card); border: 1px solid var(--border); border-radius: .5rem;
                    padding: 1rem 1.25rem; }
                .summary .goal { margin: 0 0 .4rem; }
                .summary .outcome { margin: 0; color: var(--muted); }
                .stats { display: flex; flex-wrap: wrap; gap: .75rem; }
                .tile { background: var(--card); border: 1px solid var(--border); border-radius: .5rem;
                    padding: .75rem 1rem; min-width: 120px; }
                .tile-value { font-size: 1.3rem; font-weight: 700; }
                .tile-label { font-size: .8rem; color: var(--muted); }
                .empty { color: var(--muted); font-style: italic; }
                .graph { width: 100%; max-width: 900px; height: auto; overflow: visible; }
                .node circle { fill: var(--card); stroke: var(--accent); stroke-width: 2; }
                .node text { fill: var(--fg); font-size: 13px; text-anchor: middle; }
                .edge-line { fill: none; stroke: var(--muted); stroke-width: 1.5; }
                .edge text { fill: var(--muted); font-size: 10px; text-anchor: middle; }
                .node.dim, .edge.dim { opacity: .15; }
                .finding { border-left: 4px solid var(--low); background: var(--card);
                    border-radius: .25rem; padding: .6rem 1rem; margin-bottom: .5rem; }
                .finding.critical { border-color: var(--critical); }
                .finding.high { border-color: var(--high); }
                .finding.medium { border-color: var(--medium); }
                .finding.low { border-color: var(--low); }
                .severity { font-weight: 700; font-size: .8rem; }
                .meaning { font-size: .85rem; margin-top: .3rem; }
                .meta { color: var(--muted); font-size: .8rem; margin-top: .2rem; }
                .step { background: var(--card); border: 1px solid var(--border); border-radius: .5rem;
                    padding: .5rem .9rem; margin-bottom: .5rem; }
                .step summary { cursor: pointer; font-weight: 600; }
                .confidence { color: var(--muted); font-weight: 400; font-size: .85rem; }
                .candidates { overflow-x: auto; margin-top: .75rem; }
                table { border-collapse: collapse; width: 100%; font-size: .85rem; }
                th, td { text-align: left; padding: .4rem .6rem; border-bottom: 1px solid var(--border); }
                tr.selected { background: color-mix(in srgb, var(--accent) 12%, transparent); font-weight: 600; }
                .bar { display: inline-block; width: 60px; height: 6px; background: var(--border);
                    border-radius: 3px; overflow: hidden; vertical-align: middle; margin-right: .35rem; }
                .bar-fill { height: 100%; background: var(--accent); }
                """;
    }

    private String js() {
        return """
                document.querySelectorAll('.node').forEach(function (node) {
                    node.addEventListener('mouseenter', function () {
                        var state = node.getAttribute('data-state');
                        document.querySelectorAll('.node, .edge').forEach(function (el) {
                            var related = el === node
                                || el.getAttribute('data-from') === state
                                || el.getAttribute('data-to') === state
                                || el.getAttribute('data-state') === state;
                            el.classList.toggle('dim', !related);
                        });
                    });
                    node.addEventListener('mouseleave', function () {
                        document.querySelectorAll('.node, .edge').forEach(function (el) {
                            el.classList.remove('dim');
                        });
                    });
                });
                """;
    }

    private String escape(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String escapeAttr(String value) {
        return escape(value);
    }
}
