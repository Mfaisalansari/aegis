package com.aegis.core.report;

import com.aegis.core.reasoning.learning.PatternStatistics;

import java.util.List;

/**
 * Reporting v2 "Learning" summary — computed from this mission's own
 * Experience list via the same DefaultPatternAnalyzer grouping
 * LearningEngine itself uses (Phase 2), so "new" vs "updated" and
 * "improved" vs "declined" agree with what actually drove confidence
 * adjustments during the run. Purely a read of that data for display;
 * nothing here feeds back into LearningEngine's own behavior.
 *
 * "New" = an action (by type+target) with exactly one recorded
 * experience this mission — no prior pattern to compare against yet.
 * "Updated" = an action with more than one — we now have a real
 * success-rate signal for it. "Improved"/"declined" mirror
 * DefaultLearningEngine's own bucket thresholds (>=0.75 successRate
 * earns a positive adjustment, <0.50 earns a negative one).
 *
 * actionPerformance is every distinct action this mission touched,
 * sorted best success rate first — Stage 2's "Best/Worst Performing
 * Actions": callers take the head for best, the tail for worst. A
 * mission with only a handful of distinct actions will naturally see
 * the same actions in both lists, in reverse order — that's an honest
 * reflection of a small sample, not something worth hiding.
 */
public record LearningSummary(
        int newExperiences,
        int updatedActions,
        int confidenceIncreased,
        int confidenceReduced,
        List<PatternStatistics> actionPerformance
) {

    public static LearningSummary empty() {
        return new LearningSummary(0, 0, 0, 0, List.of());
    }
}
