package com.aegis.core.reasoning.filter;

import com.aegis.core.reasoning.memory.ExecutionMemory;
import com.aegis.core.reasoning.memory.StateSignature;
import com.aegis.model.action.Action;
import com.aegis.model.context.MissionContext;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Applies a chain of filters in order. If the chain would eliminate every
 * candidate, the scorer still needs something to choose from. The
 * fallback is layered:
 * <ol>
 *   <li>Exclude every candidate already executed from this exact state
 *   (the same check {@code AlreadyExecutedCandidateFilter} does, reusing
 *   the same {@link ExecutionMemory}).</li>
 *   <li>If that still leaves nothing — every raw candidate has genuinely
 *   already been tried from here at some point (e.g. a login page
 *   reached again after logging out, where the original login already
 *   tried every field and the submit button) — retry whichever action
 *   has gone longest untried from this state, an LRU-style rotation
 *   through every stale candidate. Excluding only the single most
 *   recently executed action here would instead leave two or more
 *   distinct actions (e.g. retyping username, then password, then
 *   username again) free to oscillate between each other forever,
 *   without ever reaching a stale-but-necessary action like re-clicking
 *   login.</li>
 *   <li>If there's no current observation to key a per-state fallback on
 *   (a defensive/test scenario), exclude just the single action executed
 *   immediately before this one instead.</li>
 *   <li>If even that leaves nothing, return the untouched original list
 *   — the scorer must always get something.</li>
 * </ol>
 */
public class CompositeCandidateFilter implements CandidateFilter {

    private final List<CandidateFilter> filters;
    private final ExecutionMemory memory;

    public CompositeCandidateFilter(List<CandidateFilter> filters, ExecutionMemory memory) {
        this.filters = filters;
        this.memory = memory;
    }

    @Override
    public List<CandidateAction> filter(MissionContext context, List<CandidateAction> candidates) {

        List<CandidateAction> result = candidates;

        for (CandidateFilter filter : filters) {
            result = filter.filter(context, result);
        }

        if (!result.isEmpty()) {
            return result;
        }

        Observation current = context.getExecutionState().getCurrentObservation();

        if (current != null && !candidates.isEmpty()) {

            String stateSignature = StateSignature.of(current);

            List<CandidateAction> excludingExactRepeats = candidates.stream()
                    .filter(candidate -> !memory.hasExecuted(stateSignature, candidate.action()))
                    .collect(Collectors.toList());

            if (!excludingExactRepeats.isEmpty()) {
                return excludingExactRepeats;
            }

            // Every raw candidate has already been executed from this
            // exact state at some point (a genuinely, fully exhausted
            // state — e.g. a login page reached again after logging out,
            // where the original login already tried every field and the
            // submit button). Retry whichever action has gone longest
            // untried from here — a fair, LRU-style rotation through every
            // stale candidate — rather than just excluding literally the
            // last one, which would leave two or more distinct actions
            // (e.g. retyping username, then password, then username
            // again) free to oscillate between each other forever without
            // ever reaching a stale-but-necessary action like re-clicking
            // login.
            int oldestOrder = candidates.stream()
                    .mapToInt(candidate -> memory.lastExecutedOrder(stateSignature, candidate.action()))
                    .min()
                    .orElseThrow();

            return candidates.stream()
                    .filter(candidate -> memory.lastExecutedOrder(stateSignature, candidate.action()) == oldestOrder)
                    .collect(Collectors.toList());
        }

        // No observation to key a per-state fallback on (a defensive/test
        // scenario) — fall back to excluding just the action executed
        // immediately before this one.
        Action lastExecuted = lastExecutedAction(context);

        if (lastExecuted == null) {
            return candidates;
        }

        List<CandidateAction> withoutLastRepeat = candidates.stream()
                .filter(candidate -> !isSameAction(candidate.action(), lastExecuted))
                .collect(Collectors.toList());

        return withoutLastRepeat.isEmpty() ? candidates : withoutLastRepeat;
    }

    private Action lastExecutedAction(MissionContext context) {

        List<Action> actions = context.getExecutionState().getActions();

        return actions.isEmpty() ? null : actions.get(actions.size() - 1);
    }

    private boolean isSameAction(Action a, Action b) {
        return a.type() == b.type() && Objects.equals(a.target(), b.target());
    }
}
