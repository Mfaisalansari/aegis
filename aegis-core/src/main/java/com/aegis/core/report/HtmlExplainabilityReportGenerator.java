package com.aegis.core.report;

import com.aegis.core.bug.BugCluster;
import com.aegis.core.bug.BugExplainer;
import com.aegis.core.bug.RecommendationEngine;
import com.aegis.core.bug.RuleBasedBugExplainer;
import com.aegis.core.bug.RuleBasedRecommendationEngine;
import com.aegis.core.mission.MissionPlan;
import com.aegis.core.mission.RuleBasedMissionPlanner;
import com.aegis.core.reasoning.learning.PatternStatistics;
import com.aegis.model.experience.Experience;
import com.aegis.model.finding.Finding;
import com.aegis.model.mission.MissionStatus;
import com.aegis.model.reasoning.CandidateAction;
import com.aegis.model.reasoning.NavigationEdge;
import com.aegis.model.reasoning.ReasoningStep;
import com.aegis.model.context.MissionContext;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders a mission's explainability data as a single self-contained HTML
 * file — inline CSS/JS only, no external assets, so it works fully
 * offline. Same underlying data as ExplainabilityReportGenerator
 * (MissionReportData); this just renders it visually instead of as text.
 */
public class HtmlExplainabilityReportGenerator {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    public String generate(MissionContext context, MissionStatus status) {
        return generate(context, status, new RuleBasedBugExplainer());
    }

    /** Same report, but with AI bug explanations (Phase 8) computed via the given BugExplainer. */
    public String generate(MissionContext context, MissionStatus status, BugExplainer explainer) {
        return generate(context, status, explainer, new RuleBasedRecommendationEngine());
    }

    /** Same report, with AI recommendations (Phase 8) also computed via the given RecommendationEngine. */
    public String generate(
            MissionContext context, MissionStatus status, BugExplainer explainer, RecommendationEngine recommender) {
        return generate(context, status, explainer, recommender,
                new RuleBasedMissionPlanner().plan(context.getMission()));
    }

    /** Same report, with an AI mission plan (Phase 8) generated before the mission ran. */
    public String generate(
            MissionContext context, MissionStatus status, BugExplainer explainer,
            RecommendationEngine recommender, MissionPlan plan) {
        return generate(context, status, explainer, recommender, plan, List.of());
    }

    /**
     * The full report, with this mission's own Experience list (Reporting
     * v2) also supplied — powers the Mission Timeline's accurate
     * Execution Successful/Failed events and the Learning summary. Pass
     * List.of() (what every shorter overload above does) if no
     * ExperienceRepository is available; both degrade gracefully rather
     * than failing.
     */
    public String generate(
            MissionContext context, MissionStatus status, BugExplainer explainer,
            RecommendationEngine recommender, MissionPlan plan, List<Experience> experiences) {

        MissionReportData data = MissionReportData.from(context, status, explainer, recommender, plan, experiences);

        StringBuilder html = new StringBuilder();

        html.append("<!doctype html><html><head><meta charset=\"utf-8\">");
        html.append("<title>AEGIS Report — ").append(escape(data.missionName())).append("</title>");
        html.append("<style>").append(css()).append("</style>");
        html.append("</head><body>");

        html.append(renderHeader(data));
        html.append(renderTableOfContents());
        html.append(renderSummary(data));
        html.append(renderRecommendation(data));
        html.append(renderStats(data));
        html.append(renderTimeline(data));
        html.append(renderPlan(data));
        html.append(renderGraph(data));
        html.append(renderPageCoverage(data));
        html.append(renderLearning(data));
        html.append(renderFindingsDashboard(data));
        html.append(renderBugClusters(data));
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
     * Reporting v2 Stage 3 "interactive HTML": the report has grown to a
     * dozen-plus sections — a sticky jump nav so a reader can go straight
     * to the one they want instead of scrolling past everything else.
     */
    private String renderTableOfContents() {

        return "<nav class=\"toc\">"
                + "<a href=\"#summary\">Summary</a>"
                + "<a href=\"#recommendation\">Recommendation</a>"
                + "<a href=\"#stats\">Statistics</a>"
                + "<a href=\"#timeline\">Timeline</a>"
                + "<a href=\"#plan\">Plan</a>"
                + "<a href=\"#world-model\">World Model</a>"
                + "<a href=\"#coverage\">Coverage</a>"
                + "<a href=\"#learning\">Learning</a>"
                + "<a href=\"#findings-dashboard\">Findings</a>"
                + "<a href=\"#bug-clusters\">Bug Clusters</a>"
                + "<a href=\"#reasoning-steps\">Reasoning</a>"
                + "</nav>";
    }

    /**
     * Reporting v2's Executive Summary — the thing a manager should be
     * able to stop at: mission, duration, result, coverage, findings,
     * bug clusters, a one-line learning teaser, and the recommendation,
     * all in one place, before any raw data.
     */
    private String renderSummary(MissionReportData data) {

        return "<section id=\"summary\" class=\"summary\">"
                + "<p class=\"goal\"><strong>Goal:</strong> " + escape(data.missionGoal()) + "</p>"
                + "<p class=\"outcome\">" + escape(data.outcomeSummary()) + "</p>"
                + "<dl class=\"exec-facts\">"
                + fact("Duration", formatDuration(data.duration()))
                + fact("Coverage", String.format("%.0f%%", data.coverage().coveragePercent()))
                + fact("Findings", String.valueOf(data.findings().size()))
                + fact("Bug Clusters", String.valueOf(data.bugClusters().size()))
                + fact("Learning", learningLine(data.learningSummary()))
                + "</dl>"
                + "</section>";
    }

    private String fact(String label, String value) {
        return "<div><dt>" + escape(label) + "</dt><dd>" + escape(value) + "</dd></div>";
    }

    /**
     * Reporting v2 Stage 2 "improved recommendations": the recommendation
     * itself (Phase 8) is unchanged — this is presentation only, a
     * standalone callout instead of a line buried inside the summary
     * paragraph, since it's the single "so what do I do" line a reader
     * most wants after the Executive Summary.
     */
    private String renderRecommendation(MissionReportData data) {

        return "<section id=\"recommendation\" class=\"recommendation-callout\">"
                + "<div class=\"recommendation-label\">Recommendation</div>"
                + "<div class=\"recommendation-text\">" + escape(data.recommendation()) + "</div>"
                + "</section>";
    }

    private String learningLine(LearningSummary summary) {

        if (summary.newExperiences() == 0 && summary.updatedActions() == 0) {
            return "no experience recorded this mission";
        }

        return summary.newExperiences() + " new, " + summary.updatedActions() + " updated — "
                + summary.confidenceIncreased() + " improved, " + summary.confidenceReduced() + " declined";
    }

    private String formatDuration(Duration duration) {

        long totalSeconds = duration.toSeconds();

        if (totalSeconds < 60) {
            return String.format("%.1fs", duration.toMillis() / 1000.0);
        }

        return (totalSeconds / 60) + "m " + (totalSeconds % 60) + "s";
    }

    /**
     * Reporting v2's Mission Timeline — "the heart of Reporting": a
     * vertical, chronological replay of the whole run, built from
     * MissionReportData.timeline() (reconstructed purely from
     * already-recorded data, see MissionReportData.buildTimeline). Each
     * event kind gets its own color so the shape of a run — observe,
     * reason, execute, find — is visible at a glance before reading a
     * single line of detail.
     */
    private String renderTimeline(MissionReportData data) {

        StringBuilder section = new StringBuilder("<section id=\"timeline\"><h2>Mission Timeline</h2>"
                + "<p class=\"caption\">A chronological replay of the whole run, reconstructed from what was "
                + "actually recorded — nothing here was captured separately from the data elsewhere in this "
                + "report.</p>");

        section.append("<div class=\"timeline-filter\">"
                + "<button data-kind=\"all\" class=\"active\">All</button>"
                + "<button data-kind=\"observation\">Observations</button>"
                + "<button data-kind=\"reasoning\">Reasoning</button>"
                + "<button data-kind=\"execution\">Executions</button>"
                + "<button data-kind=\"finding\">Findings</button>"
                + "</div>");

        section.append("<ol class=\"timeline\">");

        for (TimelineEvent event : data.timeline()) {

            String kindClass = event.kind().name().toLowerCase().replace('_', '-');

            section.append("<li class=\"timeline-event ").append(kindClass).append("\">")
                    .append("<span class=\"timeline-time\">").append(TIME_FORMAT.format(event.timestamp()))
                    .append("</span>")
                    .append("<span class=\"timeline-dot\"></span>")
                    .append("<div class=\"timeline-body\">")
                    .append("<div class=\"timeline-headline\">").append(escape(event.headline())).append("</div>");

            if (event.detail() != null && !event.detail().isBlank()) {
                section.append("<div class=\"timeline-detail\">").append(escape(event.detail())).append("</div>");
            }

            if (event.screenshotPath() != null && !event.screenshotPath().isBlank()) {
                section.append("<img class=\"timeline-screenshot\" src=\"")
                        .append(escapeAttr(event.screenshotPath())).append("\" alt=\"Screenshot\"/>");
            }

            section.append("</div></li>");
        }

        section.append("</ol></section>");

        return section.toString();
    }

    /**
     * Advisory only (Phase 8 "AI mission planning") — generated before the
     * mission ran, from the mission's own description/parameters, never
     * read by the live reasoning pipeline. Shown right after the summary
     * so a reader sees the intended approach before the detailed results.
     */
    private String renderPlan(MissionReportData data) {

        StringBuilder section = new StringBuilder("<section id=\"plan\"><h2>Mission Plan</h2>"
                + "<p class=\"caption\">Advisory only — generated before the run, never read by the live "
                + "decision-making pipeline.</p><ol class=\"plan\">");

        for (String step : data.plan().steps()) {
            section.append("<li>").append(escape(step)).append("</li>");
        }

        section.append("</ol></section>");

        return section.toString();
    }

    private String renderStats(MissionReportData data) {

        long critical = data.findings().stream()
                .filter(f -> f.severity().name().equals("CRITICAL")).count();
        long high = data.findings().stream()
                .filter(f -> f.severity().name().equals("HIGH")).count();

        StringBuilder tiles = new StringBuilder("<section id=\"stats\" class=\"stats\">");
        tiles.append(tile("Actions Executed", String.valueOf(data.actionsExecuted())));
        tiles.append(tile("Pages Visited", String.valueOf(data.visitedPages().size())));
        tiles.append(tile("States Discovered", String.valueOf(data.states().size())));
        tiles.append(tile("Transitions", String.valueOf(data.edges().size())));
        tiles.append(tile("Element Coverage",
                data.coverage().elementsInteracted() + "/" + data.coverage().elementsDiscovered()
                        + " (" + String.format("%.0f%%", data.coverage().coveragePercent()) + ")"));
        tiles.append(tile("Avg Confidence", String.format("%.2f", data.averageConfidence())));
        tiles.append(tile("Bug Count", data.findings().size()
                + (critical + high > 0 ? " (" + (critical + high) + " critical/high)" : "")));
        tiles.append(tile("Clusters", String.valueOf(data.bugClusters().size())));
        tiles.append(tile("Duration", formatDuration(data.duration())));
        tiles.append("</section>");

        return tiles.toString();
    }

    private String tile(String label, String value) {
        return "<div class=\"tile\"><div class=\"tile-value\">" + escape(value) + "</div>"
                + "<div class=\"tile-label\">" + escape(label) + "</div></div>";
    }

    private String renderGraph(MissionReportData data) {

        StringBuilder section = new StringBuilder("<section id=\"world-model\"><h2>World Model</h2>"
                + "<p class=\"caption\">Node size/fill and edge thickness reflect how many times "
                + "exploration passed through that state or transition (Phase 5 heat map).</p>");

        List<String> states = data.states();

        if (states.isEmpty()) {
            section.append("<p class=\"empty\">No states observed.</p></section>");
            return section.toString();
        }

        Map<String, double[]> positions = new LinkedHashMap<>();
        double cx = 340;
        double cy = 320;
        double layoutRadius = Math.max(140, 34.0 * states.size());

        for (int i = 0; i < states.size(); i++) {
            double angle = 2 * Math.PI * i / states.size() - Math.PI / 2;
            positions.put(states.get(i), new double[]{
                    cx + layoutRadius * Math.cos(angle),
                    cy + layoutRadius * Math.sin(angle)
            });
        }

        Map<String, Double> nodeRadii = new LinkedHashMap<>();

        for (String state : states) {
            nodeRadii.put(state, nodeRadius(data.stateVisitCounts().getOrDefault(state, 1L)));
        }

        // Heat by exact transition (type + target, not just from/to): two
        // different actions between the same pair of states get their own
        // curve; the SAME action repeated multiple times collapses into
        // one thicker line instead of several overlapping near-duplicate
        // arcs, which is what "heat" should look like rather than clutter.
        Map<NavigationEdge, Long> edgeFrequency = data.edges().stream()
                .collect(Collectors.groupingBy(edge -> edge, LinkedHashMap::new, Collectors.counting()));

        Map<String, List<NavigationEdge>> grouped = new LinkedHashMap<>();

        for (NavigationEdge edge : edgeFrequency.keySet()) {
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
        // its own viewBox and looked clipped/distorted. Per-node radius
        // (instead of a flat 26) now varies with visit count, so bounds
        // must be measured against each node's actual radius too.
        double[] bounds = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};

        for (Map.Entry<String, double[]> entry : positions.entrySet()) {
            double r = nodeRadii.get(entry.getKey());
            extend(bounds, entry.getValue()[0] - r, entry.getValue()[1] - r);
            extend(bounds, entry.getValue()[0] + r, entry.getValue()[1] + r);
        }

        for (List<NavigationEdge> group : grouped.values()) {
            for (int i = 0; i < group.size(); i++) {

                NavigationEdge edge = group.get(i);
                double[][] geometry = edgeGeometry(edge, positions, nodeRadii, i);

                edgesSvg.append(renderEdge(edge, geometry, edgeFrequency.get(edge)));

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
            long visits = data.stateVisitCounts().getOrDefault(state, 1L);
            section.append(renderNode(state, positions.get(state), nodeRadii.get(state), visits, index++));
        }

        section.append("</svg></section>");

        return section.toString();
    }

    private double nodeRadius(long visits) {
        return 26 + Math.min(16, 4.0 * Math.max(0, visits - 1));
    }

    private double nodeHeatOpacity(long visits) {
        return Math.min(0.55, 0.10 * Math.max(0, visits - 1));
    }

    private double edgeStrokeWidth(long frequency) {
        return 1.5 + Math.min(4.5, 1.2 * Math.max(0, frequency - 1));
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
    private double[][] edgeGeometry(
            NavigationEdge edge, Map<String, double[]> positions, Map<String, Double> radii, int parallelIndex) {

        double[] from = positions.get(edge.fromState());
        double[] to = positions.get(edge.toState());
        double fromRadius = radii.get(edge.fromState());

        if (edge.fromState().equals(edge.toState())) {

            double loopOffset = 40 + parallelIndex * 18;

            double[] start = {from[0], from[1] - fromRadius};
            double[] ctrl1 = {from[0] - loopOffset, from[1] - loopOffset};
            double[] ctrl2 = {from[0] + loopOffset, from[1] - loopOffset};
            double[] label = {from[0], from[1] - fromRadius - loopOffset};

            return new double[][]{start, ctrl1, ctrl2, label};
        }

        double curve = parallelIndex * 22;
        double midX = (from[0] + to[0]) / 2 - (to[1] - from[1]) * 0.001 * curve * 10;
        double midY = (from[1] + to[1]) / 2 + (to[0] - from[0]) * 0.001 * curve * 10;

        return new double[][]{{from[0], from[1]}, {midX, midY}, {to[0], to[1]}};
    }

    private String renderNode(String state, double[] pos, double radius, long visits, int index) {

        double heatOpacity = nodeHeatOpacity(visits);

        return String.format(
                "<g class=\"node\" data-state=\"%s\">"
                        + "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"%.1f\" "
                        + "style=\"fill: var(--accent); fill-opacity: %.2f\"/>"
                        + "<text x=\"%.1f\" y=\"%.1f\">S%d</text>"
                        + "<title>%s (visited %d time%s)</title>"
                        + "</g>",
                escapeAttr(state), pos[0], pos[1], radius, heatOpacity,
                pos[0], pos[1] + 5, index, escape(state), visits, visits == 1 ? "" : "s"
        );
    }

    private String renderEdge(NavigationEdge edge, double[][] geometry, long frequency) {

        String label = escape(edge.actionType() + " " + shortenTarget(edge.actionTarget()))
                + (frequency > 1 ? " (×" + frequency + ")" : "");

        double strokeWidth = edgeStrokeWidth(frequency);

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
                    + "<path d=\"" + path + "\" class=\"edge-line self-loop\" "
                    + "style=\"stroke-width: " + String.format("%.1f", strokeWidth) + "\" "
                    + "marker-end=\"url(#arrow)\"/>"
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
                + "<path d=\"" + path + "\" class=\"edge-line\" "
                + "style=\"stroke-width: " + String.format("%.1f", strokeWidth) + "\" "
                + "marker-end=\"url(#arrow)\"/>"
                + "<text x=\"" + mid[0] + "\" y=\"" + mid[1] + "\">" + label + "</text>"
                + "</g>";
    }

    private String renderPageCoverage(MissionReportData data) {

        StringBuilder section = new StringBuilder("<section id=\"coverage\"><h2>Coverage</h2>"
                + "<p class=\"caption\">Pages AEGIS discovered this run — there's no sitemap, so a page it never "
                + "found can't be listed as \"not visited\"; see the World Model graph above and its heat map for "
                + "how often each state was actually revisited.</p><ul class=\"page-checklist\">");

        for (String url : data.visitedPages()) {
            section.append("<li>✓ ").append(escape(url)).append("</li>");
        }

        section.append("</ul>");

        if (data.pageCoverage().isEmpty()) {
            section.append("<p class=\"empty\">No pages observed.</p></section>");
            return section.toString();
        }

        section.append("<table><thead><tr><th>Page</th><th>Elements Covered</th><th></th></tr></thead><tbody>");

        for (PageCoverage page : data.pageCoverage()) {

            section.append("<tr><td>").append(escape(page.url())).append("</td>")
                    .append("<td>").append(page.elementsInteracted()).append("/")
                    .append(page.elementsDiscovered()).append("</td>")
                    .append("<td><div class=\"bar\"><div class=\"bar-fill\" style=\"width:")
                    .append((int) page.coveragePercent()).append("%\"></div></div> ")
                    .append(String.format("%.0f%%", page.coveragePercent())).append("</td>")
                    .append("</tr>");
        }

        section.append("</tbody></table></section>");

        return section.toString();
    }

    /**
     * Learning (Reporting v2 Stage 2): the user should see AEGIS
     * improving, not just that Phase 2's LearningEngine exists. A
     * straight read of MissionReportData.learningSummary().
     */
    private String renderLearning(MissionReportData data) {

        LearningSummary summary = data.learningSummary();

        StringBuilder section = new StringBuilder("<section id=\"learning\"><h2>Learning</h2>");

        section.append("<div class=\"stats\">")
                .append(tile("New Experiences", String.valueOf(summary.newExperiences())))
                .append(tile("Updated Actions", String.valueOf(summary.updatedActions())))
                .append(tile("Confidence Increased", String.valueOf(summary.confidenceIncreased())))
                .append(tile("Confidence Reduced", String.valueOf(summary.confidenceReduced())))
                .append("</div>");

        if (summary.actionPerformance().isEmpty()) {
            section.append("<p class=\"empty\">No experience recorded this mission.</p></section>");
            return section.toString();
        }

        List<PatternStatistics> best = summary.actionPerformance()
                .subList(0, Math.min(3, summary.actionPerformance().size()));

        List<PatternStatistics> worstFirst = summary.actionPerformance().reversed();
        List<PatternStatistics> worst = worstFirst.subList(0, Math.min(3, worstFirst.size()));

        section.append("<div class=\"performance-columns\">");
        section.append("<div><h3>Best Performing Actions</h3>").append(performanceTable(best)).append("</div>");
        section.append("<div><h3>Worst Performing Actions</h3>").append(performanceTable(worst)).append("</div>");
        section.append("</div></section>");

        return section.toString();
    }

    private String performanceTable(List<PatternStatistics> stats) {

        StringBuilder table = new StringBuilder(
                "<table><thead><tr><th>Action</th><th>Success Rate</th><th>Runs</th></tr></thead><tbody>");

        for (PatternStatistics stat : stats) {

            table.append("<tr><td>").append(escape(stat.action().type() + " " + stat.action().target())).append("</td>")
                    .append("<td><div class=\"bar\"><div class=\"bar-fill\" style=\"width:")
                    .append((int) (stat.successRate() * 100)).append("%\"></div></div> ")
                    .append(String.format("%.0f%%", stat.successRate() * 100)).append("</td>")
                    .append("<td>").append(stat.successfulExecutions()).append("/").append(stat.totalExecutions())
                    .append("</td></tr>");
        }

        table.append("</tbody></table>");

        return table.toString();
    }

    /**
     * Findings Dashboard (Reporting v2 Stage 2): the same BugClusters
     * Phase 6 already computed, grouped one level further by what kind
     * of problem they are (see FindingCategory) instead of only by
     * fingerprint.
     */
    private String renderFindingsDashboard(MissionReportData data) {

        StringBuilder section = new StringBuilder("<section id=\"findings-dashboard\"><h2>Findings Dashboard</h2>"
                + "<p class=\"caption\">Bug clusters grouped by category, not just fingerprint.</p>");

        if (data.findingsByCategory().isEmpty()) {
            section.append("<p class=\"empty\">No findings to categorize.</p></section>");
            return section.toString();
        }

        for (Map.Entry<FindingCategory, List<BugCluster>> entry : data.findingsByCategory().entrySet()) {

            section.append("<h3>").append(escape(entry.getKey().toString()))
                    .append(" (").append(entry.getValue().size()).append(")</h3>");

            for (BugCluster cluster : entry.getValue()) {

                section.append("<div class=\"finding ").append(cluster.severity().name().toLowerCase()).append("\">")
                        .append("<span class=\"severity\">").append(cluster.severity()).append("</span> ")
                        .append("<span class=\"summary\">").append(escape(cluster.representativeSummary())).append("</span>")
                        .append("<div class=\"meta\">×").append(cluster.occurrenceCount()).append("</div>")
                        .append("</div>");
            }
        }

        section.append("</section>");

        return section.toString();
    }

    private String renderBugClusters(MissionReportData data) {

        StringBuilder section = new StringBuilder("<section id=\"bug-clusters\"><h2>Bug Clusters</h2>"
                + "<p class=\"caption\">Findings grouped by a normalized fingerprint (Phase 6) — "
                + "recurring or cross-page clusters are stronger signals than any single occurrence.</p>");

        if (data.bugClusters().isEmpty()) {
            section.append("<p class=\"empty\">No findings to cluster.</p></section>");
            return section.toString();
        }

        for (BugCluster cluster : data.bugClusters()) {

            section.append("<div class=\"finding ").append(cluster.severity().name().toLowerCase()).append("\">")
                    .append("<span class=\"severity\">").append(cluster.severity()).append("</span> ")
                    .append("<span class=\"summary\">").append(escape(cluster.representativeSummary())).append("</span>")
                    .append("<div class=\"meaning\">").append(escape(data.explanationFor(cluster))).append("</div>");

            section.append("<div class=\"meta\">×").append(cluster.occurrenceCount());

            if (cluster.spansMultiplePages()) {
                section.append(" — seen on ").append(cluster.urls().size())
                        .append(" different pages, may share a root cause");
            }

            section.append("</div></div>");
        }

        section.append("</section>");

        return section.toString();
    }

    private String shortenTarget(String target) {
        return target.length() > 24 ? target.substring(0, 21) + "..." : target;
    }

    private String renderFindings(MissionReportData data) {

        StringBuilder section = new StringBuilder("<section id=\"findings\"><h2>Findings (most severe first)</h2>");

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

        StringBuilder section = new StringBuilder("<section id=\"reasoning-steps\"><h2>Reasoning Steps</h2>");

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

            boolean stepWasLearned = selected.reasoning().contains("learning-adjusted");

            section.append("<details class=\"step\">")
                    .append("<summary>Step ").append(step.step()).append(": ")
                    .append(escape(selected.action().type() + " " + selected.action().target()))
                    .append(" <span class=\"confidence\">confidence=")
                    .append(String.format("%.2f", selected.confidence()))
                    .append(" (").append(marginText).append(")</span>")
                    .append(stepWasLearned ? " <span class=\"learned-badge\">learned</span>" : "")
                    .append("</summary>");

            section.append("<div class=\"candidates\"><table><thead><tr>"
                    + "<th>Type</th><th>Target</th><th>Confidence</th><th></th><th>Reasoning</th></tr></thead><tbody>");

            for (CandidateAction candidate : step.candidates()) {

                boolean isSelected = candidate.action().id().equals(selected.action().id());
                boolean candidateWasLearned = candidate.reasoning().contains("learning-adjusted");

                section.append("<tr class=\"").append(isSelected ? "selected" : "").append("\">")
                        .append("<td>").append(candidate.action().type()).append("</td>")
                        .append("<td>").append(escape(candidate.action().target())).append("</td>")
                        .append("<td><div class=\"bar\"><div class=\"bar-fill\" style=\"width:")
                        .append((int) (candidate.confidence() * 100)).append("%\"></div></div> ")
                        .append(String.format("%.2f", candidate.confidence())).append("</td>")
                        .append("<td>").append(candidateWasLearned ? "<span class=\"learned-badge\">learned</span>" : "")
                        .append(isSelected ? " <span class=\"winner-badge\">winner</span>" : "").append("</td>")
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
                header { display: flex; align-items: center; gap: 1rem; margin-bottom: 1rem; }
                .toc { position: sticky; top: 0; z-index: 10; display: flex; flex-wrap: wrap; gap: .25rem 1rem;
                    background: var(--bg); padding: .6rem 0 1rem; margin-bottom: 1rem;
                    border-bottom: 1px solid var(--border); font-size: .82rem; }
                .toc a { color: var(--muted); text-decoration: none; }
                .toc a:hover { color: var(--accent); text-decoration: underline; }
                h1 { font-size: 1.4rem; margin: 0; }
                h2 { font-size: 1.05rem; margin: 0 0 .75rem; }
                .badge { padding: .25rem .75rem; border-radius: 999px; font-weight: 600; font-size: .85rem; color: #fff; }
                .badge.success { background: var(--success); }
                .badge.failure { background: var(--failure); }
                section { margin-bottom: 2rem; }
                .summary { background: var(--card); border: 1px solid var(--border); border-radius: .5rem;
                    padding: 1rem 1.25rem; }
                .summary .goal { margin: 0 0 .4rem; }
                .summary .outcome { margin: 0 0 .4rem; color: var(--muted); }
                .recommendation-callout { background: color-mix(in srgb, var(--accent) 10%, var(--card));
                    border: 1px solid var(--accent); border-radius: .5rem; padding: .85rem 1.25rem; }
                .recommendation-label { font-size: .75rem; font-weight: 700; text-transform: uppercase;
                    color: var(--accent); letter-spacing: .04em; margin-bottom: .3rem; }
                .recommendation-text { font-size: .95rem; }
                .stats { display: flex; flex-wrap: wrap; gap: .75rem; }
                .tile { background: var(--card); border: 1px solid var(--border); border-radius: .5rem;
                    padding: .75rem 1rem; min-width: 120px; }
                .tile-value { font-size: 1.3rem; font-weight: 700; }
                .tile-label { font-size: .8rem; color: var(--muted); }
                .empty { color: var(--muted); font-style: italic; }
                .caption { color: var(--muted); font-size: .8rem; margin: -.5rem 0 .75rem; }
                .plan { padding-left: 1.25rem; }
                .plan li { margin-bottom: .3rem; }
                .page-checklist { list-style: none; padding: 0; margin: 0 0 1rem; font-size: .85rem; }
                .page-checklist li { padding: .2rem 0; color: var(--success); }
                .performance-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; margin-top: 1rem; }
                .performance-columns h3 { font-size: .9rem; margin: 0 0 .5rem; }
                @media (max-width: 640px) { .performance-columns { grid-template-columns: 1fr; } }
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
                .exec-facts { display: flex; flex-wrap: wrap; gap: 1.25rem; margin: .75rem 0 0;
                    padding-top: .75rem; border-top: 1px solid var(--border); }
                .exec-facts div { min-width: 90px; }
                .exec-facts dt { font-size: .75rem; color: var(--muted); margin: 0; }
                .exec-facts dd { font-size: 1.05rem; font-weight: 700; margin: .1rem 0 0; }
                .learned-badge, .winner-badge { display: inline-block; font-size: .7rem; font-weight: 700;
                    padding: .1rem .45rem; border-radius: 999px; text-transform: uppercase; }
                .learned-badge { background: color-mix(in srgb, var(--accent) 18%, transparent); color: var(--accent); }
                .winner-badge { background: color-mix(in srgb, var(--success) 18%, transparent); color: var(--success); }
                .timeline { list-style: none; margin: 0; padding: 0; position: relative; }
                .timeline::before { content: ""; position: absolute; left: 5.5rem; top: 0; bottom: 0;
                    width: 2px; background: var(--border); }
                .timeline-event { position: relative; display: flex; align-items: flex-start;
                    gap: .75rem; padding: .4rem 0; }
                .timeline-time { flex: 0 0 5rem; text-align: right; font-size: .78rem; color: var(--muted);
                    font-variant-numeric: tabular-nums; padding-top: .1rem; }
                .timeline-dot { flex: 0 0 auto; width: 10px; height: 10px; border-radius: 999px;
                    background: var(--muted); margin-top: .3rem; z-index: 1;
                    box-shadow: 0 0 0 3px var(--bg); }
                .timeline-event.mission-started .timeline-dot, .timeline-event.mission-finished .timeline-dot {
                    background: var(--accent); }
                .timeline-event.observation .timeline-dot { background: #0891b2; }
                .timeline-event.reasoning .timeline-dot { background: var(--muted); }
                .timeline-event.execution .timeline-dot { background: var(--success); }
                .timeline-event.finding .timeline-dot { background: var(--high); }
                .timeline-body { flex: 1 1 auto; }
                .timeline-headline { font-size: .88rem; font-weight: 600; }
                .timeline-event.finding .timeline-headline { color: var(--high); }
                .timeline-detail { font-size: .8rem; color: var(--muted); margin-top: .1rem; }
                .timeline-screenshot { max-width: 240px; border: 1px solid var(--border); border-radius: .35rem;
                    margin-top: .4rem; display: block; }
                .timeline-filter { display: flex; flex-wrap: wrap; gap: .4rem; margin-bottom: 1rem; }
                .timeline-filter button { font: inherit; font-size: .8rem; padding: .3rem .7rem;
                    border-radius: 999px; border: 1px solid var(--border); background: var(--card);
                    color: var(--fg); cursor: pointer; }
                .timeline-filter button.active { background: var(--accent); border-color: var(--accent); color: #fff; }
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
                document.querySelectorAll('.timeline-filter button').forEach(function (btn) {
                    btn.addEventListener('click', function () {
                        document.querySelectorAll('.timeline-filter button').forEach(function (b) {
                            b.classList.remove('active');
                        });
                        btn.classList.add('active');
                        var kind = btn.getAttribute('data-kind');
                        document.querySelectorAll('.timeline-event').forEach(function (li) {
                            li.style.display = (kind === 'all' || li.classList.contains(kind)) ? '' : 'none';
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
