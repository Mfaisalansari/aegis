package com.aegis.core.reasoning.scorer;

import com.aegis.model.context.MissionContext;
import com.aegis.model.reasoning.CandidateAction;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Breadth First Exploration (proxy): exhausts non-navigating actions on
 * the current page (typing, local clicks) before following a link away
 * from it.
 *
 * Not a real graph search — there is no navigation graph to search yet
 * (that needs a World Model, Sprint 10). This approximates "explore this
 * level before going deeper" using the one signal available at decision
 * time: whether an action's target looks like a link.
 *
 * "Local" means a genuine, tagged DOM element (input/select/button) —
 * deliberately not just "anything that isn't a link". Page-level
 * interruptions (REFRESH/BACK) have no element tag at all and are
 * regenerated fresh every single iteration, so an earlier version of
 * this class that treated "not a link" as "local" would deadlock the
 * moment every genuine local element on a page was exhausted: REFRESH
 * and BACK would keep winning over links forever (confirmed live on
 * saucedemo.com — 19+ REFRESH/BACK cycles with no real progress), since
 * they satisfied "not a link" just as well as an actual button and never
 * ran out. Requiring a real, non-blank tag puts REFRESH/BACK in the same
 * last-resort tier as links, so once local elements are exhausted this
 * can still escape to a new page instead of spinning forever.
 */
public class BreadthFirstActionScorer implements ActionScorer {

    @Override
    public CandidateAction choose(MissionContext context, List<CandidateAction> candidates) {

        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("No candidate actions available.");
        }

        Optional<CandidateAction> local = candidates.stream()
                .filter(this::isRealLocalElement)
                .max(Comparator.comparingDouble(CandidateAction::confidence));

        return local.orElseGet(() -> candidates.stream()
                .max(Comparator.comparingDouble(CandidateAction::confidence))
                .orElseThrow());
    }

    private boolean isRealLocalElement(CandidateAction candidate) {

        String tag = candidate.action().elementTag();

        return tag != null && !tag.isBlank() && !"a".equalsIgnoreCase(tag);
    }
}
