package com.aegis.reporting;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared plain-language phrasing for {@link ExecutiveSummaryReportGenerator}
 * and {@link DigestGenerator} — kept in one place so the two formats never
 * describe the same {@link RunSummary} differently. Thresholds mirror the
 * same 50/80 bands a caller's own quality gate is expected to use (see
 * this session's own {@code Hooks.java} convention), so the report's
 * language and the gate's pass/fail decision never disagree.
 */
final class ReportLanguage {

    static final int MINUTES_SAVED_PER_HEAL = 5;
    static final int MINUTES_SAVED_PER_SELF_INPUT = 3;
    private static final int MAX_NAMES_SHOWN = 4;

    private ReportLanguage() {
    }

    /** {@code criticalFindingCount > 0} takes precedence over the score band — a critical finding is urgent regardless of an otherwise-decent score. */
    static String narrativeFor(RunSummary summary) {

        if (summary.criticalFindingCount() > 0) {
            return "needs immediate attention";
        }
        if (summary.experienceScore() < 50) {
            return "has real concerns worth addressing";
        }
        if (summary.experienceScore() < 80) {
            return "is functioning, with some areas worth reviewing";
        }
        return "is stable and performing well";
    }

    static String statusEmoji(boolean passed) {
        return passed ? "✅" : "❌";
    }

    static String pluralize(int count, String singular) {
        return count == 1 ? singular : singular + "s";
    }

    /**
     * Plain-language notes for {@link RunSummary#learningNotes()} — built
     * from real page names (resolved by the caller, not raw node keys)
     * rather than reused technical catalog text, because a layperson can't
     * act on a URL slug.
     *
     * The untested case deliberately does NOT claim something is broken:
     * the single most common reason a previously-covered page isn't
     * reached is that this run simply exercised a different set of
     * scenarios (each scenario intentionally covers its own flow, not the
     * whole app) — asserting "likely broken" for what is usually just
     * normal, intentional scope would cry wolf on every run. It's still
     * worth surfacing, since the *other* time it happens is a real
     * regression, but the copy names the mundane explanation first.
     */
    static List<String> learningNotesFor(List<String> newlyDiscoveredNames, List<String> untestedNames) {

        List<String> notes = new ArrayList<>();

        if (!untestedNames.isEmpty()) {
            notes.add(untestedNames.size() + " " + pluralize(untestedNames.size(), "page") + " covered in a previous run "
                    + (untestedNames.size() == 1 ? "wasn't" : "weren't") + " reached this run: " + namesJoined(untestedNames)
                    + ". Usually that just means a different set of scenarios ran this time — but if you expected full coverage,"
                    + " it's worth a quick look.");
        }

        if (!newlyDiscoveredNames.isEmpty()) {
            notes.add(newlyDiscoveredNames.size() + " new " + pluralize(newlyDiscoveredNames.size(), "page")
                    + " now being tracked: " + namesJoined(newlyDiscoveredNames) + ".");
        }

        return notes;
    }

    private static String namesJoined(List<String> names) {

        if (names.size() <= MAX_NAMES_SHOWN) {
            return String.join(", ", names);
        }

        return String.join(", ", names.subList(0, MAX_NAMES_SHOWN)) + ", and " + (names.size() - MAX_NAMES_SHOWN) + " more";
    }
}
