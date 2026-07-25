package com.aegis.core.report;

import com.aegis.core.bug.BugCluster;
import com.aegis.core.bug.BugExplainer;
import com.aegis.core.bug.RecommendationEngine;
import com.aegis.core.bug.RuleBasedBugExplainer;
import com.aegis.core.bug.RuleBasedRecommendationEngine;
import com.aegis.core.mission.MissionPlan;
import com.aegis.core.mission.RuleBasedMissionPlanner;
import com.aegis.core.reasoning.learning.PatternStatistics;
import com.aegis.core.resilience.ScreenshotSample;
import com.aegis.model.context.MissionContext;
import com.aegis.model.experience.Experience;
import com.aegis.model.finding.Finding;
import com.aegis.model.mission.MissionStatus;
import com.aegis.model.reasoning.NavigationEdge;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/**
 * Reporting v2 Stage 3: the same MissionReportData every other report
 * format renders, as machine-readable JSON instead of prose — for
 * feeding a dashboard, a CI artifact, or any tool that wants structured
 * access without scraping the text/HTML reports. Built by hand with
 * Jackson's tree API (ObjectMapper.createObjectNode()), the same
 * low-level approach OpenAiCompatibleChatClient/LlmActionScorer/
 * LlmMissionParser already use elsewhere in this codebase, rather than
 * reflecting the MissionReportData record directly — that would leak
 * internal Java shapes (and need an extra jackson-datatype-jsr310
 * dependency for Instant/Duration) into what should be a stable,
 * intentionally-designed export schema.
 */
public class JsonReportGenerator {

    private final ObjectMapper mapper = new ObjectMapper();

    public String generate(MissionContext context, MissionStatus status) {
        return generate(context, status, new RuleBasedBugExplainer());
    }

    public String generate(MissionContext context, MissionStatus status, BugExplainer explainer) {
        return generate(context, status, explainer, new RuleBasedRecommendationEngine());
    }

    public String generate(
            MissionContext context, MissionStatus status, BugExplainer explainer, RecommendationEngine recommender) {
        return generate(context, status, explainer, recommender,
                new RuleBasedMissionPlanner().plan(context.getMission()));
    }

    public String generate(
            MissionContext context, MissionStatus status, BugExplainer explainer,
            RecommendationEngine recommender, MissionPlan plan) {
        return generate(context, status, explainer, recommender, plan, List.of());
    }

    public String generate(
            MissionContext context, MissionStatus status, BugExplainer explainer,
            RecommendationEngine recommender, MissionPlan plan, List<Experience> experiences) {
        return generate(context, status, explainer, recommender, plan, experiences, List.of());
    }

    /** Full export: everything above, plus every screenshot SelfHealingBrowser captured this run. */
    public String generate(
            MissionContext context, MissionStatus status, BugExplainer explainer,
            RecommendationEngine recommender, MissionPlan plan, List<Experience> experiences,
            List<ScreenshotSample> screenshots) {

        return generate(MissionReportData.from(context, status, explainer, recommender, plan, experiences, screenshots));
    }

    /** Stage 2: renders directly from an already-built {@link MissionReportData} — see ExplainabilityReportGenerator's javadoc on its own overload. */
    public String generate(MissionReportData data) {

        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(toJson(data));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize mission report to JSON", e);
        }
    }

    private ObjectNode toJson(MissionReportData data) {

        ObjectNode root = mapper.createObjectNode();

        ObjectNode mission = root.putObject("mission");
        mission.put("name", data.missionName());
        mission.put("goal", data.missionGoal());
        mission.put("status", data.status().toString());
        mission.put("durationMs", data.duration().toMillis());
        mission.put("actionsExecuted", data.actionsExecuted());
        mission.put("averageConfidence", data.averageConfidence());

        ObjectNode coverage = root.putObject("coverage");
        coverage.put("elementsDiscovered", data.coverage().elementsDiscovered());
        coverage.put("elementsInteracted", data.coverage().elementsInteracted());
        coverage.put("coveragePercent", data.coverage().coveragePercent());

        ArrayNode pages = coverage.putArray("pages");

        for (PageCoverage page : data.pageCoverage()) {
            ObjectNode pageNode = pages.addObject();
            pageNode.put("url", page.url());
            pageNode.put("elementsDiscovered", page.elementsDiscovered());
            pageNode.put("elementsInteracted", page.elementsInteracted());
            pageNode.put("coveragePercent", page.coveragePercent());
        }

        ArrayNode worldModel = root.putArray("worldModelEdges");

        for (NavigationEdge edge : data.edges()) {
            ObjectNode edgeNode = worldModel.addObject();
            edgeNode.put("actionType", edge.actionType().toString());
            edgeNode.put("actionTarget", edge.actionTarget());
            edgeNode.put("fromState", edge.fromState());
            edgeNode.put("toState", edge.toState());
        }

        root.put("recommendation", data.recommendation());

        ArrayNode planSteps = root.putArray("plan");
        data.plan().steps().forEach(planSteps::add);

        ArrayNode timeline = root.putArray("timeline");

        for (TimelineEvent event : data.timeline()) {
            ObjectNode eventNode = timeline.addObject();
            eventNode.put("timestamp", event.timestamp().toString());
            eventNode.put("kind", event.kind().toString());
            eventNode.put("headline", event.headline());
            eventNode.put("detail", event.detail());
            eventNode.put("screenshotDataUri", event.screenshotDataUri());
        }

        root.set("learning", learningToJson(data));
        root.set("bugClusters", bugClustersToJson(data));
        root.set("findingsByCategory", findingsByCategoryToJson(data));
        root.set("findings", findingsToJson(data));

        return root;
    }

    private ObjectNode learningToJson(MissionReportData data) {

        LearningSummary summary = data.learningSummary();

        ObjectNode learning = mapper.createObjectNode();
        learning.put("newExperiences", summary.newExperiences());
        learning.put("updatedActions", summary.updatedActions());
        learning.put("confidenceIncreased", summary.confidenceIncreased());
        learning.put("confidenceReduced", summary.confidenceReduced());

        ArrayNode actionPerformance = learning.putArray("actionPerformance");

        for (PatternStatistics stat : summary.actionPerformance()) {
            ObjectNode statNode = actionPerformance.addObject();
            statNode.put("actionType", stat.action().type().toString());
            statNode.put("actionTarget", stat.action().target());
            statNode.put("totalExecutions", stat.totalExecutions());
            statNode.put("successfulExecutions", stat.successfulExecutions());
            statNode.put("failedExecutions", stat.failedExecutions());
            statNode.put("successRate", stat.successRate());
        }

        return learning;
    }

    private ArrayNode bugClustersToJson(MissionReportData data) {

        ArrayNode clusters = mapper.createArrayNode();

        for (BugCluster cluster : data.bugClusters()) {

            ObjectNode clusterNode = clusters.addObject();
            clusterNode.put("fingerprint", cluster.fingerprint());
            clusterNode.put("severity", cluster.severity().toString());
            clusterNode.put("summary", cluster.representativeSummary());
            clusterNode.put("occurrenceCount", cluster.occurrenceCount());
            clusterNode.put("spansMultiplePages", cluster.spansMultiplePages());
            clusterNode.put("explanation", data.explanationFor(cluster));

            ArrayNode urls = clusterNode.putArray("urls");
            cluster.urls().forEach(urls::add);
        }

        return clusters;
    }

    private ObjectNode findingsByCategoryToJson(MissionReportData data) {

        ObjectNode byCategory = mapper.createObjectNode();

        for (Map.Entry<FindingCategory, List<BugCluster>> entry : data.findingsByCategory().entrySet()) {

            ArrayNode fingerprints = byCategory.putArray(entry.getKey().toString());

            entry.getValue().forEach(cluster -> fingerprints.add(cluster.fingerprint()));
        }

        return byCategory;
    }

    private ArrayNode findingsToJson(MissionReportData data) {

        ArrayNode findings = mapper.createArrayNode();

        for (Finding finding : data.rankedFindings()) {

            ObjectNode findingNode = findings.addObject();
            findingNode.put("severity", finding.severity().toString());
            findingNode.put("summary", finding.summary());
            findingNode.put("url", finding.url());
            findingNode.put("detectedAt", finding.detectedAt().toString());
            findingNode.put("explanation", data.explain(finding));
        }

        return findings;
    }
}
