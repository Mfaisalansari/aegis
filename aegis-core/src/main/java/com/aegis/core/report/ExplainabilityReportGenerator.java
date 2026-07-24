package com.aegis.core.report;

import com.aegis.core.bug.BugCluster;
import com.aegis.core.bug.BugExplainer;
import com.aegis.core.bug.RecommendationEngine;
import com.aegis.core.bug.RuleBasedBugExplainer;
import com.aegis.core.bug.RuleBasedRecommendationEngine;
import com.aegis.core.mission.MissionPlan;
import com.aegis.core.mission.RuleBasedMissionPlanner;
import com.aegis.model.context.MissionContext;
import com.aegis.model.finding.Finding;
import com.aegis.model.mission.MissionStatus;
import com.aegis.model.reasoning.NavigationEdge;
import com.aegis.model.reasoning.ReasoningStep;

/**
 * Renders a mission's outcome into a plain-text report for after-the-fact
 * review: what AEGIS was trying to do, what happened, and — per finding
 * and per reasoning step — why it matters, not just the raw log line.
 * Kept separate from the live console log so the run itself stays quiet.
 */
public class ExplainabilityReportGenerator {

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

        MissionReportData data = MissionReportData.from(context, status, explainer, recommender, plan);

        StringBuilder report = new StringBuilder();

        report.append("=".repeat(60)).append('\n');
        report.append("AEGIS Explainability Report\n");
        report.append("Mission : ").append(data.missionName()).append('\n');
        report.append("Goal    : ").append(data.missionGoal()).append('\n');
        report.append("Status  : ").append(data.status()).append('\n');
        report.append("=".repeat(60)).append('\n');

        report.append('\n').append("Summary:\n  ").append(data.outcomeSummary()).append('\n');

        report.append('\n').append("Mission Plan (advisory, generated before the run):\n");

        for (String step : data.plan().steps()) {
            report.append("  - ").append(step).append('\n');
        }

        report.append('\n').append("Recommendation:\n  ").append(data.recommendation()).append('\n');

        report.append('\n').append("Pages Visited:\n");

        for (String url : data.visitedPages()) {
            report.append("  - ").append(url).append('\n');
        }

        report.append('\n')
                .append("World Model: ")
                .append(data.states().size()).append(" states, ")
                .append(data.edges().size()).append(" transitions discovered\n");

        report.append("Exploration Coverage: ")
                .append(data.coverage().elementsInteracted()).append("/")
                .append(data.coverage().elementsDiscovered())
                .append(" discovered interactive elements exercised (")
                .append(String.format("%.0f%%", data.coverage().coveragePercent()))
                .append(")\n");

        report.append('\n').append("Page Coverage:\n");

        for (PageCoverage page : data.pageCoverage()) {
            report.append(String.format(
                    "  %-60s %d/%d (%.0f%%)%n",
                    page.url(),
                    page.elementsInteracted(),
                    page.elementsDiscovered(),
                    page.coveragePercent()
            ));
        }

        report.append('\n');

        for (NavigationEdge edge : data.edges()) {
            report.append(String.format(
                    "  %s %s: %s -> %s%n",
                    edge.actionType(),
                    edge.actionTarget(),
                    edge.fromState(),
                    edge.toState()
            ));
        }

        report.append('\n').append("Bug Clusters (Phase 6 — grouped by normalized fingerprint, most severe first):\n");

        if (data.bugClusters().isEmpty()) {
            report.append("  (none)\n");
        }

        for (BugCluster cluster : data.bugClusters()) {

            report.append(String.format(
                    "  [%s] %s (x%d%s)%n    -> %s%n",
                    cluster.severity(),
                    cluster.representativeSummary(),
                    cluster.occurrenceCount(),
                    cluster.spansMultiplePages()
                            ? ", seen on " + cluster.urls().size() + " different pages — may share a root cause"
                            : "",
                    data.explanationFor(cluster)
            ));
        }

        report.append('\n').append("Findings (most severe first):\n");

        if (data.findings().isEmpty()) {
            report.append("  (none)\n");
        }

        for (Finding finding : data.rankedFindings()) {
            report.append(String.format(
                    "  [%s] %s — %s (%s)%n    -> %s%n",
                    finding.severity(),
                    finding.summary(),
                    finding.url(),
                    finding.detectedAt(),
                    data.explain(finding)
            ));
        }

        report.append('\n').append("Reasoning (what was chosen and why, per step):\n");

        for (ReasoningStep step : data.reasoningSteps()) {

            double margin = data.runnerUpMargin(step);
            String marginText = Double.isNaN(margin)
                    ? "no alternatives were available"
                    : String.format("%.2f ahead of the next-best of %d alternatives", margin, step.candidates().size() - 1);

            report.append(String.format(
                    "  Step %d: %s %s (confidence=%.2f, %s) — %s%n",
                    step.step(),
                    step.selected().action().type(),
                    step.selected().action().target(),
                    step.selected().confidence(),
                    marginText,
                    step.selected().reasoning()
            ));
        }

        report.append('\n').append("=".repeat(60)).append('\n');

        return report.toString();
    }
}
