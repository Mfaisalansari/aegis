package com.aegis.core.report;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reinterprets the exact, frozen suffix {@code
 * HeuristicCandidateConfidenceEstimator#adjust} appends to a candidate's
 * reasoning — display-layer only, never touches how that string is
 * produced (that estimator and {@code LearningEngine} are frozen Stable
 * Components; see AEGIS_ROADMAP.md).
 *
 * That suffix collapses two very different situations into the same
 * substring, "learning-adjusted": a flat, always-+0.05 exploration bonus
 * for a candidate with zero prior Experience this mission (the common
 * case — fires on nearly every page-specific element), and a genuine
 * adjustment derived from a real prior success/failure rate for a
 * candidate that reappeared across states (rare — persistent nav
 * elements etc.). Naive substring matching on "learning-adjusted" tags
 * both as "learned", which is what actually misled a user watching the
 * live dashboard into thinking real learning was happening on every
 * single action. This class tells the two apart and supplies honest
 * display copy for both, plus a no-op for the neutral 0.0-adjustment
 * case where nothing was appended at all.
 */
public final class LearningAdjustmentGlossary {

    private LearningAdjustmentGlossary() {
    }

    private static final String NEVER_TRIED_REASON = "never tried yet this mission";

    /**
     * Anchored on the literal "(learning-adjusted " prefix, not just the
     * bare substring, so it can't collide with unrelated parenthetical
     * text already in a candidate's base reason (e.g. "Credential-like
     * field (PASSWORD)"). Group 1 is the signed, {@code %.2f}-formatted
     * magnitude (e.g. "+0.05", "-0.20"). Group 2, present only for the
     * exploration-bonus case, is the extra reason clause.
     */
    private static final Pattern LEARNING_CLAUSE =
            Pattern.compile("\\(learning-adjusted ([+-]?\\d+\\.\\d{2})(?:, ([^()]+))?\\)");

    /**
     * True only for a genuine prior-experience adjustment — the bare
     * "(learning-adjusted +0.20)"-style clause with no "never tried"
     * extra reason. False for the flat exploration bonus, the neutral
     * 0.0 case (nothing was appended), and null.
     */
    public static boolean isGenuineLearnedAdjustment(String reasoning) {

        if (reasoning == null) {
            return false;
        }

        Matcher matcher = LEARNING_CLAUSE.matcher(reasoning);
        return matcher.find() && !NEVER_TRIED_REASON.equals(matcher.group(2));
    }

    /**
     * Replaces the raw clause with honest display copy. A no-op for the
     * neutral 0.0-adjustment case (no clause was ever appended, so there
     * is nothing to relabel) and for null.
     */
    public static String honestReasoning(String reasoning) {

        if (reasoning == null) {
            return null;
        }

        Matcher matcher = LEARNING_CLAUSE.matcher(reasoning);
        if (!matcher.find()) {
            return reasoning;
        }

        String magnitude = matcher.group(1);
        String extraReason = matcher.group(2);
        String replacement = NEVER_TRIED_REASON.equals(extraReason)
                ? "(" + magnitude + " — exploring new ground)"
                : "(learned: " + (magnitude.startsWith("-") ? "tends to fail" : "tends to work") + ", " + magnitude + ")";

        return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    }
}
