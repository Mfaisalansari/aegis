package com.aegis.core.reasoning.scorer;

import com.aegis.model.context.MissionContext;
import com.aegis.model.reasoning.CandidateAction;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Form First Exploration (proxy): prioritizes completing a form in its
 * natural order — inputs, then dropdowns, then buttons — before
 * following a link away from the page.
 *
 * Same caveat as BreadthFirst/DepthFirst: element tag is the only
 * structural signal available without a real navigation graph, so this
 * only distinguishes form-shaped elements from links, not "form" from
 * "not form" in any deeper sense.
 */
public class FormFirstActionScorer implements ActionScorer {

    private static final List<String> PRIORITY = List.of("input", "select", "button");

    @Override
    public CandidateAction choose(MissionContext context, List<CandidateAction> candidates) {

        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("No candidate actions available.");
        }

        for (String tag : PRIORITY) {

            Optional<CandidateAction> best = candidates.stream()
                    .filter(candidate -> tag.equalsIgnoreCase(candidate.action().elementTag()))
                    .max(Comparator.comparingDouble(CandidateAction::confidence));

            if (best.isPresent()) {
                return best.get();
            }
        }

        return candidates.stream()
                .max(Comparator.comparingDouble(CandidateAction::confidence))
                .orElseThrow();
    }
}
