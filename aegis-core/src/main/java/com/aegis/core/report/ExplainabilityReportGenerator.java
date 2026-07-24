package com.aegis.core.report;

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

        MissionReportData data = MissionReportData.from(context, status);

        StringBuilder report = new StringBuilder();

        report.append("=".repeat(60)).append('\n');
        report.append("AEGIS Explainability Report\n");
        report.append("Mission : ").append(data.missionName()).append('\n');
        report.append("Goal    : ").append(data.missionGoal()).append('\n');
        report.append("Status  : ").append(data.status()).append('\n');
        report.append("=".repeat(60)).append('\n');

        report.append('\n').append("Summary:\n  ").append(data.outcomeSummary()).append('\n');

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
