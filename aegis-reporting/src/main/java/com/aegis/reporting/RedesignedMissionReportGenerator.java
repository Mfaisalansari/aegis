package com.aegis.reporting;

import com.aegis.core.bug.BugCluster;
import com.aegis.core.bug.BugFingerprint;
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
import com.aegis.core.knowledge.State;
import com.aegis.core.knowledge.StateCatalog;
import com.aegis.core.knowledge.UxFinding;
import com.aegis.core.knowledge.UxFindingCatalog;
import com.aegis.core.reasoning.learning.PatternStatistics;
import com.aegis.core.report.ExplorationCoverage;
import com.aegis.core.report.FindingCategory;
import com.aegis.core.report.LearningAdjustmentGlossary;
import com.aegis.core.report.LearningHonestyGlossary;
import com.aegis.core.report.LearningSummary;
import com.aegis.core.report.MissionReportData;
import com.aegis.core.report.PageCoverage;
import com.aegis.core.report.PlainLanguageGlossary;
import com.aegis.core.report.TimelineEvent;
import com.aegis.model.finding.Finding;
import com.aegis.model.mission.MissionStatus;
import com.aegis.model.reasoning.CandidateAction;
import com.aegis.model.reasoning.NavigationEdge;
import com.aegis.model.reasoning.ReasoningStep;

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
 * A visual redesign of {@code HtmlExplainabilityReportGenerator}'s report —
 * same five tabs, same underlying {@link MissionReportData}, same content —
 * built as a new, separate, additive class rather than editing that
 * generator, which is on AEGIS's frozen Stable Components list
 * ({@code AEGIS_ROADMAP.md}). Two concrete things changed from the
 * original: an instrument-panel visual language (IBM Plex Sans/Mono,
 * semantic-color left-edge stripes) in place of the original palette, and
 * a fixed world-model graph layout — node spacing and edge-label placement
 * now derive from each label's actual rendered width instead of a fixed
 * 160px constant, which is what caused the overlap in the original. Every
 * other structural decision (tabs, sections, data read from {@link
 * MissionReportData}) is left exactly as it was — see AEGIS's own "Option
 * B: redesigned, still two reports" plan for why this doesn't restructure
 * into a single cross-linked page the way an alternative "Option A" would.
 */
public class RedesignedMissionReportGenerator {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

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

    private String renderPlainLanguageSummary(MissionReportData data) {
        return "<section id=\"plain-summary\" class=\"panel panel-stripe "
                + (data.status() == MissionStatus.SUCCESS ? "good" : "bad") + "\">"
                + "<div class=\"panel-body\">"
                + "<p class=\"panel-label\">In Plain English</p>"
                + "<p class=\"summary-text\">" + escape(data.plainLanguageSummary()) + "</p>"
                + "</div></section>";
    }

    private String renderHeader(MissionReportData data) {

        boolean success = data.status() == MissionStatus.SUCCESS;

        return "<div class=\"wrap\"><header class=\"masthead\">"
                + "<div><p class=\"eyebrow\">AEGIS Mission Report — Technical</p>"
                + "<h1>" + escape(data.missionName()) + "</h1></div>"
                + "<span class=\"verdict " + (success ? "pass" : "fail") + "\" title=\"" + escape(data.status().name()) + "\">"
                + escape(PlainLanguageGlossary.missionStatusLabel(data.status())) + "</span>"
                + "</header></div>";
    }

    private String renderTabs() {

        StringBuilder nav = new StringBuilder("<div class=\"wrap\"><nav class=\"tabs\" role=\"tablist\">");

        nav.append(tabButton("overview", "Overview", true));
        nav.append(tabButton("findings", "Findings", false));
        nav.append(tabButton("reasoning", "Reasoning &amp; Learning", false));
        nav.append(tabButton("timeline", "Timeline", false));
        nav.append(tabButton("world-model", "World Model", false));

        nav.append("</nav></div>");

        return nav.toString();
    }

    private String tabButton(String tab, String label, boolean active) {
        return "<button class=\"tab-btn" + (active ? " active" : "") + "\" data-tab=\"" + tab
                + "\" role=\"tab\" aria-selected=\"" + active + "\">" + label + "</button>";
    }

    private String tabPanel(String tab, boolean active, String content) {
        return "<section class=\"tab-panel" + (active ? " active" : "") + "\" data-tab-panel=\"" + tab
                + "\" role=\"tabpanel\"" + (active ? "" : " style=\"display:none\"") + "><div class=\"wrap\">"
                + content + "</div></section>";
    }

    private String renderOverviewTab(MissionReportData data) {
        return renderPlainLanguageSummary(data)
                + renderSummary(data)
                + renderRecommendation(data)
                + renderStats(data)
                + renderPlan(data);
    }

    private String renderFindingsTab(MissionReportData data) {
        return renderBugClustersConsolidated(data)
                + renderUxQuality(data.knowledgeBase())
                + renderPageInspection(data.knowledgeBase());
    }

    private String renderReasoningTab(MissionReportData data) {
        return renderLearning(data) + renderSteps(data);
    }

    private String renderWorldModelTab(MissionReportData data) {
        return renderWorldModelGraph(data) + renderPageCoverage(data);
    }

    private String renderSummary(MissionReportData data) {

        return "<section id=\"summary\" class=\"panel\"><div class=\"panel-body\">"
                + "<p class=\"goal\"><strong>Goal:</strong> " + escape(data.missionGoal()) + "</p>"
                + "<p class=\"outcome\">" + escape(data.outcomeSummary()) + "</p>"
                + "<dl class=\"exec-facts\">"
                + fact("Duration", formatDuration(data.duration()))
                + fact("Coverage", String.format("%.0f%%", data.coverage().coveragePercent()))
                + fact("Findings", String.valueOf(data.findings().size()))
                + fact("Bug Clusters", String.valueOf(data.bugClusters().size()))
                + fact("Learning", learningLine(data.learningSummary()))
                + "</dl></div></section>";
    }

    private String fact(String label, String value) {
        return "<div><dt>" + escape(label) + "</dt><dd>" + escape(value) + "</dd></div>";
    }

    private String renderRecommendation(MissionReportData data) {
        return "<section id=\"recommendation\" class=\"panel panel-stripe accent\"><div class=\"panel-body rec\">"
                + "<span class=\"rec-icon\">Recommendation</span>"
                + "<p class=\"rec-text\">" + escape(data.recommendation()) + "</p>"
                + "</div></section>";
    }

    private String learningLine(LearningSummary summary) {

        if (summary.newExperiences() == 0 && summary.updatedActions() == 0) {
            return "no experience recorded this mission";
        }

        return LearningHonestyGlossary.honestLearningLine(summary);
    }

    private String formatDuration(Duration duration) {

        long totalSeconds = duration.toSeconds();

        if (totalSeconds < 60) {
            return String.format("%.1fs", duration.toMillis() / 1000.0);
        }

        return (totalSeconds / 60) + "m " + (totalSeconds % 60) + "s";
    }

    private String renderTimeline(MissionReportData data) {

        StringBuilder section = new StringBuilder("<section id=\"timeline\"><h2 class=\"section-title\">Mission Timeline</h2>"
                + "<p class=\"caption\">A chronological replay of the whole run, reconstructed from what was "
                + "actually recorded — nothing here was captured separately from the data elsewhere in this "
                + "report.</p>");

        section.append("<div class=\"chip-row\">"
                + "<button data-kind=\"all\" class=\"chip active\">All</button>"
                + "<button data-kind=\"observation\" class=\"chip\">Observations</button>"
                + "<button data-kind=\"reasoning\" class=\"chip\">Reasoning</button>"
                + "<button data-kind=\"execution\" class=\"chip\">Executions</button>"
                + "<button data-kind=\"finding\" class=\"chip\">Findings</button>"
                + "</div>");

        section.append("<div class=\"panel\"><ol class=\"timeline\">");

        for (TimelineEvent event : data.timeline()) {

            String kindClass = event.kind().name().toLowerCase().replace('_', '-');

            section.append("<li class=\"tl-row ").append(kindClass).append("\">")
                    .append("<span class=\"tl-time\">").append(TIME_FORMAT.format(event.timestamp()))
                    .append("</span>")
                    .append("<span class=\"tl-dot\"></span>")
                    .append("<div class=\"tl-body\">")
                    .append("<div class=\"tl-headline\">").append(escape(event.headline())).append("</div>");

            if (event.detail() != null && !event.detail().isBlank()) {
                section.append("<div class=\"tl-detail\">").append(escape(LearningAdjustmentGlossary.honestReasoning(event.detail()))).append("</div>");
            }

            if (event.screenshotDataUri() != null && !event.screenshotDataUri().isBlank()) {
                section.append("<img class=\"tl-screenshot\" src=\"")
                        .append(escapeAttr(event.screenshotDataUri())).append("\" alt=\"Screenshot\"/>");
            }

            section.append("</div></li>");
        }

        section.append("</ol></div></section>");

        return section.toString();
    }

    private String renderPlan(MissionReportData data) {

        StringBuilder section = new StringBuilder("<section id=\"plan\"><h2 class=\"section-title\">Mission Plan</h2>"
                + "<p class=\"caption\">Advisory only — generated before the run, never read by the live "
                + "decision-making pipeline.</p><div class=\"panel\"><div class=\"panel-body\"><ol class=\"plan\">");

        for (String step : data.plan().steps()) {
            section.append("<li>").append(escape(step)).append("</li>");
        }

        section.append("</ol></div></div></section>");

        return section.toString();
    }

    private String renderStats(MissionReportData data) {

        long critical = data.findings().stream()
                .filter(f -> f.severity().name().equals("CRITICAL")).count();
        long high = data.findings().stream()
                .filter(f -> f.severity().name().equals("HIGH")).count();

        boolean coverageIsLow = data.coverage().coveragePercent() < 10;

        StringBuilder tiles = new StringBuilder("<section id=\"stats\"><div class=\"panel\"><div class=\"stat-grid\">");
        tiles.append(tile("Actions Executed", String.valueOf(data.actionsExecuted()), false));
        tiles.append(tile("Pages Visited", String.valueOf(data.visitedPages().size()), false));
        tiles.append(tile("States Discovered", String.valueOf(data.states().size()), false));
        tiles.append(tile("Transitions", String.valueOf(data.edges().size()), false));
        tiles.append(tile("Element Coverage",
                data.coverage().elementsInteracted() + "/" + data.coverage().elementsDiscovered()
                        + " (" + String.format("%.0f%%", data.coverage().coveragePercent()) + ")", coverageIsLow));
        tiles.append(tile("Avg Confidence", String.format("%.2f", data.averageConfidence()), false));
        tiles.append(tile("Bug Count", data.findings().size()
                + (critical + high > 0 ? " (" + (critical + high) + " critical/high)" : ""), critical + high > 0));
        tiles.append(tile("Clusters", String.valueOf(data.bugClusters().size()), false));
        tiles.append(tile("Duration", formatDuration(data.duration()), false));
        tiles.append("</div></div></section>");

        return tiles.toString();
    }

    private String tile(String label, String value, boolean flagged) {
        return "<div class=\"stat\"><div class=\"stat-value" + (flagged ? " bad" : "") + "\">" + escape(value)
                + "</div><div class=\"stat-label\">" + escape(label) + "</div></div>";
    }

    private String renderWorldModelGraph(MissionReportData data) {

        StringBuilder section = new StringBuilder("<section id=\"world-model\"><h2 class=\"section-title\">World Model</h2>"
                + "<p class=\"caption\">The path AEGIS actually took through the app, by name — switch to "
                + "Graph for the fuller technical map of every state and transition discovered.</p>");

        if (data.states().isEmpty()) {
            section.append("<p class=\"empty\">No states observed.</p></section>");
            return section.toString();
        }

        section.append("<div class=\"chip-row\">"
                + "<button data-view=\"path\" class=\"chip active\">Path</button>"
                + "<button data-view=\"graph\" class=\"chip\">Graph</button>"
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

    private String renderPathView(MissionReportData data) {

        KnowledgeBase knowledgeBase = data.knowledgeBase();
        Optional<JourneyCatalog> journeyCatalog = knowledgeBase.get(JourneyCatalog.class);
        List<Journey> observed = journeyCatalog.map(JourneyCatalog::observed).orElse(List.of());

        if (observed.isEmpty()) {
            return "<p class=\"empty\">No named path available for this run.</p>";
        }

        Journey journey = observed.get(0);

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
        StringBuilder path = new StringBuilder("<div class=\"panel\"><div class=\"path-flow\">");

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

        path.append("</div></div>");

        return path.toString();
    }

    private String renderPathStep(MissionReportData data, Node node, List<String> matchedJourneyNames) {

        String signature = signatureForNode(data.knowledgeBase(), node);
        long visits = signature != null ? data.stateVisitCounts().getOrDefault(signature, 1L) : 1L;

        List<NavigationEdge> actionsHere = signature != null
                ? data.edges().stream().filter(edge -> edge.fromState().equals(signature)).toList()
                : List.of();

        StringBuilder step = new StringBuilder("<details class=\"tech-expander path-step\"><summary>");
        step.append("<span class=\"path-node-name\">").append(escape(node.displayName())).append("</span>");

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
     * Same layered (BFS-depth) top-to-bottom layout as before — the fix is
     * how much horizontal room each node gets. The original used a fixed
     * {@code nodeSpacing} constant regardless of what was actually
     * rendered; this instead gives every node a "footprint" — half its own
     * circle radius or half its label's estimated rendered width,
     * whichever is bigger — and packs same-layer nodes edge-to-edge with a
     * fixed minimum gap between footprints, so spacing always adapts to
     * content instead of silently overlapping long names or crowded
     * layers.
     */
    private String renderGraphView(MissionReportData data) {

        List<String> states = data.states();
        Map<String, Integer> depth = computeLayeredDepth(states, data.edges());

        Map<Integer, List<String>> statesByLayer = new LinkedHashMap<>();
        for (String state : states) {
            statesByLayer.computeIfAbsent(depth.get(state), layer -> new ArrayList<>()).add(state);
        }

        Map<String, Double> nodeRadii = new LinkedHashMap<>();
        Map<String, String> nodeLabels = new LinkedHashMap<>();
        Map<String, Double> footprintHalfWidth = new LinkedHashMap<>();

        for (String state : states) {

            long visits = data.stateVisitCounts().getOrDefault(state, 1L);
            double radius = nodeRadius(visits);
            String label = shortenLabel(nodeForSignature(data.knowledgeBase(), state)
                    .map(Node::displayName).orElse(state));

            nodeRadii.put(state, radius);
            nodeLabels.put(state, label);
            footprintHalfWidth.put(state, Math.max(radius, estimateTextWidth(label) / 2.0 + 6));
        }

        double layerHeight = 130;
        double minGap = 28;
        double topMargin = 60;

        Map<String, double[]> positions = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<String>> layer : statesByLayer.entrySet()) {

            List<String> layerStates = layer.getValue();
            double cursor = 0;
            double[] layerX = new double[layerStates.size()];

            for (int i = 0; i < layerStates.size(); i++) {
                double half = footprintHalfWidth.get(layerStates.get(i));
                cursor += half;
                layerX[i] = cursor;
                cursor += half + minGap;
            }

            double layerWidth = layerStates.isEmpty() ? 0 : cursor - minGap;
            double shift = 340 - layerWidth / 2.0;

            for (int i = 0; i < layerStates.size(); i++) {
                positions.put(layerStates.get(i), new double[]{
                        layerX[i] + shift,
                        topMargin + layer.getKey() * layerHeight
                });
            }
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
            double half = footprintHalfWidth.get(entry.getKey());
            extend(bounds, entry.getValue()[0] - half, entry.getValue()[1] - r);
            extend(bounds, entry.getValue()[0] + half, entry.getValue()[1] + r);
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
        svg.append("<div class=\"panel graph-fixed\">");
        svg.append("<svg class=\"g\" viewBox=\"")
                .append((int) minX).append(' ').append((int) minY).append(' ')
                .append((int) viewWidth).append(' ').append((int) viewHeight)
                .append("\" width=\"").append((int) svgWidth)
                .append("\" height=\"").append((int) svgHeight)
                .append("\">");

        svg.append("<defs><marker id=\"gm-arrow\" viewBox=\"0 0 10 10\" refX=\"9\" refY=\"5\" "
                + "markerWidth=\"6\" markerHeight=\"6\" orient=\"auto-start-reverse\">"
                + "<path d=\"M0,0 L10,5 L0,10 z\" class=\"arrowhead\"/></marker></defs>");

        svg.append(edgesSvg);

        int index = 1;
        for (String state : states) {
            long visits = data.stateVisitCounts().getOrDefault(state, 1L);
            boolean onPath = pathSignatures.contains(state);
            svg.append(renderGraphNode(nodeLabels.get(state), state, positions.get(state), nodeRadii.get(state),
                    visits, index++, selfLoopCountByState.getOrDefault(state, 0L), onPath));
        }

        svg.append("</svg>");
        svg.append("<p class=\"graph-caption\">Node spacing and edge-label placement are sized from each label's "
                + "actual rendered width, not a fixed constant — long names or crowded layers push neighbors "
                + "apart instead of overlapping them.</p>");
        svg.append("</div>");

        return svg.toString();
    }

    /**
     * A plain-text width estimate (no real font metrics available at HTML
     * generation time) — good enough for layout purposes at the node-label
     * font size/weight used here. Long labels are still truncated (see
     * {@link #shortenLabel}) so this never has to budget for arbitrarily
     * long text.
     */
    private double estimateTextWidth(String text) {
        return text.length() * 6.6;
    }

    private String shortenLabel(String displayName) {
        return displayName.length() > 20 ? displayName.substring(0, 18) + "…" : displayName;
    }

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

        int fallbackDepth = depth.values().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        for (String state : states) {
            depth.putIfAbsent(state, fallbackDepth);
        }

        return depth;
    }

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

    private String signatureForNode(KnowledgeBase knowledgeBase, Node node) {
        return knowledgeBase.get(StateCatalog.class)
                .flatMap(catalog -> catalog.byId(node.stateId()))
                .map(State::stateSignature)
                .orElse(null);
    }

    private String renderNodeDictionary(KnowledgeBase knowledgeBase) {

        List<Node> nodes = knowledgeBase.get(NodeCatalog.class).map(NodeCatalog::nodes).orElse(List.of());

        if (nodes.isEmpty()) {
            return "";
        }

        StringBuilder table = new StringBuilder(
                "<h3 class=\"sub\">Node Dictionary</h3><div class=\"panel\"><table><thead><tr><th>ID</th><th>Name</th><th>Source</th></tr></thead><tbody>");

        for (Node node : nodes) {
            table.append("<tr><td>").append(escape(node.stateId())).append("</td>")
                    .append("<td>").append(escape(node.displayName())).append("</td>")
                    .append("<td><span class=\"name-source ").append(node.nameSource().name().toLowerCase()).append("\">")
                    .append(node.nameSource() == NameSource.CONFIGURED ? "configured" : "auto")
                    .append("</span></td></tr>");
        }

        table.append("</tbody></table></div>");

        return table.toString();
    }

    private String renderFlowsAndJourneys(KnowledgeBase knowledgeBase) {

        List<Flow> flows = knowledgeBase.get(FlowCatalog.class).map(FlowCatalog::flows).orElse(List.of());
        JourneyCatalog journeyCatalog = knowledgeBase.get(JourneyCatalog.class).orElse(null);
        List<JourneyDefinition> journeys = journeyCatalog == null ? List.of() : journeyCatalog.definitions();

        if (flows.isEmpty() && journeys.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder();

        if (!flows.isEmpty()) {
            out.append("<h3 class=\"sub\">Flows</h3><ul class=\"flow-list\">");
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

            out.append("<h3 class=\"sub\">Journeys</h3><ul class=\"flow-list\">");
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
                        ? " <span class=\"verdict pass\">followed this run</span>"
                        : " <span class=\"meta\">(not followed this run)</span>");
                out.append("</li>");
            }
            out.append("</ul>");
        }

        return out.toString();
    }

    private String renderUxQuality(KnowledgeBase knowledgeBase) {

        List<UxFinding> findings = knowledgeBase.get(UxFindingCatalog.class)
                .map(UxFindingCatalog::findings)
                .orElse(List.of());

        if (findings.isEmpty()) {
            return "";
        }

        StringBuilder table = new StringBuilder(
                "<h3 class=\"sub\">UX Quality</h3><div class=\"panel\"><table><thead><tr><th>Type</th><th>Severity</th><th>Summary</th><th>Evidence</th></tr></thead><tbody>");

        for (UxFinding finding : findings) {
            table.append("<tr><td><strong>").append(escape(PlainLanguageGlossary.uxFindingLabel(finding.type()))).append("</strong>")
                    .append("<div class=\"meaning\">").append(escape(PlainLanguageGlossary.uxFindingExplanation(finding.type()))).append("</div>")
                    .append("<code class=\"tech-id\">").append(escape(finding.type().name())).append("</code></td>")
                    .append("<td><span class=\"sev-chip ").append(finding.severity().name().toLowerCase())
                    .append("\" title=\"").append(escape(PlainLanguageGlossary.severityWhyItMatters(finding.severity()))).append("\">")
                    .append(escape(PlainLanguageGlossary.severityLabel(finding.severity()))).append("</span></td>")
                    .append("<td>").append(escape(finding.summary())).append("</td>")
                    .append("<td><code class=\"ev\">").append(finding.evidence().isBlank() ? "&mdash;" : "Element: " + escape(finding.evidence()))
                    .append("</code></td></tr>");
        }

        table.append("</tbody></table></div>");

        return table.toString();
    }

    private String renderPageInspection(KnowledgeBase knowledgeBase) {

        List<InspectionFinding> findings = knowledgeBase.get(InspectionCatalog.class)
                .map(InspectionCatalog::findings)
                .orElse(List.of());

        if (findings.isEmpty()) {
            return "";
        }

        StringBuilder table = new StringBuilder(
                "<h3 class=\"sub\">Page Inspection</h3><div class=\"panel\"><table><thead><tr><th>Type</th><th>Severity</th><th>Summary</th><th>Evidence</th></tr></thead><tbody>");

        for (InspectionFinding finding : findings) {
            table.append("<tr><td><strong>").append(escape(PlainLanguageGlossary.inspectionCheckLabel(finding.type()))).append("</strong>")
                    .append("<div class=\"meaning\">").append(escape(PlainLanguageGlossary.inspectionCheckExplanation(finding.type()))).append("</div>")
                    .append("<code class=\"tech-id\">").append(escape(finding.type().name())).append("</code></td>")
                    .append("<td><span class=\"sev-chip ").append(finding.severity().name().toLowerCase())
                    .append("\" title=\"").append(escape(PlainLanguageGlossary.severityWhyItMatters(finding.severity()))).append("\">")
                    .append(escape(PlainLanguageGlossary.severityLabel(finding.severity()))).append("</span></td>")
                    .append("<td>").append(escape(finding.summary())).append("</td>")
                    .append("<td><code class=\"ev\">").append(finding.evidence().isBlank() ? "&mdash;" : "Element: " + escape(finding.evidence()))
                    .append("</code></td></tr>");
        }

        table.append("</tbody></table></div>");

        return table.toString();
    }

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

    private double[][] nonLoopEdgeGeometry(NavigationEdge edge, Map<String, double[]> positions, int parallelIndex) {

        double[] from = positions.get(edge.fromState());
        double[] to = positions.get(edge.toState());

        double curve = parallelIndex * 22;
        double midX = (from[0] + to[0]) / 2 - (to[1] - from[1]) * 0.001 * curve * 10;
        double midY = (from[1] + to[1]) / 2 + (to[0] - from[0]) * 0.001 * curve * 10;

        return new double[][]{{from[0], from[1]}, {midX, midY}, {to[0], to[1]}};
    }

    private String renderGraphNode(
            String label, String state, double[] pos, double radius, long visits, int index,
            long selfLoopCount, boolean onPath) {

        double heatOpacity = nodeHeatOpacity(visits);

        StringBuilder node = new StringBuilder();
        node.append("<g class=\"node").append(onPath ? " on-path" : "").append("\" data-state=\"")
                .append(escapeAttr(state)).append("\">");

        node.append(String.format(
                "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"%.1f\" class=\"node-circle\" style=\"fill-opacity: %.2f\"/>",
                pos[0], pos[1], radius, 0.25 + heatOpacity));

        node.append(String.format(
                "<text x=\"%.1f\" y=\"%.1f\" class=\"node-label\">%s</text>",
                pos[0], pos[1] + 5, escape(label)));

        if (selfLoopCount > 0) {
            node.append(String.format(
                    "<text x=\"%.1f\" y=\"%.1f\" class=\"node-sub\">×%d visits</text>",
                    pos[0], pos[1] - radius - 10, selfLoopCount));
        }

        node.append(String.format(
                "<title>%s (S%d, visited %d time%s%s)</title>",
                escape(state), index, visits, visits == 1 ? "" : "s",
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

        // A background rect sized to the label's estimated width sits
        // behind the text so it stays legible even where an edge crosses
        // close to a node or another edge — a cheap mitigation for the
        // cases the content-aware spacing above doesn't fully separate.
        double labelWidth = estimateTextWidth(label) + 8;

        return "<g class=\"edge" + (onPath ? " on-path" : "") + "\" data-from=\"" + escapeAttr(edge.fromState())
                + "\" data-to=\"" + escapeAttr(edge.toState()) + "\">"
                + "<path d=\"" + path + "\" class=\"edge-line\" "
                + "style=\"stroke-width: " + String.format("%.1f", strokeWidth) + "\" "
                + "marker-end=\"url(#gm-arrow)\"/>"
                + String.format("<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"14\" class=\"edge-label-bg\"/>",
                        mid[0] - labelWidth / 2, mid[1] - 11, labelWidth)
                + "<text x=\"" + mid[0] + "\" y=\"" + mid[1] + "\" class=\"edge-label\">" + label + "</text>"
                + "</g>";
    }

    private String renderPageCoverage(MissionReportData data) {

        StringBuilder section = new StringBuilder("<section id=\"coverage\"><h2 class=\"section-title\">Coverage</h2>"
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

        section.append("<div class=\"panel\"><table><thead><tr><th>Page</th><th>Elements Covered</th><th></th></tr></thead><tbody>");

        for (PageCoverage page : data.pageCoverage()) {

            section.append("<tr><td>").append(escape(page.url())).append("</td>")
                    .append("<td>").append(page.elementsInteracted()).append("/")
                    .append(page.elementsDiscovered()).append("</td>")
                    .append("<td><div class=\"bar\"><div class=\"bar-fill\" style=\"width:")
                    .append((int) page.coveragePercent()).append("%\"></div></div> ")
                    .append(String.format("%.0f%%", page.coveragePercent())).append("</td>")
                    .append("</tr>");
        }

        section.append("</tbody></table></div></section>");

        return section.toString();
    }

    private String renderLearning(MissionReportData data) {

        LearningSummary summary = data.learningSummary();

        StringBuilder section = new StringBuilder("<section id=\"learning\"><h2 class=\"section-title\">Learning</h2>");

        long confirmedReliable = LearningHonestyGlossary.confirmedReliableCount(summary.actionPerformance());
        long confirmedUnreliable = LearningHonestyGlossary.confirmedUnreliableCount(summary.actionPerformance());

        section.append("<div class=\"panel\"><div class=\"stat-grid\">")
                .append(tile("New Things Learned", String.valueOf(summary.newExperiences()), false))
                .append(tile("Tried More Than Once", String.valueOf(summary.updatedActions()), false))
                .append(tile("Confirmed Reliable", String.valueOf(confirmedReliable), false))
                .append(tile("Confirmed Unreliable", String.valueOf(confirmedUnreliable), confirmedUnreliable > 0))
                .append("</div>");

        if (summary.updatedActions() == 0 && !summary.actionPerformance().isEmpty()) {
            section.append("<p class=\"help\" style=\"padding:0 22px 18px\">Every action this run was a first attempt "
                    + "&mdash; nothing was repeated enough yet to say what's actually more or less reliable.</p>");
        }

        section.append("</div>");

        if (summary.actionPerformance().isEmpty()) {
            section.append("<p class=\"empty\">No experience recorded this mission.</p></section>");
            return section.toString();
        }

        section.append("<p class=\"caption\">").append(escape(LearningHonestyGlossary.honestLearningLine(summary))).append("</p>");

        List<PatternStatistics> actionPerformance = summary.actionPerformance();
        String table = "<div class=\"panel\">" + performanceTable(actionPerformance) + "</div>";

        if (actionPerformance.size() > 15) {
            section.append("<details open class=\"tech-expander\"><summary>")
                    .append(actionPerformance.size()).append(" actions tracked this run</summary>")
                    .append(table).append("</details>");
        } else {
            section.append(table);
        }

        section.append("</section>");

        return section.toString();
    }

    private String performanceTable(List<PatternStatistics> stats) {

        StringBuilder table = new StringBuilder(
                "<table><thead><tr><th>Action</th><th>Success Rate</th><th>Runs</th><th>Status</th></tr></thead><tbody>");

        for (PatternStatistics stat : stats) {

            table.append("<tr><td>").append(escape(PlainLanguageGlossary.actionVerb(stat.action().type()) + " " + stat.action().target())).append("</td>")
                    .append("<td><div class=\"bar\"><div class=\"bar-fill\" style=\"width:")
                    .append((int) (stat.successRate() * 100)).append("%\"></div></div> ")
                    .append(String.format("%.0f%%", stat.successRate() * 100)).append("</td>")
                    .append("<td>").append(stat.successfulExecutions()).append("/").append(stat.totalExecutions())
                    .append("</td>")
                    .append("<td>").append(escape(LearningHonestyGlossary.statusLabel(stat))).append("</td>")
                    .append("</tr>");
        }

        table.append("</tbody></table>");

        return table.toString();
    }

    private String renderBugClustersConsolidated(MissionReportData data) {

        StringBuilder section = new StringBuilder("<section id=\"bug-clusters\"><h2 class=\"section-title\">Findings</h2>"
                + "<p class=\"caption\">Grouped by a normalized fingerprint — "
                + "recurring or cross-page clusters are stronger signals than any single occurrence.</p>");

        if (data.bugClusters().isEmpty()) {
            section.append("<p class=\"empty\">No findings to cluster.</p></section>");
            return section.toString();
        }

        Map<String, List<Finding>> occurrencesByFingerprint = data.findings().stream()
                .collect(Collectors.groupingBy(BugFingerprint::of, LinkedHashMap::new, Collectors.toList()));

        section.append("<div class=\"chip-row\">"
                + "<button data-group=\"category\" class=\"chip active\">By Category</button>"
                + "<button data-group=\"flat\" class=\"chip\">Most Severe First</button>"
                + "</div>");

        section.append("<div class=\"cluster-view\" data-group-view=\"category\">");
        for (Map.Entry<FindingCategory, List<BugCluster>> entry : data.findingsByCategory().entrySet()) {
            section.append("<h3 class=\"sub\">").append(escape(PlainLanguageGlossary.categoryLabel(entry.getKey())))
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

        row.append("<div class=\"panel panel-stripe ").append(cluster.severity().name().toLowerCase()).append("\">")
                .append("<div class=\"panel-body\">")
                .append("<span class=\"sev-chip ").append(cluster.severity().name().toLowerCase())
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

            row.append("<details class=\"tech-expander\"><summary>Show ")
                    .append(occurrences.size()).append(" individual occurrences</summary>");

            for (Finding finding : occurrences) {
                row.append("<div class=\"occurrence\">").append(escape(finding.summary()))
                        .append("<div class=\"meta\">").append(escape(finding.url()))
                        .append(" — ").append(finding.detectedAt()).append("</div></div>");
            }

            row.append("</details>");
        }

        row.append("</div></div>");

        return row.toString();
    }

    private static final Pattern FRAME_TARGET_PREFIX = Pattern.compile("^frame:(\\d+)>(.*)$", Pattern.DOTALL);

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

        StringBuilder section = new StringBuilder("<section id=\"reasoning-steps\"><h2 class=\"section-title\">Reasoning Steps</h2><div class=\"panel\">");

        if (data.reasoningSteps().isEmpty()) {
            section.append("<div class=\"panel-body\"><p class=\"empty\">No reasoning steps recorded.</p></div></div></section>");
            return section.toString();
        }

        for (ReasoningStep step : data.reasoningSteps()) {

            CandidateAction selected = step.selected();
            double margin = data.runnerUpMargin(step);
            String marginText = Double.isNaN(margin)
                    ? "only candidate"
                    : String.format("+%.2f over %d alternative%s", margin, step.candidates().size() - 1,
                            step.candidates().size() - 1 == 1 ? "" : "s");

            boolean stepWasLearned = LearningAdjustmentGlossary.isGenuineLearnedAdjustment(selected.reasoning());
            String selectedValue = selected.action().value();

            section.append("<details class=\"tech-expander step-detail\">")
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
                boolean candidateWasLearned = LearningAdjustmentGlossary.isGenuineLearnedAdjustment(candidate.reasoning());
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
                        .append("<td>").append(escape(LearningAdjustmentGlossary.honestReasoning(candidate.reasoning()))).append("</td>")
                        .append("</tr>");
            }

            section.append("</tbody></table></div></details>");
        }

        section.append("</div></section>");

        return section.toString();
    }

    private String css() {
        return """
                @font-face { font-family: 'Plex Sans'; src: local('IBM Plex Sans'), local('IBMPlexSans'); }
                @font-face { font-family: 'Plex Mono'; src: local('IBM Plex Mono'), local('IBMPlexMono'); }
                :root {
                    color-scheme: light dark;
                    --ink: #14181f; --ink-soft: #3b414c; --muted: #6b7280; --faint: #9aa1ab;
                    --paper: #ffffff; --surface: #f6f7f9; --surface-raised: #ffffff; --line: #e3e6ea;
                    --accent: #3a6ea5; --accent-soft: #e9f0f8;
                    --good: #1f8a5f; --good-soft: #e7f5ee;
                    --warn: #b5760a; --warn-soft: #fbf1e2;
                    --bad: #c1392b; --bad-soft: #fbeae8;
                    --radius: 10px;
                    --mono: 'Plex Mono', ui-monospace, 'SFMono-Regular', Menlo, monospace;
                    --sans: 'Plex Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                }
                @media (prefers-color-scheme: dark) {
                    :root:not([data-theme="light"]) {
                        --ink: #eef0f3; --ink-soft: #c7ccd4; --muted: #9aa1ab; --faint: #6b7280;
                        --paper: #14161b; --surface: #1a1d23; --surface-raised: #20232a; --line: #2b2f37;
                        --accent: #7fb0e0; --accent-soft: #1c2b3a;
                        --good: #4fbf8f; --good-soft: #16281f;
                        --warn: #e0a748; --warn-soft: #2c2113;
                        --bad: #e8756a; --bad-soft: #2e1917;
                    }
                }
                :root[data-theme="dark"] {
                    --ink: #eef0f3; --ink-soft: #c7ccd4; --muted: #9aa1ab; --faint: #6b7280;
                    --paper: #14161b; --surface: #1a1d23; --surface-raised: #20232a; --line: #2b2f37;
                    --accent: #7fb0e0; --accent-soft: #1c2b3a;
                    --good: #4fbf8f; --good-soft: #16281f;
                    --warn: #e0a748; --warn-soft: #2c2113;
                    --bad: #e8756a; --bad-soft: #2e1917;
                }
                * { box-sizing: border-box; }
                body { margin: 0; background: var(--surface); color: var(--ink); font-family: var(--sans);
                    font-size: 15px; line-height: 1.55; }
                .wrap { max-width: 900px; margin: 0 auto; padding: 0 24px; }
                .eyebrow { font-family: var(--mono); font-size: 11px; letter-spacing: 0.08em;
                    text-transform: uppercase; color: var(--muted); margin: 0 0 4px; }
                header.masthead { display: flex; justify-content: space-between; align-items: flex-start;
                    gap: 16px; padding: 32px 0 20px; }
                h1 { font-size: 24px; margin: 0; font-weight: 650; letter-spacing: -0.01em; }
                .verdict { display: inline-flex; align-items: center; gap: 6px; font-family: var(--mono);
                    font-size: 12px; font-weight: 600; letter-spacing: 0.03em; text-transform: uppercase;
                    padding: 7px 12px; border-radius: 999px; white-space: nowrap; }
                .verdict.fail { background: var(--bad-soft); color: var(--bad); }
                .verdict.pass { background: var(--good-soft); color: var(--good); }
                .verdict::before { content: ''; width: 7px; height: 7px; border-radius: 999px; background: currentColor; }
                .tabs { position: sticky; top: 0; z-index: 10; display: flex; flex-wrap: wrap; gap: 4px;
                    background: var(--surface); padding: 0 0 14px; margin-bottom: 4px; border-bottom: 1px solid var(--line); }
                .tab-btn { font: inherit; font-size: 13px; font-weight: 600; padding: 9px 14px;
                    border-radius: 8px 8px 0 0; border: none; background: none; color: var(--muted); cursor: pointer; }
                .tab-btn:hover { color: var(--ink); }
                .tab-btn.active { color: var(--accent); background: var(--accent-soft); }
                .tab-panel.active { display: block; }
                h2.section-title { font-size: 13px; font-weight: 650; letter-spacing: 0.03em;
                    text-transform: uppercase; color: var(--muted); margin: 30px 0 12px; }
                h3.sub { font-size: 12.5px; font-weight: 650; letter-spacing: 0.03em; text-transform: uppercase;
                    color: var(--muted); margin: 20px 0 10px; }
                .panel { background: var(--surface-raised); border: 1px solid var(--line); border-radius: var(--radius);
                    margin-bottom: 12px; overflow: hidden; }
                .panel-stripe { border-left: 4px solid var(--line); }
                .panel-stripe.bad { border-left-color: var(--bad); }
                .panel-stripe.warn, .panel-stripe.high { border-left-color: var(--warn); }
                .panel-stripe.good { border-left-color: var(--good); }
                .panel-stripe.accent { border-left-color: var(--accent); }
                .panel-stripe.critical { border-left-color: var(--bad); }
                .panel-stripe.medium { border-left-color: var(--faint); }
                .panel-stripe.low { border-left-color: var(--faint); }
                .panel-body { padding: 18px 22px; }
                .panel-label { font-family: var(--mono); font-size: 10.5px; letter-spacing: 0.08em;
                    text-transform: uppercase; color: var(--faint); margin: 0 0 10px; }
                .summary-text { font-size: 16.5px; line-height: 1.6; color: var(--ink-soft); margin: 0; }
                .rec { display: flex; gap: 12px; align-items: flex-start; }
                .rec-icon { font-family: var(--mono); font-size: 11px; font-weight: 700; color: var(--accent);
                    background: var(--accent-soft); border-radius: 6px; padding: 3px 7px; flex: none; margin-top: 2px;
                    text-transform: uppercase; letter-spacing: 0.04em; }
                .rec-text { color: var(--ink-soft); margin: 0; }
                .goal, .outcome { margin: 0 0 8px; }
                .outcome { color: var(--muted); }
                .exec-facts { display: flex; flex-wrap: wrap; gap: 20px; margin: 14px 0 0; padding-top: 14px;
                    border-top: 1px solid var(--line); }
                .exec-facts div { min-width: 90px; }
                .exec-facts dt { font-size: 11px; color: var(--muted); margin: 0; }
                .exec-facts dd { font-family: var(--mono); font-size: 16px; font-weight: 700; margin: 2px 0 0; }
                .stat-grid { display: flex; flex-wrap: wrap; background: var(--surface-raised); }
                .stat { flex: 1 1 110px; background: var(--surface-raised); padding: 14px 16px;
                    border-right: 1px solid var(--line); border-bottom: 1px solid var(--line); }
                .stat-value { font-family: var(--mono); font-size: 20px; font-weight: 600; font-variant-numeric: tabular-nums; }
                .stat-value.bad { color: var(--bad); }
                .stat-label { font-size: 11.5px; color: var(--muted); margin-top: 2px; }
                .caption { color: var(--faint); font-size: 12.5px; margin: -4px 0 12px; }
                .plan { margin: 0; padding-left: 20px; }
                .plan li { margin-bottom: 6px; }
                .empty { color: var(--faint); font-style: italic; }
                .page-checklist { list-style: none; padding: 0; margin: 0 0 12px; font-size: 13px; }
                .page-checklist li { padding: 2px 0; color: var(--good); }
                .performance-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-top: 12px; padding: 0 22px 18px; }
                @media (max-width: 640px) { .performance-columns { grid-template-columns: 1fr; } }
                svg.g { display: block; margin: 0 auto; max-width: 100%; height: auto; overflow: visible; }
                svg.g .node-circle { fill: var(--accent); stroke: var(--accent); stroke-width: 2; }
                svg.g .node-label { font-family: var(--sans); font-size: 12px; font-weight: 600; fill: var(--ink); text-anchor: middle; }
                svg.g .node-sub { font-family: var(--mono); font-size: 9.5px; fill: var(--faint); text-anchor: middle; }
                svg.g .node.on-path .node-circle { stroke-width: 3.5; }
                svg.g .edge-line { fill: none; stroke: var(--faint); stroke-width: 1.5; }
                svg.g .edge-label { font-family: var(--mono); font-size: 10px; fill: var(--muted); text-anchor: middle; }
                svg.g .edge-label-bg { fill: var(--surface-raised); }
                svg.g .edge.on-path .edge-line { stroke: var(--accent); }
                svg.g .edge.on-path .edge-label { fill: var(--accent); font-weight: 600; }
                svg.g .node.dim, svg.g .edge.dim { opacity: .15; }
                .graph-fixed { padding: 20px; }
                .graph-caption { font-size: 12px; color: var(--faint); margin: 10px 0 0; text-align: center; }
                .chip-row { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 14px; }
                .chip { font: inherit; font-size: 12px; font-weight: 600; padding: 6px 12px; border-radius: 999px;
                    border: 1px solid var(--line); background: var(--surface-raised); color: var(--muted); cursor: pointer; }
                .chip.active { background: var(--accent); border-color: var(--accent); color: #fff; }
                .path-flow { display: flex; flex-wrap: wrap; align-items: flex-start; gap: 6px; padding: 18px; }
                .path-arrow { color: var(--faint); font-size: 15px; align-self: center; }
                .path-step { background: var(--surface); border: 1px solid var(--line); border-radius: 8px; padding: 10px 14px; min-width: 150px; }
                .path-node-name { font-weight: 600; font-size: 14px; }
                .path-step > summary { cursor: pointer; list-style: none; }
                .path-step > summary::-webkit-details-marker { display: none; }
                .visit-badge { font-family: var(--mono); font-size: 10.5px; font-weight: 700; padding: 1px 7px;
                    border-radius: 999px; background: var(--surface-raised); color: var(--muted); border: 1px solid var(--line); }
                .journey-tag { font-size: 10.5px; font-weight: 700; padding: 1px 8px; border-radius: 999px;
                    background: var(--good-soft); color: var(--good); }
                .path-step-detail { margin-top: 8px; font-size: 12.5px; color: var(--muted); }
                .path-step-detail ul { margin: 0 0 6px; padding-left: 18px; }
                .sev-chip { font-family: var(--mono); font-size: 10px; font-weight: 700; letter-spacing: 0.04em;
                    text-transform: uppercase; padding: 2px 7px; border-radius: 5px; display: inline-block; }
                .sev-chip.critical { background: var(--bad-soft); color: var(--bad); }
                .sev-chip.high { background: var(--warn-soft); color: var(--warn); }
                .sev-chip.medium { background: var(--surface); color: var(--muted); border: 1px solid var(--line); }
                .sev-chip.low { background: var(--surface); color: var(--faint); border: 1px solid var(--line); }
                .summary { font-weight: 600; }
                .meaning { font-size: 13px; margin-top: 6px; color: var(--muted); }
                .meta { color: var(--faint); font-size: 12px; margin-top: 4px; }
                .tech-id { font-family: var(--mono); font-size: 11px; color: var(--muted); background: var(--surface);
                    border: 1px solid var(--line); border-radius: 4px; padding: 1px 6px; display: inline-block; margin-top: 6px; }
                .ev { font-family: var(--mono); font-size: 12px; background: var(--surface); border: 1px solid var(--line);
                    padding: 1px 6px; border-radius: 4px; color: var(--ink-soft); word-break: break-all; }
                .occurrence { padding: 8px 0; border-top: 1px solid var(--line); font-size: 12.5px; }
                .tech-expander { margin-top: 10px; }
                .tech-expander > summary { cursor: pointer; font-family: var(--mono); font-size: 12px; color: var(--accent);
                    font-weight: 600; list-style: none; }
                .tech-expander > summary::-webkit-details-marker { display: none; }
                .tech-expander > summary::before { content: '▸ '; }
                .tech-expander[open] > summary::before { content: '▾ '; }
                .step-detail { border-bottom: 1px solid var(--line); padding: 12px 22px; margin-top: 0; }
                .step-detail:last-child { border-bottom: none; }
                .step-detail > summary::before { content: ''; }
                .confidence { color: var(--muted); font-weight: 400; font-size: 12.5px; }
                .candidates { overflow-x: auto; margin-top: 10px; }
                table { border-collapse: collapse; width: 100%; font-size: 13px; }
                th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--line); }
                th { color: var(--muted); font-weight: 600; font-size: 11.5px; text-transform: uppercase; letter-spacing: 0.03em; }
                tr.selected { background: var(--accent-soft); font-weight: 600; }
                .bar { display: inline-block; width: 54px; height: 5px; background: var(--line); border-radius: 3px;
                    overflow: hidden; vertical-align: middle; margin-right: 6px; }
                .bar-fill { height: 100%; background: var(--accent); }
                .learned-badge, .winner-badge { display: inline-block; font-size: 10px; font-weight: 700;
                    padding: 1px 7px; border-radius: 999px; text-transform: uppercase; font-family: var(--mono); }
                .learned-badge { background: var(--accent-soft); color: var(--accent); }
                .winner-badge { background: var(--good-soft); color: var(--good); }
                .strategy-badge { display: inline-block; font-size: 10px; font-weight: 700; padding: 1px 7px;
                    border-radius: 999px; text-transform: uppercase; font-family: var(--mono); background: var(--surface);
                    color: var(--muted); border: 1px solid var(--line); }
                .value-tag { color: var(--muted); font-size: 12.5px; font-family: var(--mono); }
                .timeline { list-style: none; margin: 0; padding: 8px 20px; }
                .tl-row { display: flex; gap: 12px; padding: 7px 0; font-size: 12.5px; border-top: 1px solid var(--line); }
                .tl-row:first-child { border-top: none; }
                .tl-time { font-family: var(--mono); color: var(--faint); flex: none; width: 58px;
                    font-variant-numeric: tabular-nums; }
                .tl-dot { width: 6px; height: 6px; border-radius: 999px; background: var(--faint); margin-top: 6px; flex: none; }
                .tl-row.mission-started .tl-dot, .tl-row.mission-finished .tl-dot { background: var(--accent); }
                .tl-row.observation .tl-dot { background: #4a90c4; }
                .tl-row.execution .tl-dot { background: var(--good); }
                .tl-row.finding .tl-dot { background: var(--warn); }
                .tl-body { color: var(--ink-soft); flex: 1; }
                .tl-headline { font-weight: 600; }
                .tl-row.finding .tl-headline { color: var(--warn); }
                .tl-detail { color: var(--muted); margin-top: 2px; }
                .tl-screenshot { max-width: 220px; border: 1px solid var(--line); border-radius: 6px; margin-top: 6px; display: block; }
                .name-source { font-size: 10px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.03em;
                    padding: 1px 7px; border-radius: 999px; font-family: var(--mono); }
                .name-source.configured { background: var(--good-soft); color: var(--good); }
                .name-source.auto_title, .name-source.auto_url { background: var(--surface); color: var(--muted); border: 1px solid var(--line); }
                .flow-list { list-style: none; padding: 0; margin: 0 0 16px; font-size: 13px; }
                .flow-list li { padding: 8px 0; border-bottom: 1px solid var(--line); }
                .flow-list li:last-child { border-bottom: none; }
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

                document.querySelectorAll('.cluster-view').forEach(function () {});
                document.querySelectorAll('[data-group]').forEach(function (btn) {
                    btn.addEventListener('click', function () {
                        document.querySelectorAll('[data-group]').forEach(function (b) {
                            b.classList.remove('active');
                        });
                        btn.classList.add('active');
                        var group = btn.getAttribute('data-group');
                        document.querySelectorAll('.cluster-view').forEach(function (v) {
                            v.style.display = v.getAttribute('data-group-view') === group ? '' : 'none';
                        });
                    });
                });

                document.querySelectorAll('[data-view]').forEach(function (btn) {
                    btn.addEventListener('click', function () {
                        document.querySelectorAll('[data-view]').forEach(function (b) {
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

                document.querySelectorAll('[data-kind]').forEach(function (btn) {
                    btn.addEventListener('click', function () {
                        document.querySelectorAll('[data-kind]').forEach(function (b) {
                            b.classList.remove('active');
                        });
                        btn.classList.add('active');
                        var kind = btn.getAttribute('data-kind');
                        document.querySelectorAll('.tl-row').forEach(function (li) {
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
