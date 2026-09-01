package com.aegis.core.reasoning.scorer;

import com.aegis.model.action.Action;
import com.aegis.model.context.MissionContext;
import com.aegis.model.reasoning.CandidateAction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Picks the highest-confidence candidate. When several tie on confidence
 * (e.g. {@code PageLevelCandidateActionGenerator}'s REFRESH/BACK, both
 * fixed at 0.2, or a page with many equally-generic buttons and links), a
 * plain {@code Stream.max} always keeps the first one seen in generation
 * order — deterministically re-selecting the exact same action forever if
 * a tie recurs every iteration, or systematically starving an entire
 * element type (e.g. every link on a page, including a shopping-cart
 * icon, loses every tie to buttons since {@code DefaultObserver} appends
 * links after buttons) until that type is exhausted.
 *
 * Ties are broken in two stages:
 * <ol>
 *   <li>Away from the exact action just executed, if a different tied
 *   option exists — otherwise a REFRESH/BACK-shaped tie (identical
 *   confidence, empty {@code elementTag}, recurring every iteration)
 *   would pick the same one forever.</li>
 *   <li>Among what remains, toward whichever {@code elementTag} (input,
 *   button, link, select, ...) was picked longest ago by this scorer —
 *   an instance-scoped, one-per-mission rotation, not a global one — so
 *   element types interleave instead of one type hogging every tied win
 *   until it's fully exhausted.</li>
 * </ol>
 */
public class HighestConfidenceActionScorer implements ActionScorer {

    private final Map<String, Integer> lastPickedOrderByTag = new HashMap<>();
    private int nextOrder = 0;

    @Override
    public CandidateAction choose(MissionContext context, List<CandidateAction> candidates) {

        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("No candidate actions available.");
        }

        double maxConfidence = candidates.stream()
                .mapToDouble(CandidateAction::confidence)
                .max()
                .orElseThrow(() -> new IllegalStateException("No candidate actions available."));

        List<CandidateAction> tiedForFirst = candidates.stream()
                .filter(candidate -> candidate.confidence() == maxConfidence)
                .toList();

        if (tiedForFirst.size() == 1) {
            return tiedForFirst.get(0);
        }

        List<CandidateAction> pool = excludingLastExecuted(context, tiedForFirst);

        CandidateAction chosen = leastRecentlyPickedTag(pool);

        lastPickedOrderByTag.put(chosen.action().elementTag(), nextOrder++);

        return chosen;
    }

    private List<CandidateAction> excludingLastExecuted(MissionContext context, List<CandidateAction> tiedForFirst) {

        Action lastExecuted = lastExecutedAction(context);

        if (lastExecuted == null) {
            return tiedForFirst;
        }

        List<CandidateAction> excludingLast = new ArrayList<>();

        for (CandidateAction candidate : tiedForFirst) {
            if (!isSameAction(candidate.action(), lastExecuted)) {
                excludingLast.add(candidate);
            }
        }

        return excludingLast.isEmpty() ? tiedForFirst : excludingLast;
    }

    private CandidateAction leastRecentlyPickedTag(List<CandidateAction> pool) {

        Map<String, List<CandidateAction>> byTag = new LinkedHashMap<>();

        for (CandidateAction candidate : pool) {
            byTag.computeIfAbsent(candidate.action().elementTag(), key -> new ArrayList<>()).add(candidate);
        }

        String oldestTag = null;
        int oldestOrder = Integer.MAX_VALUE;

        for (String tag : byTag.keySet()) {
            int order = lastPickedOrderByTag.getOrDefault(tag, -1);
            if (order < oldestOrder) {
                oldestOrder = order;
                oldestTag = tag;
            }
        }

        return byTag.get(oldestTag).get(0);
    }

    private Action lastExecutedAction(MissionContext context) {

        List<Action> actions = context.getExecutionState().getActions();

        return actions.isEmpty() ? null : actions.get(actions.size() - 1);
    }

    private boolean isSameAction(Action a, Action b) {
        return a.type() == b.type() && Objects.equals(a.target(), b.target());
    }
}
