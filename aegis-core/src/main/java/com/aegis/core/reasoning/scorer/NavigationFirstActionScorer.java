package com.aegis.core.reasoning.scorer;

import com.aegis.model.context.MissionContext;
import com.aegis.model.reasoning.CandidateAction;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Navigation First Exploration (proxy): prefers following a link away
 * from the current page over filling in forms or clicking buttons, so
 * menus and navigation get covered early.
 *
 * Element tag is the only structural signal available today — there's no
 * nav-region/menu detection — so in practice this currently makes the
 * same choice as DepthFirstActionScorer. They're kept as separate
 * strategies because they express different intents (DepthFirst is
 * about traversal order and is meant to eventually follow real
 * WorldModel graph edges; NavigationFirst is about element category) and
 * should diverge once a richer navigation signal exists.
 */
public class NavigationFirstActionScorer implements ActionScorer {

    @Override
    public CandidateAction choose(MissionContext context, List<CandidateAction> candidates) {

        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("No candidate actions available.");
        }

        Optional<CandidateAction> link = candidates.stream()
                .filter(candidate -> "a".equalsIgnoreCase(candidate.action().elementTag()))
                .max(Comparator.comparingDouble(CandidateAction::confidence));

        return link.orElseGet(() -> candidates.stream()
                .max(Comparator.comparingDouble(CandidateAction::confidence))
                .orElseThrow());
    }
}
