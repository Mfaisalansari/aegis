package com.aegis.core.reasoning.scorer;

import com.aegis.core.reasoning.memory.StateSignature;
import com.aegis.model.context.MissionContext;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Automatic Dynamic Strategy Switching (Phase 4): delegates to a primary
 * strategy normally, but switches to a coverage-biased one once
 * exploration looks stuck — no newly discovered state in the last
 * {@value STUCK_THRESHOLD} iterations.
 *
 * "Stuck" is recomputed fresh from MissionContext.getExecutionState()'s
 * own observation history on every choose() call — no extra state to
 * track, and it self-corrects the moment a new state turns up again,
 * without needing to be told to switch back. This is why it's a wrapper
 * around two existing ActionScorers rather than a new standalone one:
 * the "when" is the only genuinely new logic; "what to do about it" is
 * already CoverageAwareActionScorer's job.
 */
public class AdaptiveActionScorer implements ActionScorer {

    private static final int STUCK_THRESHOLD = 3;

    private final ActionScorer primary;
    private final ActionScorer whenStuck;

    public AdaptiveActionScorer(ActionScorer primary, ActionScorer whenStuck) {
        this.primary = primary;
        this.whenStuck = whenStuck;
    }

    @Override
    public CandidateAction choose(MissionContext context, List<CandidateAction> candidates) {

        ActionScorer active = isStuck(context) ? whenStuck : primary;

        return active.choose(context, candidates);
    }

    private boolean isStuck(MissionContext context) {

        List<Observation> observations = context.getExecutionState().getObservations();

        if (observations.isEmpty()) {
            return false;
        }

        Set<String> discovered = new LinkedHashSet<>();
        int lastNewStateIndex = -1;

        for (int i = 0; i < observations.size(); i++) {

            String signature = StateSignature.of(observations.get(i));

            if (discovered.add(signature)) {
                lastNewStateIndex = i;
            }
        }

        int iterationsSinceNewState = (observations.size() - 1) - lastNewStateIndex;

        return iterationsSinceNewState >= STUCK_THRESHOLD;
    }
}
