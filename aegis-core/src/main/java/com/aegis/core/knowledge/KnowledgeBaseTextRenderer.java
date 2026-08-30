package com.aegis.core.knowledge;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders a {@link KnowledgeBase} as the "World Model Summary" text
 * block — a plain, standalone renderer a caller invokes explicitly, not
 * wired into the {@code ReportRenderer} SPI (that interface's signature
 * is tied to {@code MissionReportData}, not this layer).
 */
public final class KnowledgeBaseTextRenderer {

    private static final String RULE = "────────────────────────────────────";

    public String render(String applicationName, KnowledgeBase knowledgeBase) {

        StateCatalog stateCatalog = knowledgeBase.require(StateCatalog.class);
        NodeCatalog nodeCatalog = knowledgeBase.require(NodeCatalog.class);
        FlowCatalog flowCatalog = knowledgeBase.require(FlowCatalog.class);
        JourneyCatalog journeyCatalog = knowledgeBase.require(JourneyCatalog.class);
        UxFindingCatalog uxFindingCatalog = knowledgeBase.require(UxFindingCatalog.class);
        InspectionCatalog inspectionCatalog = knowledgeBase.require(InspectionCatalog.class);
        NavigationGraphCatalog navigationGraphCatalog = knowledgeBase.require(NavigationGraphCatalog.class);
        ExperienceScoreCatalog experienceScoreCatalog = knowledgeBase.require(ExperienceScoreCatalog.class);

        StringBuilder out = new StringBuilder();

        out.append("World Model Summary\n\n");
        out.append("Application: ").append(applicationName).append("\n\n");
        out.append("Discovered Screens: ").append(stateCatalog.discoveredScreenCount()).append('\n');
        out.append("Discovered Components: ").append(stateCatalog.distinctComponentCount()).append('\n');
        out.append("Business Flows: ").append(flowCatalog.flows().size()).append('\n');
        out.append('\n');

        out.append("Node Dictionary\n").append(RULE).append('\n');
        for (State state : stateCatalog.states()) {
            String displayName = nodeCatalog.byStateId(state.id()).map(Node::displayName).orElse("(unnamed)");
            out.append(String.format("%-3s → %s%n", state.id(), displayName));
        }
        out.append('\n');

        out.append("Flows\n").append(RULE).append('\n');
        if (flowCatalog.flows().isEmpty()) {
            out.append("(none declared)\n");
        }
        for (Flow flow : flowCatalog.flows()) {
            out.append(flow.name()).append(": ").append(renderNodeIds(nodeCatalog, flow.nodeKeys()));
            if (!flow.unmatchedNodeKeys().isEmpty()) {
                out.append(" (not yet discovered: ").append(String.join(", ", flow.unmatchedNodeKeys())).append(')');
            }
            out.append('\n');
        }
        out.append('\n');

        out.append("Journeys\n").append(RULE).append('\n');
        if (journeyCatalog.definitions().isEmpty()) {
            out.append("(none declared)\n");
        }
        for (JourneyDefinition definition : journeyCatalog.definitions()) {

            boolean observedThisRun = journeyCatalog.observed().stream()
                    .anyMatch(journey -> journey.matchedDefinitionKeys().contains(definition.key()));

            out.append(definition.name()).append(": ").append(renderNodeIds(nodeCatalog, definition.nodeKeys()));
            if (!definition.unmatchedNodeKeys().isEmpty()) {
                out.append(" (not yet discovered: ").append(String.join(", ", definition.unmatchedNodeKeys())).append(')');
            }
            out.append(observedThisRun ? " [followed this run]" : " [not followed this run]");
            out.append('\n');
        }
        out.append('\n');

        out.append("UX Quality\n").append(RULE).append('\n');
        if (uxFindingCatalog.findings().isEmpty()) {
            out.append("(no findings)\n");
        }
        for (UxFinding finding : uxFindingCatalog.findings()) {
            out.append(finding.type()).append(" [").append(finding.severity()).append("] ")
                    .append(finding.summary()).append(" (evidence: ").append(finding.evidence()).append(")\n");
        }
        out.append('\n');

        out.append("Page Inspection\n").append(RULE).append('\n');
        if (inspectionCatalog.findings().isEmpty()) {
            out.append("(no findings)\n");
        }
        for (InspectionFinding finding : inspectionCatalog.findings()) {
            out.append(finding.type()).append(" [").append(finding.severity()).append("] ")
                    .append(finding.summary()).append(" (evidence: ").append(finding.evidence()).append(")\n");
        }
        out.append('\n');

        out.append("Navigation Graph\n").append(RULE).append('\n');
        out.append("Transitions: ").append(navigationGraphCatalog.edges().size()).append('\n');
        for (NavigationGraphEdge edge : navigationGraphCatalog.edges()) {
            out.append("  ").append(edge.fromNodeKey()).append(" → ").append(edge.toNodeKey())
                    .append(" (x").append(edge.traversalCount()).append(")\n");
        }
        out.append("Loops walked: ").append(navigationGraphCatalog.loops().size()).append('\n');
        for (NavigationLoop loop : navigationGraphCatalog.loops()) {
            out.append("  ").append(String.join(" → ", loop.nodeKeySequence())).append('\n');
        }
        out.append("Dead ends: ");
        out.append(navigationGraphCatalog.deadEndNodeKeys().isEmpty()
                ? "(none)" : String.join(", ", navigationGraphCatalog.deadEndNodeKeys()));
        out.append('\n');
        out.append('\n');

        out.append("Experience Score\n").append(RULE).append('\n');
        out.append(experienceScoreCatalog.score()).append(" / 100");
        out.append(" (").append(experienceScoreCatalog.totalFindings()).append(" finding(s), ")
                .append(experienceScoreCatalog.frictionPoints()).append(" friction point(s))\n");

        // Opt-in (see TestIntelligenceCatalog's own Javadoc for why it's
        // not always present) — rendered only when a caller added it.
        knowledgeBase.get(TestIntelligenceCatalog.class).ifPresent(testIntelligence -> {
            out.append('\n');
            out.append("Test Intelligence\n").append(RULE).append('\n');
            for (String recommendation : testIntelligence.recommendations()) {
                out.append("- ").append(recommendation).append('\n');
            }
        });

        return out.toString();
    }

    private String renderNodeIds(NodeCatalog nodeCatalog, List<String> nodeKeys) {
        return nodeKeys.stream()
                .map(key -> nodeCatalog.byKey(key).map(Node::stateId).orElse(key))
                .collect(Collectors.joining(" → "));
    }
}
