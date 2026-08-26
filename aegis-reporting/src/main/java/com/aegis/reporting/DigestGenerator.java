package com.aegis.reporting;

import java.util.List;

/**
 * A short, plain-text summary of a {@link RunSummary} (or a whole suite's
 * worth), meant to be pasted directly into Slack/Teams/email with no
 * rendering needed. Deterministic — no LLM dependency, same "a correct
 * result is always available" discipline this codebase applies to every
 * rule-based fallback.
 */
public final class DigestGenerator {

    public String generate(String applicationName, RunSummary summary) {

        StringBuilder out = new StringBuilder();

        out.append(ReportLanguage.statusEmoji(summary.passed())).append(' ')
                .append(applicationName).append(" — ").append(summary.name()).append('\n');
        out.append("Experience Score ").append(summary.experienceScore()).append("/100 — ")
                .append(applicationName).append(' ').append(ReportLanguage.narrativeFor(summary)).append('\n');
        out.append('\n');

        appendRoiLines(out, summary.healCount(), summary.selfInputCount());
        appendLearningLines(out, summary.newlyDiscoveredCount(), summary.untestedCount(), summary.learningNotes());
        appendFindingLines(out, summary.notableFindings(), summary.criticalFindingCount());

        return out.toString();
    }

    /** Same suite digest, without a coverage-learning section — for a caller that never wired up cross-run coverage tracking at all. */
    public String generate(String applicationName, List<RunSummary> summaries) {
        return generate(applicationName, summaries, 0, 0, List.of());
    }

    /**
     * Suite digest with explicit, suite-wide coverage numbers — see
     * {@link ExecutiveSummaryReportGenerator}'s matching overload for why
     * these can't just be summed from each scenario's own {@link
     * RunSummary} the way heal/self-input counts are.
     */
    public String generate(String applicationName, List<RunSummary> summaries,
                            int suiteNewlyDiscoveredCount, int suiteUntestedCount, List<String> suiteLearningNotes) {

        long passedCount = summaries.stream().filter(RunSummary::passed).count();
        boolean allPassed = passedCount == summaries.size();
        int averageScore = summaries.isEmpty() ? 0
                : (int) Math.round(summaries.stream().mapToInt(RunSummary::experienceScore).average().orElse(0));

        int totalHeals = summaries.stream().mapToInt(RunSummary::healCount).sum();
        int totalSelfInputs = summaries.stream().mapToInt(RunSummary::selfInputCount).sum();

        StringBuilder out = new StringBuilder();

        out.append(ReportLanguage.statusEmoji(allPassed)).append(' ').append(applicationName).append(" Regression").append('\n');
        out.append(passedCount).append('/').append(summaries.size())
                .append(" scenarios passed · Experience Score ").append(averageScore).append("/100 (average)").append('\n');
        out.append('\n');

        appendRoiLines(out, totalHeals, totalSelfInputs);
        appendLearningLines(out, suiteNewlyDiscoveredCount, suiteUntestedCount, suiteLearningNotes);

        List<RunSummary> needsAttention = summaries.stream()
                .filter(s -> !s.passed() || s.criticalFindingCount() > 0 || s.experienceScore() < 50)
                .toList();

        if (needsAttention.isEmpty()) {
            out.append("🎯 No critical issues").append('\n');
            if (suiteUntestedCount > 0) {
                out.append("   (pass/fail and score only — coverage change noted above is separate)").append('\n');
            }
        } else {
            out.append("⚠️  ").append(needsAttention.size()).append(' ')
                    .append(ReportLanguage.pluralize(needsAttention.size(), "scenario")).append(" need attention:").append('\n');
            for (RunSummary needsReview : needsAttention) {
                out.append("   - ").append(needsReview.name())
                        .append(" (score ").append(needsReview.experienceScore()).append("/100)").append('\n');
            }
        }

        return out.toString();
    }

    private void appendRoiLines(StringBuilder out, int healCount, int selfInputCount) {

        if (healCount > 0) {
            out.append("🔧 ").append(healCount).append(' ').append(ReportLanguage.pluralize(healCount, "locator"))
                    .append(" auto-healed — ~").append(healCount * ReportLanguage.MINUTES_SAVED_PER_HEAL)
                    .append(" min manual fixes avoided (estimated)").append('\n');
        }

        if (selfInputCount > 0) {
            out.append("✍️ ").append(selfInputCount).append(' ').append(ReportLanguage.pluralize(selfInputCount, "field value"))
                    .append(" auto-generated — ~").append(selfInputCount * ReportLanguage.MINUTES_SAVED_PER_SELF_INPUT)
                    .append(" min test-data authoring avoided (estimated)").append('\n');
        }
    }

    private void appendLearningLines(StringBuilder out, int newlyDiscoveredCount, int untestedCount, List<String> learningNotes) {

        if (newlyDiscoveredCount > 0) {
            out.append("🧭 ").append(newlyDiscoveredCount).append(' ')
                    .append(ReportLanguage.pluralize(newlyDiscoveredCount, "screen")).append(" seen for the first time").append('\n');
        }

        if (untestedCount > 0) {
            out.append("⏮️ ").append(untestedCount).append(' ')
                    .append(ReportLanguage.pluralize(untestedCount, "screen")).append(" reached before but not this run").append('\n');
        }

        for (String note : learningNotes) {
            out.append("   ").append(note).append('\n');
        }
    }

    private void appendFindingLines(StringBuilder out, List<String> notableFindings, int criticalFindingCount) {

        if (notableFindings.isEmpty()) {
            out.append("🎯 No critical issues").append('\n');
            return;
        }

        String severityLabel = criticalFindingCount > 0 ? "critical" : "medium";
        out.append("⚠️  ").append(notableFindings.size()).append(' ')
                .append(ReportLanguage.pluralize(notableFindings.size(), "finding")).append(" (").append(severityLabel).append("): ")
                .append(String.join("; ", notableFindings)).append('\n');
    }
}
