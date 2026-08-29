package com.aegis.core.report;

import com.aegis.core.reasoning.learning.PatternStatistics;

import java.util.List;

/**
 * Display-layer only, same precedent as {@link LearningAdjustmentGlossary} —
 * recomputes an honest read directly from {@link PatternStatistics#totalExecutions()}/
 * {@link PatternStatistics#successRate()} because {@link LearningSummary#confidenceIncreased()}/
 * {@link LearningSummary#confidenceReduced()} count any action with a high/low
 * success rate, including a single 1/1 first try — conflating "succeeded
 * once" with "confirmed reliable through repetition". Never touches
 * {@link MissionReportData}/{@link LearningSummary}/{@link PatternStatistics}
 * themselves; those are shared with the frozen {@code
 * HtmlExplainabilityReportGenerator} and must keep producing exactly what
 * they always have.
 */
public final class LearningHonestyGlossary {

    private LearningHonestyGlossary() {
    }

    /** Per-action status for a report row — "First try" for anything not yet repeated, otherwise a real reliability read. */
    public static String statusLabel(PatternStatistics stat) {

        if (stat.totalExecutions() == 1) {
            return "First try";
        }
        if (stat.successRate() >= 0.75) {
            return "Confirmed reliable";
        }
        if (stat.successRate() < 0.50) {
            return "Confirmed unreliable";
        }
        return "Mixed results";
    }

    /** Only counts actions actually repeated more than once — a single lucky success never counts as "confirmed". */
    public static long confirmedReliableCount(List<PatternStatistics> actionPerformance) {
        return actionPerformance.stream()
                .filter(stat -> stat.totalExecutions() > 1 && stat.successRate() >= 0.75)
                .count();
    }

    public static long confirmedUnreliableCount(List<PatternStatistics> actionPerformance) {
        return actionPerformance.stream()
                .filter(stat -> stat.totalExecutions() > 1 && stat.successRate() < 0.50)
                .count();
    }

    /**
     * The one-line summary shared by the Overview tab's "Learning" fact and
     * the Learning tab's own caption, so the two never disagree. Honest
     * about the common case — a short mission where nothing was repeated
     * enough yet to say anything about reliability.
     */
    public static String honestLearningLine(LearningSummary summary) {

        if (summary.updatedActions() == 0) {
            return summary.newExperiences() + " new — every action was a first attempt, nothing repeated yet";
        }

        return summary.newExperiences() + " new, " + summary.updatedActions() + " repeated — "
                + confirmedReliableCount(summary.actionPerformance()) + " confirmed reliable, "
                + confirmedUnreliableCount(summary.actionPerformance()) + " confirmed unreliable";
    }
}
