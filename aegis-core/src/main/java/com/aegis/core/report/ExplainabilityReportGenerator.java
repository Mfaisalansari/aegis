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
import com.aegis.model.reasoning.ReasoningStep;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Renders a mission's outcome into a plain-text report for after-the-fact
 * review: what AEGIS was trying to do, what happened, and — per finding
 * and per reasoning step — why it matters, not just the raw log line.
 * Kept separate from the live console log so the run itself stays quiet.
 *
 * Reporting v2 layout: an Executive Summary a manager can stop at, the
 * Mission Timeline right after it (a chronological replay of the whole
 * run — the "heart" of this report), then the same detail sections as
 * before for readers who want to go deeper.
 */
public class ExplainabilityReportGenerator {

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

        MissionReportData data =
                MissionReportData.from(context, status, explainer, recommender, plan, experiences, screenshots);

        StringBuilder report = new StringBuilder();

        report.append("=".repeat(70)).append('\n');
        report.append("AEGIS Explainability Report\n");
        report.append("=".repeat(70)).append('\n');

        appendExecutiveSummary(report, data);
        appendTimeline(report, data);
        appendMissionPlan(report, data);
        appendPagesAndWorldModel(report, data);
        appendCoverage(report, data);
        appendLearning(report, data);
        appendFindingsDashboard(report, data);
        appendBugClusters(report, data);
        appendFindings(report, data);
        appendReasoning(report, data);

        report.append('\n').append("=".repeat(70)).append('\n');

        return report.toString();
    }

    private void appendExecutiveSummary(StringBuilder report, MissionReportData data) {

        report.append('\n').append("EXECUTIVE SUMMARY\n");
        report.append("  Mission      : ").append(data.missionName()).append('\n');
        report.append("  Goal         : ").append(data.missionGoal()).append('\n');
        report.append("  Result       : ").append(data.status()).append('\n');
        report.append("  Duration     : ").append(formatDuration(data.duration())).append('\n');
        report.append("  Coverage     : ").append(String.format("%.0f%%", data.coverage().coveragePercent()))
                .append(" (").append(data.coverage().elementsInteracted()).append("/")
                .append(data.coverage().elementsDiscovered()).append(" elements)\n");
        report.append("  Findings     : ").append(data.findings().size()).append('\n');
        report.append("  Bug Clusters : ").append(data.bugClusters().size()).append('\n');
        report.append("  Learning     : ").append(learningLine(data.learningSummary())).append('\n');
        report.append("  Recommendation: ").append(data.recommendation()).append('\n');

        report.append('\n').append("STATISTICS\n");
        report.append("  Actions Executed: ").append(data.actionsExecuted());
        report.append("   Pages: ").append(data.visitedPages().size());
        report.append("   States: ").append(data.states().size());
        report.append("   Transitions: ").append(data.edges().size()).append('\n');
        report.append("  Avg Confidence: ").append(String.format("%.2f", data.averageConfidence()));
        report.append("   Bug Count: ").append(data.findings().size());
        report.append("   Clusters: ").append(data.bugClusters().size());
        report.append("   Duration: ").append(formatDuration(data.duration())).append('\n');
    }

    private String learningLine(LearningSummary summary) {

        if (summary.newExperiences() == 0 && summary.updatedActions() == 0) {
            return "no experience recorded this mission";
        }

        return summary.newExperiences() + " new, " + summary.updatedActions() + " updated action(s) — "
                + summary.confidenceIncreased() + " improved, " + summary.confidenceReduced() + " declined";
    }

    /**
     * The Mission Timeline — Reporting v2's centerpiece. A chronological
     * replay of the whole run, one line per event, built from
     * MissionReportData.timeline() (itself reconstructed purely from
     * already-recorded data, see MissionReportData.buildTimeline).
     */
    private void appendTimeline(StringBuilder report, MissionReportData data) {

        report.append('\n').append("MISSION TIMELINE\n");

        for (TimelineEvent event : data.timeline()) {

            report.append("  ").append(TIME_FORMAT.format(event.timestamp()))
                    .append("  ").append(event.headline());

            if (event.detail() != null && !event.detail().isBlank()) {
                report.append('\n').append("            -> ").append(event.detail());
            }

            if (event.screenshotDataUri() != null && !event.screenshotDataUri().isBlank()) {
                // The full data URI (often 100KB+ of base64) belongs in the
                // HTML/JSON reports, not here — this format is meant for
                // grepping/diffing, so just note that a screenshot exists.
                report.append('\n').append("            [screenshot captured — see HTML/JSON report]");
            }

            report.append('\n');
        }
    }

    private void appendMissionPlan(StringBuilder report, MissionReportData data) {

        report.append('\n').append("Mission Plan (advisory, generated before the run):\n");

        for (String step : data.plan().steps()) {
            report.append("  - ").append(step).append('\n');
        }
    }

    private void appendPagesAndWorldModel(StringBuilder report, MissionReportData data) {

        report.append('\n').append("Pages Visited:\n");

        for (String url : data.visitedPages()) {
            report.append("  - ").append(url).append('\n');
        }

        report.append('\n')
                .append("World Model: ")
                .append(data.states().size()).append(" states, ")
                .append(data.edges().size()).append(" transitions discovered\n");

        for (NavigationEdge edge : data.edges()) {
            report.append(String.format(
                    "  %s %s: %s -> %s%n",
                    edge.actionType(),
                    edge.actionTarget(),
                    edge.fromState(),
                    edge.toState()
            ));
        }
    }

    private void appendCoverage(StringBuilder report, MissionReportData data) {

        report.append('\n').append("Exploration Coverage: ")
                .append(data.coverage().elementsInteracted()).append("/")
                .append(data.coverage().elementsDiscovered())
                .append(" discovered interactive elements exercised (")
                .append(String.format("%.0f%%", data.coverage().coveragePercent()))
                .append(")\n");

        report.append('\n').append("Pages (all pages AEGIS discovered — there is no sitemap, so a page it never "
                + "found can't appear here as \"not visited\"):\n");

        for (String url : data.visitedPages()) {
            report.append("  ✓ ").append(url).append('\n');
        }

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

        report.append('\n').append("(See World Model above and the HTML report's heat map for the navigation ")
                .append("graph and state-visit-frequency view of this same coverage.)\n");
    }

    /**
     * Learning (Reporting v2 Stage 2): the user should see AEGIS
     * improving, not just that Phase 2's LearningEngine exists. Every
     * number here is a straight read of MissionReportData.learningSummary()
     * — see LearningSummary and DefaultPatternAnalyzer for how it's
     * computed.
     */
    private void appendLearning(StringBuilder report, MissionReportData data) {

        LearningSummary summary = data.learningSummary();

        report.append('\n').append("Learning:\n");
        report.append("  New Experiences      : ").append(summary.newExperiences()).append('\n');
        report.append("  Updated Actions      : ").append(summary.updatedActions()).append('\n');
        report.append("  Confidence Increased : ").append(summary.confidenceIncreased()).append('\n');
        report.append("  Confidence Reduced   : ").append(summary.confidenceReduced()).append('\n');

        if (summary.actionPerformance().isEmpty()) {
            report.append("  (no experience recorded this mission)\n");
            return;
        }

        report.append("\n  Best Performing Actions:\n");

        for (PatternStatistics stat : summary.actionPerformance().subList(0, Math.min(3, summary.actionPerformance().size()))) {
            report.append("    ").append(actionPerformanceLine(stat)).append('\n');
        }

        report.append("\n  Worst Performing Actions:\n");

        List<PatternStatistics> worstFirst = summary.actionPerformance().reversed();

        for (PatternStatistics stat : worstFirst.subList(0, Math.min(3, worstFirst.size()))) {
            report.append("    ").append(actionPerformanceLine(stat)).append('\n');
        }
    }

    private String actionPerformanceLine(PatternStatistics stat) {
        return String.format(
                "%s %s — %.0f%% success (%d/%d)",
                stat.action().type(), stat.action().target(),
                stat.successRate() * 100, stat.successfulExecutions(), stat.totalExecutions()
        );
    }

    /**
     * Findings Dashboard (Reporting v2 Stage 2): the same BugClusters
     * Phase 6 already computed, grouped one level further by what kind
     * of problem they are (see FindingCategory) instead of only by
     * fingerprint — "3 JavaScript issues, 1 Network issue" reads faster
     * than a flat list when there are more than a couple of clusters.
     */
    private void appendFindingsDashboard(StringBuilder report, MissionReportData data) {

        report.append('\n').append("Findings Dashboard (Bug Clusters grouped by category):\n");

        if (data.findingsByCategory().isEmpty()) {
            report.append("  (none)\n");
            return;
        }

        for (Map.Entry<FindingCategory, List<BugCluster>> entry : data.findingsByCategory().entrySet()) {

            report.append("  ").append(entry.getKey()).append(" (").append(entry.getValue().size()).append("):\n");

            for (BugCluster cluster : entry.getValue()) {
                report.append("    [").append(cluster.severity()).append("] ")
                        .append(cluster.representativeSummary()).append(" (x").append(cluster.occurrenceCount())
                        .append(")\n");
            }
        }
    }

    private void appendBugClusters(StringBuilder report, MissionReportData data) {

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
    }

    private void appendFindings(StringBuilder report, MissionReportData data) {

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
    }

    /**
     * Reasoning per step: every candidate considered, its confidence, and
     * why the winner won — including a [learned] tag when the heuristic
     * confidence was adjusted by Phase 2/3's learning (parsed out of the
     * candidate's own reasoning text, which already carries that
     * annotation — see HeuristicCandidateConfidenceEstimator).
     */
    private void appendReasoning(StringBuilder report, MissionReportData data) {

        report.append('\n').append("Reasoning (what was chosen and why, per step):\n");

        for (ReasoningStep step : data.reasoningSteps()) {

            double margin = data.runnerUpMargin(step);
            String marginText = Double.isNaN(margin)
                    ? "no alternatives were available"
                    : String.format("%.2f ahead of the next-best of %d alternatives", margin, step.candidates().size() - 1);

            String learnedTag = step.selected().reasoning().contains("learning-adjusted") ? " [learned]" : "";

            report.append(String.format(
                    "  Step %d: %s %s (confidence=%.2f, %s)%s — %s%n",
                    step.step(),
                    step.selected().action().type(),
                    step.selected().action().target(),
                    step.selected().confidence(),
                    marginText,
                    learnedTag,
                    step.selected().reasoning()
            ));
        }
    }

    private String formatDuration(Duration duration) {

        long totalSeconds = duration.toSeconds();

        if (totalSeconds < 60) {
            return String.format("%.1fs", duration.toMillis() / 1000.0);
        }

        return (totalSeconds / 60) + "m " + (totalSeconds % 60) + "s";
    }
}
