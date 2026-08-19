package com.aegis.core.report;

import com.aegis.core.bug.BugCluster;
import com.aegis.core.bug.BugExplainer;
import com.aegis.core.bug.BugFingerprint;
import com.aegis.core.bug.RecommendationEngine;
import com.aegis.core.bug.RuleBasedBugExplainer;
import com.aegis.core.bug.RuleBasedRecommendationEngine;
import com.aegis.core.knowledge.Flow;
import com.aegis.core.knowledge.FlowCatalog;
import com.aegis.core.knowledge.Journey;
import com.aegis.core.knowledge.JourneyCatalog;
import com.aegis.core.knowledge.JourneyDefinition;
import com.aegis.core.knowledge.KnowledgeBase;
import com.aegis.core.knowledge.NameSource;
import com.aegis.core.knowledge.Node;
import com.aegis.core.knowledge.NodeCatalog;
import com.aegis.core.knowledge.InspectionCatalog;
import com.aegis.core.knowledge.InspectionFinding;
import com.aegis.core.knowledge.KnowledgeConfig;
import com.aegis.core.knowledge.SignalLog;
import com.aegis.core.knowledge.State;
import com.aegis.core.knowledge.StateCatalog;
import com.aegis.core.knowledge.UxFinding;
import com.aegis.core.knowledge.UxFindingCatalog;
import com.aegis.core.mission.MissionPlan;
import com.aegis.core.mission.RuleBasedMissionPlanner;
import com.aegis.core.reasoning.learning.PatternStatistics;
import com.aegis.core.resilience.ScreenshotSample;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
     * Same report, with this mission's own Experience list (Reporting v2)
     * also supplied — powers the Mission Timeline's accurate Execution
     * Successful/Failed events and the Learning summary. Pass List.of()
     * (what every shorter overload above does) if no ExperienceRepository
     * is available; both degrade gracefully rather than failing. No
     * screenshots either — see the 7-arg overload below.
     */
    public String generate(
            MissionContext context, MissionStatus status, BugExplainer explainer,
            RecommendationEngine recommender, MissionPlan plan, List<Experience> experiences) {
        return generate(context, status, explainer, recommender, plan, experiences, List.of());
    }

    /**
     * The full report: everything above, plus every screenshot
     * SelfHealingBrowser captured this run (v1.1 follow-up to Reporting
     * v2's screenshot hook) — matched to the nearest Execution event by
     * timestamp. Pass List.of() when none were captured.
     */
    public String generate(
            MissionContext context, MissionStatus status, BugExplainer explainer,
            RecommendationEngine recommender, MissionPlan plan, List<Experience> experiences,
            List<ScreenshotSample> screenshots) {

        return generate(MissionReportData.from(context, status, explainer, recommender, plan, experiences, screenshots));
    }

    /** Same report, with the plain-language summary (see {@link ReportSummarizer}) also computed via the given summarizer. */
    public String generate(
            MissionContext context, MissionStatus status, BugExplainer explainer,
            RecommendationEngine recommender, MissionPlan plan, List<Experience> experiences,
            List<ScreenshotSample> screenshots, KnowledgeConfig knowledgeConfig,
            SignalLog signalLog, ReportSummarizer summarizer) {

        return generate(MissionReportData.from(context, status, explainer, recommender, plan, experiences,
                screenshots, knowledgeConfig, signalLog, summarizer));
    }

    /** Stage 2: renders directly from an already-built {@link MissionReportData} — see ExplainabilityReportGenerator's javadoc on its own overload. */
    public String generate(MissionReportData data) {

        StringBuilder html = new StringBuilder();

        html.append("<!doctype html><html><head><meta charset=\"utf-8\">");
        html.append("<title>AEGIS Report — ").append(escape(data.missionName())).append("</title>");
        html.append("<style>").append(css()).append("</style>");
        html.append("</head><body>");

        html.append(renderHeader(data));
        html.append(renderTabs());

        html.append("<main>");
        html.append(tabPanel("overview", true, renderOverviewTab(data)));
        html.append(tabPanel("findings", false, renderFindingsTab(data)));
        html.append(tabPanel("reasoning", false, renderReasoningTab(data)));
        html.append(tabPanel("timeline", false, renderTimeline(data)));
        html.append(tabPanel("world-model", false, renderWorldModelTab(data)));
        html.append("</main>");

        html.append("<script>").append(js()).append("</script>");
        html.append("</body></html>");

        return html.toString();
    }

    /**
     * The very first thing a non-technical reader sees below the title —
     * a plain-language rewrite of the whole report (see {@link
     * ReportSummarizer}), rule-based by default or a real LLM rewrite
     * when opted into ({@code AEGIS_LLM_REPORT_SUMMARY=enabled}). Styled
     * distinctly from the recommendation callout below it — this is a
     * different kind of content ("what happened, in plain English"), not
     * "what to do about it".
     */
    private String renderPlainLanguageSummary(MissionReportData data) {

        return "<section id=\"plain-summary\" class=\"plain-summary-callout\">"
                + "<div class=\"plain-summary-label\">In Plain English</div>"
                + "<p class=\"plain-summary-text\">" + escape(data.plainLanguageSummary()) + "</p>"
                + "</section>";
    }

    private String renderHeader(MissionReportData data) {

        String statusClass = data.status() == MissionStatus.SUCCESS ? "success" : "failure";

        return "<header>"
                + "<h1>" + escape(data.missionName()) + "</h1>"
                + "<span class=\"badge " + statusClass + "\" title=\"" + escape(data.status().name()) + "\">"
                + escape(PlainLanguageGlossary.missionStatusLabel(data.status())) + "</span>"
                + "</header>";
    }

    /**
     * Simplified interactive report: 5 tabs instead of a dozen-plus
     * always-expanded sections — Overview is what a reader lands on;
     * everything else is a click away, not a scroll. Replaces the old
     * sticky jump-nav ({@code renderTableOfContents}).
     */
    private String renderTabs() {

        StringBuilder nav = new StringBuilder("<nav class=\"tabs\" role=\"tablist\">");

        nav.append(tabButton("overview", "Overview", true));
        nav.append(tabButton("findings", "Findings", false));
        nav.append(tabButton("reasoning", "Reasoning &amp; Learning", false));
        nav.append(tabButton("timeline", "Timeline", false));
        nav.append(tabButton("world-model", "World Model", false));

        nav.append("</nav>");

        return nav.toString();
    }

    private String tabButton(String tab, String label, boolean active) {
        return "<button class=\"tab-btn" + (active ? " active" : "") + "\" data-tab=\"" + tab
                + "\" role=\"tab\" aria-selected=\"" + active + "\">" + label + "</button>";
    }

    private String tabPanel(String tab, boolean active, String content) {
        return "<section class=\"tab-panel" + (active ? " active" : "") + "\" data-tab-panel=\"" + tab
                + "\" role=\"tabpanel\"" + (active ? "" : " style=\"display:none\"") + ">" + content + "</section>";
    }

    /** The "stop here if you're busy" tab — nothing here requires interpreting a graph or a table. */
    private String renderOverviewTab(MissionReportData data) {
        return renderPlainLanguageSummary(data)
                + renderSummary(data)
                + renderRecommendation(data)
                + renderStats(data)
                + renderPlan(data);
    }

    /** Everything with a severity badge lives here — bug clusters, UX Quality, and Page Inspection findings. */
    private String renderFindingsTab(MissionReportData data) {
        return renderBugClustersConsolidated(data)
                + renderUxQuality(data.knowledgeBase())
                + renderPageInspection(data.knowledgeBase());
    }

    /** Both are "how it decided, and got better" — aggregate learning stats paired with per-decision detail. */
    private String renderReasoningTab(MissionReportData data) {
        return renderLearning(data) + renderSteps(data);
    }

    /** "What was explored," distinct from "what was wrong" (Findings) or "how it decided" (Reasoning). */
    private String renderWorldModelTab(MissionReportData data) {
        return renderWorldModelGraph(data) + renderPageCoverage(data);
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

            if (event.screenshotDataUri() != null && !event.screenshotDataUri().isBlank()) {
                section.append("<img class=\"timeline-screenshot\" src=\"")
                        .append(escapeAttr(event.screenshotDataUri())).append("\" alt=\"Screenshot\"/>");
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

    /**
     * The World Model's two views, toggled by the same vanilla-JS idiom
     * already used for the Findings tab's category/flat switch and the
     * timeline filter — {@code Path} (default) is "the knowledge path":
     * the real named screens this run actually visited, in order.
     * {@code Graph} is the fuller technical map (branches, revisit heat,
     * every transition) for anyone who wants it.
     */
    private String renderWorldModelGraph(MissionReportData data) {

        StringBuilder section = new StringBuilder("<section id=\"world-model\"><h2>World Model</h2>"
                + "<p class=\"caption\">The path AEGIS actually took through the app, by name — switch to "
                + "Graph for the fuller technical map of every state and transition discovered.</p>");

        if (data.states().isEmpty()) {
            section.append("<p class=\"empty\">No states observed.</p></section>");
            return section.toString();
        }

        section.append("<div class=\"graph-view-toggle timeline-filter\">"
                + "<button data-view=\"path\" class=\"active\">Path</button>"
                + "<button data-view=\"graph\">Graph</button>"
                + "</div>");

        section.append("<div class=\"graph-view\" data-graph-view=\"path\">")
                .append(renderPathView(data)).append("</div>");
        section.append("<div class=\"graph-view\" data-graph-view=\"graph\" style=\"display:none\">")
                .append(renderGraphView(data)).append("</div>");

        section.append(renderNodeDictionary(data.knowledgeBase()));
        section.append(renderFlowsAndJourneys(data.knowledgeBase()));
        section.append("</section>");

        return section.toString();
    }

    /**
     * The knowledge path: {@link Journey#actualNodeKeySequence()} — the
     * real, revisit-collapsed order of named screens this run visited —
     * rendered as a left-to-right stepper instead of a graph at all.
     * Real display names are the primary label; raw detail (state
     * signature, every action taken from that screen) is a click away per
     * step, never shown by default.
     */
    private String renderPathView(MissionReportData data) {

        KnowledgeBase knowledgeBase = data.knowledgeBase();
        Optional<JourneyCatalog> journeyCatalog = knowledgeBase.get(JourneyCatalog.class);
        List<Journey> observed = journeyCatalog.map(JourneyCatalog::observed).orElse(List.of());

        if (observed.isEmpty()) {
            return "<p class=\"empty\">No named path available for this run.</p>";
        }

        Journey journey = observed.get(0);

        // Every declared journey this run's path actually matched, indexed
        // by which node keys belong to it — a node can belong to more than
        // one matched journey (e.g. a shared login screen), and a match is
        // only a subsequence match (JourneyCatalogProvider.isSubsequence),
        // not necessarily contiguous — so each step is tagged individually
        // rather than drawing one bracket over a range, which would
        // misrepresent runs where other steps fall in between.
        Map<String, List<String>> matchedJourneyNamesByNodeKey = new LinkedHashMap<>();
        for (String definitionKey : journey.matchedDefinitionKeys()) {
            journeyCatalog.get().definitions().stream()
                    .filter(definition -> definition.key().equals(definitionKey))
                    .findFirst()
                    .ifPresent(definition -> {
                        for (String nodeKey : definition.nodeKeys()) {
                            matchedJourneyNamesByNodeKey
                                    .computeIfAbsent(nodeKey, key -> new ArrayList<>())
                                    .add(definition.name());
                        }
                    });
        }

        List<String> sequence = journey.actualNodeKeySequence();
        StringBuilder path = new StringBuilder("<div class=\"path-view\">");

        for (int i = 0; i < sequence.size(); i++) {

            String nodeKey = sequence.get(i);
            Optional<Node> node = knowledgeBase.get(NodeCatalog.class).flatMap(catalog -> catalog.byKey(nodeKey));

            if (node.isEmpty()) {
                continue;
            }

            path.append(renderPathStep(data, node.get(), matchedJourneyNamesByNodeKey.get(nodeKey)));

            if (i < sequence.size() - 1) {
                path.append("<span class=\"path-arrow\">&#8594;</span>");
            }
        }

        path.append("</div>");

        return path.toString();
    }

    private String renderPathStep(MissionReportData data, Node node, List<String> matchedJourneyNames) {

        String signature = signatureForNode(data.knowledgeBase(), node);
        long visits = signature != null ? data.stateVisitCounts().getOrDefault(signature, 1L) : 1L;

        List<NavigationEdge> actionsHere = signature != null
                ? data.edges().stream().filter(edge -> edge.fromState().equals(signature)).toList()
                : List.of();

        StringBuilder step = new StringBuilder("<details class=\"path-step\"><summary>");
        step.append("<span class=\"path-step-name\">").append(escape(node.displayName())).append("</span>");

        if (visits > 1) {
            step.append(" <span class=\"visit-badge\">×").append(visits).append("</span>");
        }

        if (matchedJourneyNames != null) {
            for (String journeyName : matchedJourneyNames) {
                step.append(" <span class=\"journey-tag\">&#10003; ").append(escape(journeyName)).append("</span>");
            }
        }

        step.append("</summary>");
        step.append("<div class=\"path-step-detail\">");

        if (actionsHere.isEmpty()) {
            step.append("<p class=\"empty\">No actions recorded from this screen.</p>");
        } else {
            step.append("<ul>");
            for (NavigationEdge edge : actionsHere) {
                step.append("<li>").append(escape(PlainLanguageGlossary.actionVerb(edge.actionType())
                        + " " + shortenTarget(edge.actionTarget()))).append("</li>");
            }
            step.append("</ul>");
        }

        step.append("<p class=\"tech-id\">").append(escape(signature != null ? signature : "(no signature)")).append("</p>");
        step.append("</div></details>");

        return step.toString();
    }

    /**
     * The fuller technical map — same node/edge/heat-map concept as
     * before, three real fixes: a layered top-to-bottom layout (BFS depth
     * from the first-visited state) instead of an arbitrary circle, real
     * display names as the primary label instead of "S1", and self-loops
     * collapsed into a small ×N badge (click/hover reveals the raw
     * repeated actions via the node's tooltip) instead of an always-drawn
     * curve — the self-loop curves were a direct contributor to the
     * oversized/tall bounding box the earlier SVG-scaling fix only
     * partially addressed. The observed knowledge-path (same sequence
     * Path View renders) is highlighted in the accent color so the
     * "golden path" is visible even alongside branches.
     */
    private String renderGraphView(MissionReportData data) {

        List<String> states = data.states();
        Map<String, Integer> depth = computeLayeredDepth(states, data.edges());

        Map<Integer, List<String>> statesByLayer = new LinkedHashMap<>();
        for (String state : states) {
            statesByLayer.computeIfAbsent(depth.get(state), layer -> new ArrayList<>()).add(state);
        }

        double layerHeight = 130;
        double nodeSpacing = 160;
        double topMargin = 60;

        Map<String, double[]> positions = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<String>> layer : statesByLayer.entrySet()) {

            List<String> layerStates = layer.getValue();
            double layerWidth = (layerStates.size() - 1) * nodeSpacing;
            double startX = 340 - layerWidth / 2;

            for (int i = 0; i < layerStates.size(); i++) {
                positions.put(layerStates.get(i), new double[]{
                        startX + i * nodeSpacing,
                        topMargin + layer.getKey() * layerHeight
                });
            }
        }

        Map<String, Double> nodeRadii = new LinkedHashMap<>();
        for (String state : states) {
            nodeRadii.put(state, nodeRadius(data.stateVisitCounts().getOrDefault(state, 1L)));
        }

        Map<NavigationEdge, Long> edgeFrequency = data.edges().stream()
                .collect(Collectors.groupingBy(edge -> edge, LinkedHashMap::new, Collectors.counting()));

        Map<String, Long> selfLoopCountByState = new LinkedHashMap<>();
        Map<String, List<NavigationEdge>> grouped = new LinkedHashMap<>();

        for (NavigationEdge edge : edgeFrequency.keySet()) {
            if (edge.fromState().equals(edge.toState())) {
                selfLoopCountByState.merge(edge.fromState(), edgeFrequency.get(edge), Long::sum);
            } else {
                grouped.computeIfAbsent(edge.fromState() + " " + edge.toState(), key -> new ArrayList<>())
                        .add(edge);
            }
        }

        Set<String> pathSignatures = journeyPathSignatures(data);

        StringBuilder edgesSvg = new StringBuilder();
        double[] bounds = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};

        for (Map.Entry<String, double[]> entry : positions.entrySet()) {
            double r = nodeRadii.get(entry.getKey());
            extend(bounds, entry.getValue()[0] - r, entry.getValue()[1] - r);
            extend(bounds, entry.getValue()[0] + r, entry.getValue()[1] + r);
            // Reserve room above the node for its self-loop badge, if any.
            extend(bounds, entry.getValue()[0], entry.getValue()[1] - r - 26);
        }

        for (List<NavigationEdge> group : grouped.values()) {
            for (int i = 0; i < group.size(); i++) {

                NavigationEdge edge = group.get(i);
                double[][] geometry = nonLoopEdgeGeometry(edge, positions, i);
                boolean onPath = pathSignatures.contains(edge.fromState()) && pathSignatures.contains(edge.toState());

                edgesSvg.append(renderGraphEdge(edge, geometry, edgeFrequency.get(edge), onPath));

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
        double svgWidth = Math.max(400, viewWidth);
        double svgHeight = svgWidth * (viewHeight / viewWidth);

        StringBuilder svg = new StringBuilder();
        svg.append("<svg class=\"graph\" viewBox=\"")
                .append((int) minX).append(' ').append((int) minY).append(' ')
                .append((int) viewWidth).append(' ').append((int) viewHeight)
                .append("\" width=\"").append((int) svgWidth)
                .append("\" height=\"").append((int) svgHeight)
                .append("\">");

        svg.append("<defs><marker id=\"arrow\" viewBox=\"0 0 10 10\" refX=\"9\" refY=\"5\" "
                + "markerWidth=\"6\" markerHeight=\"6\" orient=\"auto-start-reverse\">"
                + "<path d=\"M0,0 L10,5 L0,10 z\" class=\"arrowhead\"/></marker></defs>");

        svg.append(edgesSvg);

        int index = 1;
        for (String state : states) {
            long visits = data.stateVisitCounts().getOrDefault(state, 1L);
            boolean onPath = pathSignatures.contains(state);
            svg.append(renderGraphNode(data.knowledgeBase(), state, positions.get(state), nodeRadii.get(state),
                    visits, index++, selfLoopCountByState.getOrDefault(state, 0L), onPath));
        }

        svg.append("</svg>");

        return svg.toString();
    }

    /** BFS depth from the first-discovered state — which top-to-bottom layer each node renders in. */
    private Map<String, Integer> computeLayeredDepth(List<String> states, List<NavigationEdge> edges) {

        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        for (NavigationEdge edge : edges) {
            if (!edge.fromState().equals(edge.toState())) {
                adjacency.computeIfAbsent(edge.fromState(), key -> new ArrayList<>()).add(edge.toState());
            }
        }

        Map<String, Integer> depth = new LinkedHashMap<>();

        if (states.isEmpty()) {
            return depth;
        }

        Deque<String> queue = new ArrayDeque<>();
        depth.put(states.get(0), 0);
        queue.add(states.get(0));

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String next : adjacency.getOrDefault(current, List.of())) {
                if (!depth.containsKey(next)) {
                    depth.put(next, depth.get(current) + 1);
                    queue.add(next);
                }
            }
        }

        // Anything BFS didn't reach (shouldn't normally happen — every
        // discovered state got there via some transition — but stay
        // defensive) goes one layer past the deepest reached state rather
        // than being silently dropped from the layout.
        int fallbackDepth = depth.values().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        for (String state : states) {
            depth.putIfAbsent(state, fallbackDepth);
        }

        return depth;
    }

    /** The raw state signatures on this run's actual knowledge path — same sequence Path View renders, for highlighting. */
    private Set<String> journeyPathSignatures(MissionReportData data) {

        KnowledgeBase knowledgeBase = data.knowledgeBase();
        List<Journey> observed = knowledgeBase.get(JourneyCatalog.class).map(JourneyCatalog::observed).orElse(List.of());

        if (observed.isEmpty()) {
            return Set.of();
        }

        Set<String> signatures = new LinkedHashSet<>();
        for (String nodeKey : observed.get(0).actualNodeKeySequence()) {
            knowledgeBase.get(NodeCatalog.class).flatMap(catalog -> catalog.byKey(nodeKey))
                    .map(node -> signatureForNode(knowledgeBase, node))
                    .filter(signature -> signature != null)
                    .ifPresent(signatures::add);
        }

        return signatures;
    }

    /** The inverse of {@link #nodeForSignature} — a Node's raw state signature, via the State it belongs to. */
    private String signatureForNode(KnowledgeBase knowledgeBase, Node node) {
        return knowledgeBase.get(StateCatalog.class)
                .flatMap(catalog -> catalog.byId(node.stateId()))
                .map(State::stateSignature)
                .orElse(null);
    }

    /**
     * Knowledge Enrichment Layer integration: the same node dictionary
     * KnowledgeBaseTextRenderer prints, as an HTML table alongside the
     * graph — every node's display name plus where that name came from
     * (NameSource), so a reader can always tell an organization-declared
     * name apart from AEGIS's own auto-generated guess.
     */
    private String renderNodeDictionary(KnowledgeBase knowledgeBase) {

        List<Node> nodes = knowledgeBase.get(NodeCatalog.class).map(NodeCatalog::nodes).orElse(List.of());

        if (nodes.isEmpty()) {
            return "";
        }

        StringBuilder table = new StringBuilder(
                "<h3>Node Dictionary</h3><table><thead><tr><th>ID</th><th>Name</th><th>Source</th></tr></thead><tbody>");

        for (Node node : nodes) {
            table.append("<tr><td>").append(escape(node.stateId())).append("</td>")
                    .append("<td>").append(escape(node.displayName())).append("</td>")
                    .append("<td><span class=\"name-source ").append(node.nameSource().name().toLowerCase()).append("\">")
                    .append(node.nameSource() == NameSource.CONFIGURED ? "configured" : "auto")
                    .append("</span></td></tr>");
        }

        table.append("</tbody></table>");

        return table.toString();
    }

    /**
     * Declared business flows and journeys (definition + whether this
     * run's actual path followed it) — always organization-declared via
     * knowledge.yml, never invented by AEGIS. Renders nothing when none
     * are declared, same as every other optional section in this report.
     */
    private String renderFlowsAndJourneys(KnowledgeBase knowledgeBase) {

        List<Flow> flows = knowledgeBase.get(FlowCatalog.class).map(FlowCatalog::flows).orElse(List.of());
        JourneyCatalog journeyCatalog = knowledgeBase.get(JourneyCatalog.class).orElse(null);
        List<JourneyDefinition> journeys = journeyCatalog == null ? List.of() : journeyCatalog.definitions();

        if (flows.isEmpty() && journeys.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder();

        if (!flows.isEmpty()) {
            out.append("<h3>Flows</h3><ul class=\"flow-list\">");
            for (Flow flow : flows) {
                out.append("<li><strong>").append(escape(flow.name())).append(":</strong> ")
                        .append(escape(String.join(" → ", flow.nodeKeys())));
                if (!flow.unmatchedNodeKeys().isEmpty()) {
                    out.append(" <span class=\"meta\">(not yet discovered: ")
                            .append(escape(String.join(", ", flow.unmatchedNodeKeys()))).append(")</span>");
                }
                out.append("</li>");
            }
            out.append("</ul>");
        }

        if (!journeys.isEmpty()) {

            List<Journey> observed = journeyCatalog.observed();

            out.append("<h3>Journeys</h3><ul class=\"flow-list\">");
            for (JourneyDefinition definition : journeys) {

                boolean followedThisRun = observed.stream()
                        .anyMatch(journey -> journey.matchedDefinitionKeys().contains(definition.key()));

                out.append("<li><strong>").append(escape(definition.name())).append(":</strong> ")
                        .append(escape(String.join(" → ", definition.nodeKeys())));
                if (!definition.unmatchedNodeKeys().isEmpty()) {
                    out.append(" <span class=\"meta\">(not yet discovered: ")
                            .append(escape(String.join(", ", definition.unmatchedNodeKeys()))).append(")</span>");
                }
                out.append(followedThisRun
                        ? " <span class=\"badge success\">followed this run</span>"
                        : " <span class=\"meta\">(not followed this run)</span>");
                out.append("</li>");
            }
            out.append("</ul>");
        }

        return out.toString();
    }

    /**
     * How good this run's navigation experience actually was — backtracking,
     * declared-journey divergence, navigation friction, and structural
     * accessible-name signals from {@link UxFindingCatalog}. Renders
     * nothing when there are no findings, same as every other optional
     * section in this report.
     */
    private String renderUxQuality(KnowledgeBase knowledgeBase) {

        List<UxFinding> findings = knowledgeBase.get(UxFindingCatalog.class)
                .map(UxFindingCatalog::findings)
                .orElse(List.of());

        if (findings.isEmpty()) {
            return "";
        }

        StringBuilder table = new StringBuilder(
                "<h3>UX Quality</h3><table><thead><tr><th>Type</th><th>Severity</th><th>Summary</th><th>Evidence</th></tr></thead><tbody>");

        for (UxFinding finding : findings) {
            table.append("<tr><td><strong>").append(escape(PlainLanguageGlossary.uxFindingLabel(finding.type()))).append("</strong>")
                    .append("<div class=\"meaning\">").append(escape(PlainLanguageGlossary.uxFindingExplanation(finding.type()))).append("</div>")
                    .append("<code class=\"tech-id\">").append(escape(finding.type().name())).append("</code></td>")
                    .append("<td><span class=\"badge severity-").append(finding.severity().name().toLowerCase())
                    .append("\" title=\"").append(escape(PlainLanguageGlossary.severityWhyItMatters(finding.severity()))).append("\">")
                    .append(escape(PlainLanguageGlossary.severityLabel(finding.severity()))).append("</span></td>")
                    .append("<td>").append(escape(finding.summary())).append("</td>")
                    .append("<td><code class=\"evidence-detail\">").append(finding.evidence().isBlank() ? "&mdash;" : "Element: " + escape(finding.evidence()))
                    .append("</code></td></tr>");
        }

        table.append("</tbody></table>");

        return table.toString();
    }

    /**
     * Console errors/exceptions, network failures, broken links, and UI
     * checks (contrast, accessible name, zero-size, overflow) from {@link
     * InspectionCatalog}. Renders nothing when there are no findings —
     * either nothing was wrong, or inspection capture was never enabled.
     */
    private String renderPageInspection(KnowledgeBase knowledgeBase) {

        List<InspectionFinding> findings = knowledgeBase.get(InspectionCatalog.class)
                .map(InspectionCatalog::findings)
                .orElse(List.of());

        if (findings.isEmpty()) {
            return "";
        }

        StringBuilder table = new StringBuilder(
                "<h3>Page Inspection</h3><table><thead><tr><th>Type</th><th>Severity</th><th>Summary</th><th>Evidence</th></tr></thead><tbody>");

        for (InspectionFinding finding : findings) {
            table.append("<tr><td><strong>").append(escape(PlainLanguageGlossary.inspectionCheckLabel(finding.type()))).append("</strong>")
                    .append("<div class=\"meaning\">").append(escape(PlainLanguageGlossary.inspectionCheckExplanation(finding.type()))).append("</div>")
                    .append("<code class=\"tech-id\">").append(escape(finding.type().name())).append("</code></td>")
                    .append("<td><span class=\"badge severity-").append(finding.severity().name().toLowerCase())
                    .append("\" title=\"").append(escape(PlainLanguageGlossary.severityWhyItMatters(finding.severity()))).append("\">")
                    .append(escape(PlainLanguageGlossary.severityLabel(finding.severity()))).append("</span></td>")
                    .append("<td>").append(escape(finding.summary())).append("</td>")
                    .append("<td><code class=\"evidence-detail\">").append(finding.evidence().isBlank() ? "&mdash;" : "Element: " + escape(finding.evidence()))
                    .append("</code></td></tr>");
        }

        table.append("</tbody></table>");

        return table.toString();
    }

    /** Looks a Node up by the raw StateSignature renderGraph already keys everything by, via the State it belongs to. */
    private Optional<Node> nodeForSignature(KnowledgeBase knowledgeBase, String stateSignature) {

        Optional<State> state = knowledgeBase.get(StateCatalog.class)
                .flatMap(catalog -> catalog.states().stream()
                        .filter(s -> s.stateSignature().equals(stateSignature))
                        .findFirst());

        return state.flatMap(s -> knowledgeBase.get(NodeCatalog.class).flatMap(nc -> nc.byStateId(s.id())));
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
     * Returns the points defining a (non-self-loop) edge's curve —
     * [start, control, end] — used both to draw the path
     * (renderGraphEdge) and to bound the SVG viewBox, so the two can
     * never drift out of sync. Self-loops are no longer drawn as edges at
     * all — see renderGraphView's self-loop badge instead.
     */
    private double[][] nonLoopEdgeGeometry(NavigationEdge edge, Map<String, double[]> positions, int parallelIndex) {

        double[] from = positions.get(edge.fromState());
        double[] to = positions.get(edge.toState());

        double curve = parallelIndex * 22;
        double midX = (from[0] + to[0]) / 2 - (to[1] - from[1]) * 0.001 * curve * 10;
        double midY = (from[1] + to[1]) / 2 + (to[0] - from[0]) * 0.001 * curve * 10;

        return new double[][]{{from[0], from[1]}, {midX, midY}, {to[0], to[1]}};
    }

    private String renderGraphNode(
            KnowledgeBase knowledgeBase, String state, double[] pos, double radius, long visits, int index,
            long selfLoopCount, boolean onPath) {

        double heatOpacity = nodeHeatOpacity(visits);

        // Real display name is the primary visible label now — "S1" alone
        // told a reader nothing without hovering; the raw signature and
        // per-run label both stay available in the tooltip for anyone who
        // needs them.
        String displayName = nodeForSignature(knowledgeBase, state).map(Node::displayName).orElse("S" + index);
        String label = displayName.length() > 16 ? displayName.substring(0, 14) + "…" : displayName;

        StringBuilder node = new StringBuilder();
        node.append("<g class=\"node").append(onPath ? " on-path" : "").append("\" data-state=\"")
                .append(escapeAttr(state)).append("\">");

        node.append(String.format(
                "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"%.1f\" style=\"fill: var(--accent); fill-opacity: %.2f\"/>",
                pos[0], pos[1], radius, heatOpacity));

        node.append(String.format(
                "<text x=\"%.1f\" y=\"%.1f\" class=\"node-label\">%s</text>",
                pos[0], pos[1] + 5, escape(label)));

        if (selfLoopCount > 0) {
            node.append(String.format(
                    "<text x=\"%.1f\" y=\"%.1f\" class=\"self-loop-badge\">×%d</text>",
                    pos[0], pos[1] - radius - 10, selfLoopCount));
        }

        node.append(String.format(
                "<title>%s — %s (S%d, visited %d time%s%s)</title>",
                escape(displayName), escape(state), index, visits, visits == 1 ? "" : "s",
                selfLoopCount > 0
                        ? ", " + selfLoopCount + " repeated action" + (selfLoopCount == 1 ? "" : "s") + " here"
                        : ""));

        node.append("</g>");

        return node.toString();
    }

    private String renderGraphEdge(NavigationEdge edge, double[][] geometry, long frequency, boolean onPath) {

        String label = escape(PlainLanguageGlossary.actionVerb(edge.actionType()) + " " + shortenTarget(edge.actionTarget()))
                + (frequency > 1 ? " (×" + frequency + ")" : "");

        double strokeWidth = edgeStrokeWidth(frequency);

        double[] from = geometry[0];
        double[] mid = geometry[1];
        double[] to = geometry[2];

        String path = String.format(
                "M %.1f %.1f Q %.1f %.1f, %.1f %.1f",
                from[0], from[1], mid[0], mid[1], to[0], to[1]
        );

        return "<g class=\"edge" + (onPath ? " on-path" : "") + "\" data-from=\"" + escapeAttr(edge.fromState())
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
                .append(tile("New Things Learned", String.valueOf(summary.newExperiences())))
                .append(tile("Adjusted Based on Results", String.valueOf(summary.updatedActions())))
                .append(tile("Got More Reliable", String.valueOf(summary.confidenceIncreased())))
                .append(tile("Got Less Reliable", String.valueOf(summary.confidenceReduced())))
                .append("</div>");

        if (summary.actionPerformance().isEmpty()) {
            section.append("<p class=\"empty\">No experience recorded this mission.</p></section>");
            return section.toString();
        }

        List<PatternStatistics> best = summary.actionPerformance()
                .subList(0, Math.min(3, summary.actionPerformance().size()));

        List<PatternStatistics> worstFirst = summary.actionPerformance().reversed();
        List<PatternStatistics> worst = worstFirst.subList(0, Math.min(3, worstFirst.size()));

        section.append("<p class=\"caption\">").append(escape(learningLine(summary))).append("</p>");
        section.append("<details class=\"learning-detail\"><summary>Show the technical breakdown, action by action</summary>");
        section.append("<div class=\"performance-columns\">");
        section.append("<div><h3>Best Performing Actions</h3>").append(performanceTable(best)).append("</div>");
        section.append("<div><h3>Worst Performing Actions</h3>").append(performanceTable(worst)).append("</div>");
        section.append("</div></details></section>");

        return section.toString();
    }

    private String performanceTable(List<PatternStatistics> stats) {

        StringBuilder table = new StringBuilder(
                "<table><thead><tr><th>Action</th><th>Success Rate</th><th>Runs</th></tr></thead><tbody>");

        for (PatternStatistics stat : stats) {

            table.append("<tr><td>").append(escape(PlainLanguageGlossary.actionVerb(stat.action().type()) + " " + stat.action().target())).append("</td>")
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
     * Consolidates the old "Findings Dashboard" (bug clusters grouped by
     * {@link FindingCategory}) and "Bug Clusters" (the same clusters,
     * flat, most-severe-first) sections — they rendered the identical
     * {@code data.bugClusters()} list twice, just grouped differently.
     * Both views are still server-rendered (no client round-trip), just
     * toggled with the same vanilla-JS show/hide idiom the timeline
     * filter already established, one visible at a time.
     */
    private String renderBugClustersConsolidated(MissionReportData data) {

        StringBuilder section = new StringBuilder("<section id=\"bug-clusters\"><h2>Findings</h2>"
                + "<p class=\"caption\">Grouped by a normalized fingerprint (Phase 6) — "
                + "recurring or cross-page clusters are stronger signals than any single occurrence.</p>");

        if (data.bugClusters().isEmpty()) {
            section.append("<p class=\"empty\">No findings to cluster.</p></section>");
            return section.toString();
        }

        Map<String, List<Finding>> occurrencesByFingerprint = data.findings().stream()
                .collect(Collectors.groupingBy(BugFingerprint::of, LinkedHashMap::new, Collectors.toList()));

        section.append("<div class=\"cluster-group-toggle timeline-filter\">"
                + "<button data-group=\"category\" class=\"active\">By Category</button>"
                + "<button data-group=\"flat\">Most Severe First</button>"
                + "</div>");

        section.append("<div class=\"cluster-view\" data-group-view=\"category\">");
        for (Map.Entry<FindingCategory, List<BugCluster>> entry : data.findingsByCategory().entrySet()) {
            section.append("<h3>").append(escape(PlainLanguageGlossary.categoryLabel(entry.getKey())))
                    .append(" (").append(entry.getValue().size()).append(")</h3>");
            for (BugCluster cluster : entry.getValue()) {
                section.append(renderClusterRow(data, cluster, occurrencesByFingerprint));
            }
        }
        section.append("</div>");

        section.append("<div class=\"cluster-view\" data-group-view=\"flat\" style=\"display:none\">");
        for (BugCluster cluster : data.bugClusters()) {
            section.append(renderClusterRow(data, cluster, occurrencesByFingerprint));
        }
        section.append("</div>");

        section.append("</section>");

        return section.toString();
    }

    private String renderClusterRow(
            MissionReportData data, BugCluster cluster, Map<String, List<Finding>> occurrencesByFingerprint) {

        StringBuilder row = new StringBuilder();

        row.append("<div class=\"finding ").append(cluster.severity().name().toLowerCase()).append("\">")
                .append("<span class=\"badge severity-").append(cluster.severity().name().toLowerCase())
                .append("\" title=\"").append(escape(PlainLanguageGlossary.severityWhyItMatters(cluster.severity()))).append("\">")
                .append(escape(PlainLanguageGlossary.severityLabel(cluster.severity()))).append("</span> ")
                .append("<span class=\"summary\">").append(escape(cluster.representativeSummary())).append("</span>")
                .append("<div class=\"meaning\">").append(escape(data.explanationFor(cluster))).append("</div>");

        row.append("<div class=\"meta\">×").append(cluster.occurrenceCount());

        if (cluster.spansMultiplePages()) {
            row.append(" — seen on ").append(cluster.urls().size())
                    .append(" different pages, may share a root cause");
        }

        row.append("</div>");

        List<Finding> occurrences = occurrencesByFingerprint.getOrDefault(cluster.fingerprint(), List.of());

        if (occurrences.size() > 1) {

            row.append("<details class=\"cluster-occurrences\"><summary>Show ")
                    .append(occurrences.size()).append(" individual occurrences</summary>");

            for (Finding finding : occurrences) {
                row.append("<div class=\"occurrence\">").append(escape(finding.summary()))
                        .append("<div class=\"meta\">").append(escape(finding.url()))
                        .append(" — ").append(finding.detectedAt()).append("</div></div>");
            }

            row.append("</details>");
        }

        row.append("</div>");

        return row.toString();
    }

    private static final Pattern FRAME_TARGET_PREFIX = Pattern.compile("^frame:(\\d+)>(.*)$", Pattern.DOTALL);

    /**
     * A framed target (see PlaywrightBrowser's iframe support) carries a
     * raw {@code frame:N>} prefix that means nothing to a reader — this
     * strips it and appends a plain-language note instead, before the
     * existing length truncation, so a report never shows the raw
     * mechanism string.
     */
    private String shortenTarget(String target) {

        Matcher framePrefix = FRAME_TARGET_PREFIX.matcher(target);
        String suffix = "";
        String bare = target;

        if (framePrefix.matches()) {
            bare = framePrefix.group(2);
            suffix = " (in embedded frame)";
        }

        String shortened = bare.length() > 24 ? bare.substring(0, 21) + "..." : bare;

        return shortened + suffix;
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
            String selectedValue = selected.action().value();

            section.append("<details class=\"step\">")
                    .append("<summary>Step ").append(step.step()).append(": ")
                    .append("<span class=\"strategy-badge\">").append(escape(step.strategy())).append("</span> ")
                    .append(escape(PlainLanguageGlossary.actionVerb(selected.action().type()) + " " + selected.action().target()))
                    .append(selectedValue != null && !selectedValue.isBlank()
                            ? " <span class=\"value-tag\">value: " + escape(selectedValue) + "</span>" : "")
                    .append(" <span class=\"confidence\">confidence=")
                    .append(String.format("%.0f%%", selected.confidence() * 100))
                    .append(" (").append(marginText).append(")</span>")
                    .append(stepWasLearned ? " <span class=\"learned-badge\">learned</span>" : "")
                    .append("</summary>");

            section.append("<div class=\"candidates\"><table><thead><tr>"
                    + "<th>Type</th><th>Target</th><th>Value</th><th>Confidence</th><th></th><th>Reasoning</th></tr></thead><tbody>");

            for (CandidateAction candidate : step.candidates()) {

                boolean isSelected = candidate.action().id().equals(selected.action().id());
                boolean candidateWasLearned = candidate.reasoning().contains("learning-adjusted");
                String candidateValue = candidate.action().value();

                section.append("<tr class=\"").append(isSelected ? "selected" : "").append("\">")
                        .append("<td>").append(escape(PlainLanguageGlossary.actionVerb(candidate.action().type()))).append("</td>")
                        .append("<td>").append(escape(candidate.action().target())).append("</td>")
                        .append("<td>").append(candidateValue != null && !candidateValue.isBlank() ? escape(candidateValue) : "&mdash;").append("</td>")
                        .append("<td><div class=\"bar\"><div class=\"bar-fill\" style=\"width:")
                        .append((int) (candidate.confidence() * 100)).append("%\"></div></div> ")
                        .append(String.format("%.0f%%", candidate.confidence() * 100)).append("</td>")
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
                .tabs { position: sticky; top: 0; z-index: 10; display: flex; flex-wrap: wrap; gap: .4rem;
                    background: var(--bg); padding: .6rem 0 1rem; margin-bottom: 1.25rem;
                    border-bottom: 1px solid var(--border); }
                .tab-btn { font: inherit; font-size: .88rem; font-weight: 600; padding: .45rem 1rem;
                    border-radius: .5rem .5rem 0 0; border: 1px solid transparent; background: none;
                    color: var(--muted); cursor: pointer; }
                .tab-btn:hover { color: var(--fg); }
                .tab-btn.active { color: var(--accent); border: 1px solid var(--border);
                    border-bottom-color: var(--bg); background: var(--card);
                    position: relative; top: 1px; }
                .tab-panel.active { display: block; }
                .cluster-occurrences { margin-top: .5rem; font-size: .82rem; }
                .cluster-occurrences summary { cursor: pointer; font-weight: 600; color: var(--accent); }
                .cluster-occurrences .occurrence { padding: .4rem 0; border-top: 1px solid var(--border); }
                .cluster-occurrences .occurrence:first-of-type { border-top: none; margin-top: .4rem; }
                h1 { font-size: 1.4rem; margin: 0; }
                h2 { font-size: 1.05rem; margin: 0 0 .75rem; }
                .badge { padding: .25rem .75rem; border-radius: 999px; font-weight: 600; font-size: .85rem; color: #fff; }
                .badge.success { background: var(--success); }
                .badge.failure { background: var(--failure); }
                .badge.severity-low { background: var(--low); }
                .badge.severity-medium { background: var(--medium); }
                .badge.severity-high { background: var(--high); }
                .badge.severity-critical { background: var(--critical); }
                .name-source { font-size: .7rem; font-weight: 700; text-transform: uppercase; letter-spacing: .03em;
                    padding: .1rem .5rem; border-radius: 999px; }
                .name-source.configured { background: color-mix(in srgb, var(--success) 20%, transparent); color: var(--success); }
                .name-source.auto_title, .name-source.auto_url { background: var(--card); color: var(--muted); border: 1px solid var(--border); }
                .flow-list { list-style: none; padding: 0; margin: 0 0 .75rem; font-size: .88rem; }
                .flow-list li { padding: .3rem 0; border-bottom: 1px solid var(--border); }
                .flow-list li:last-child { border-bottom: none; }
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
                .plain-summary-callout { background: color-mix(in srgb, var(--accent) 16%, var(--card));
                    border: 1px solid var(--accent); border-left: 5px solid var(--accent);
                    border-radius: .5rem; padding: 1.1rem 1.4rem; }
                .plain-summary-label { font-size: .75rem; font-weight: 700; text-transform: uppercase;
                    color: var(--accent); letter-spacing: .04em; margin-bottom: .4rem; }
                .plain-summary-text { font-size: 1.05rem; line-height: 1.5; margin: 0; }
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
                .graph { display: block; max-width: 100%; height: auto; overflow: visible; }
                .node circle { fill: var(--card); stroke: var(--accent); stroke-width: 2; }
                .node text.node-label { fill: var(--fg); font-size: 13px; font-weight: 600; text-anchor: middle; }
                .node text.self-loop-badge { fill: var(--muted); font-size: 11px; font-weight: 700; text-anchor: middle; }
                .node.on-path circle { stroke-width: 3.5; }
                .edge-line { fill: none; stroke: var(--muted); stroke-width: 1.5; }
                .edge text { fill: var(--muted); font-size: 10px; text-anchor: middle; }
                .edge.on-path .edge-line { stroke: var(--accent); }
                .edge.on-path text { fill: var(--accent); font-weight: 600; }
                .node.dim, .edge.dim { opacity: .15; }

                .graph-view-toggle { margin-bottom: 1rem; }
                .path-view { display: flex; flex-wrap: wrap; align-items: center; gap: .35rem; margin-bottom: 1rem; }
                .path-arrow { color: var(--muted); font-size: 1.1rem; flex: none; }
                .path-step { background: var(--card); border: 1px solid var(--border); border-radius: .6rem;
                    padding: .55rem .9rem; }
                .path-step summary { cursor: pointer; font-weight: 600; list-style: none; }
                .path-step summary::-webkit-details-marker { display: none; }
                .path-step-name { font-size: .95rem; }
                .visit-badge { display: inline-block; font-size: .7rem; font-weight: 700; padding: .1rem .45rem;
                    border-radius: 999px; background: color-mix(in srgb, var(--fg) 10%, transparent); color: var(--muted); }
                .journey-tag { display: inline-block; font-size: .7rem; font-weight: 700; padding: .1rem .5rem;
                    border-radius: 999px; background: color-mix(in srgb, var(--success) 18%, transparent); color: var(--success); }
                .path-step-detail { margin-top: .5rem; font-size: .82rem; color: var(--muted); }
                .path-step-detail ul { margin: 0 0 .4rem; padding-left: 1.1rem; }
                .path-step-detail .tech-id { margin-top: 0; }
                .finding { border-left: 4px solid var(--low); background: var(--card);
                    border-radius: .25rem; padding: .6rem 1rem; margin-bottom: .5rem; }
                .finding.critical { border-color: var(--critical); }
                .finding.high { border-color: var(--high); }
                .finding.medium { border-color: var(--medium); }
                .finding.low { border-color: var(--low); }
                .severity { font-weight: 700; font-size: .8rem; }
                .meaning { font-size: .85rem; margin-top: .3rem; color: var(--muted); }
                .meta { color: var(--muted); font-size: .8rem; margin-top: .2rem; }
                .tech-id { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: .68rem;
                    color: var(--muted); background: var(--bg); border: 1px solid var(--border); border-radius: .25rem;
                    padding: .05rem .35rem; display: inline-block; margin-top: .3rem; }
                .evidence-detail { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: .78rem;
                    color: var(--muted); word-break: break-all; }
                .step { background: var(--card); border: 1px solid var(--border); border-radius: .5rem;
                    padding: .5rem .9rem; margin-bottom: .5rem; }
                .step summary { cursor: pointer; font-weight: 600; }
                .learning-detail { margin-top: .5rem; }
                .learning-detail summary { cursor: pointer; font-weight: 600; color: var(--accent); font-size: .85rem; }
                .learning-detail .performance-columns { margin-top: .75rem; }
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
                .strategy-badge { display: inline-block; font-size: .7rem; font-weight: 700; padding: .1rem .45rem;
                    border-radius: 999px; text-transform: uppercase; background: color-mix(in srgb, var(--fg) 10%, transparent); color: var(--muted); }
                .value-tag { color: var(--muted); font-size: .85rem; font-family: ui-monospace, monospace; }
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
                var TAB_ALIASES = {
                    'plain-summary': 'overview', 'summary': 'overview', 'recommendation': 'overview',
                    'stats': 'overview', 'plan': 'overview', 'overview': 'overview',
                    'findings-dashboard': 'findings', 'bug-clusters': 'findings', 'findings': 'findings',
                    'learning': 'reasoning', 'reasoning-steps': 'reasoning', 'reasoning': 'reasoning',
                    'timeline': 'timeline',
                    'coverage': 'world-model', 'world-model': 'world-model'
                };

                function activateTab(tab) {
                    document.querySelectorAll('.tab-btn').forEach(function (b) {
                        var active = b.getAttribute('data-tab') === tab;
                        b.classList.toggle('active', active);
                        b.setAttribute('aria-selected', active ? 'true' : 'false');
                    });
                    document.querySelectorAll('.tab-panel').forEach(function (p) {
                        var active = p.getAttribute('data-tab-panel') === tab;
                        p.classList.toggle('active', active);
                        p.style.display = active ? '' : 'none';
                    });
                }

                document.querySelectorAll('.tab-btn').forEach(function (btn) {
                    btn.addEventListener('click', function () {
                        var tab = btn.getAttribute('data-tab');
                        activateTab(tab);
                        history.replaceState(null, '', '#' + tab);
                    });
                });

                var initialTab = TAB_ALIASES[location.hash.replace('#', '')] || 'overview';
                activateTab(initialTab);

                window.addEventListener('hashchange', function () {
                    activateTab(TAB_ALIASES[location.hash.replace('#', '')] || 'overview');
                });

                document.querySelectorAll('.cluster-group-toggle button').forEach(function (btn) {
                    btn.addEventListener('click', function () {
                        document.querySelectorAll('.cluster-group-toggle button').forEach(function (b) {
                            b.classList.remove('active');
                        });
                        btn.classList.add('active');
                        var group = btn.getAttribute('data-group');
                        document.querySelectorAll('.cluster-view').forEach(function (v) {
                            v.style.display = v.getAttribute('data-group-view') === group ? '' : 'none';
                        });
                    });
                });

                document.querySelectorAll('.graph-view-toggle button').forEach(function (btn) {
                    btn.addEventListener('click', function () {
                        document.querySelectorAll('.graph-view-toggle button').forEach(function (b) {
                            b.classList.remove('active');
                        });
                        btn.classList.add('active');
                        var view = btn.getAttribute('data-view');
                        document.querySelectorAll('.graph-view').forEach(function (v) {
                            v.style.display = v.getAttribute('data-graph-view') === view ? '' : 'none';
                        });
                    });
                });

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
