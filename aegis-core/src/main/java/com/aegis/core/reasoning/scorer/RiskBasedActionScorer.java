package com.aegis.core.reasoning.scorer;

import com.aegis.model.context.MissionContext;
import com.aegis.model.reasoning.CandidateAction;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Risk-Based Exploration: prioritizes candidates whose locator suggests a
 * destructive or high-value action (delete, checkout, submit, pay, ...)
 * — the actions most worth exercising early, since they're the ones most
 * likely to hide a real defect or do real damage if broken. Falls back
 * to the least-confident candidate (probing paths a greedy strategy
 * would skip) when nothing looks risky, rather than picking arbitrarily.
 *
 * The only signal available here is the action's locator string, which
 * often embeds the element's id/name (e.g. "[id='delete-account']") —
 * not a full model of risk, but the same style of proxy FieldPurpose
 * already uses for credential fields, and a closer match to "prioritize
 * destructive/high-value actions" than plain confidence-inversion was.
 */
public class RiskBasedActionScorer implements ActionScorer {

    private static final List<String> RISK_KEYWORDS = List.of(
            "delete", "remove", "cancel", "clear", "reset",
            "logout", "signout", "sign-out", "unsubscribe", "deactivate",
            "checkout", "purchase", "buy", "pay", "submit", "confirm", "order"
    );

    @Override
    public CandidateAction choose(MissionContext context, List<CandidateAction> candidates) {

        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("No candidate actions available.");
        }

        Optional<CandidateAction> risky = candidates.stream()
                .filter(this::looksRisky)
                .max(Comparator.comparingDouble(CandidateAction::confidence));

        return risky.orElseGet(() -> candidates.stream()
                .min(Comparator.comparingDouble(CandidateAction::confidence))
                .orElseThrow());
    }

    private boolean looksRisky(CandidateAction candidate) {

        String target = candidate.action().target().toLowerCase(Locale.ROOT);

        return RISK_KEYWORDS.stream().anyMatch(target::contains);
    }
}
