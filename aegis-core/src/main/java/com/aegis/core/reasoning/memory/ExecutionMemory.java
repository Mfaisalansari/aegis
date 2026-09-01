package com.aegis.core.reasoning.memory;

import com.aegis.model.action.Action;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks which actions have already been executed, scoped per state (see
 * StateSignature) rather than globally. An exact repeat of the same
 * action from the same state is blocked — that's what actually prevents
 * pointless looping. A locator that's genuinely present on more than one
 * page (a persistent nav link, say) can still be tried again once
 * reached from a state it hasn't been tried from yet; whether that's
 * worth pruning anyway is KnownDeadEndCandidateFilter's job, using
 * WorldModel history.
 *
 * Also tracks WHEN (by execution order, not wall-clock time) each action
 * was last executed from a given state — see {@link #lastExecutedOrder}.
 * A state can be genuinely, fully exhausted (every action ever offered
 * from it has already been tried at some point — e.g. a login page
 * revisited after logging out, where the original login already tried
 * every field and the submit button) without that meaning nothing
 * useful can be done there anymore; re-authenticating is still the only
 * way forward. {@code CompositeCandidateFilter}'s deepest fallback uses
 * this ordering to retry whichever action has gone longest untried
 * (e.g. the submit button, last touched at the very start of the
 * mission) rather than just excluding literally the single most recent
 * repeat, which would otherwise leave two-or-more distinct actions free
 * to oscillate forever.
 */
public class ExecutionMemory {

    private final Map<String, Integer> lastExecutedOrder = new HashMap<>();
    private int nextOrder = 0;

    public boolean hasExecuted(String stateSignature, Action action) {
        return lastExecutedOrder.containsKey(key(stateSignature, action));
    }

    public void remember(String stateSignature, Action action) {
        lastExecutedOrder.put(key(stateSignature, action), nextOrder++);
    }

    /**
     * The execution order (lower = longer ago) this exact action was last
     * executed from this exact state, or {@code -1} if it never was —
     * i.e. treated as infinitely stale, always least-recently-used.
     */
    public int lastExecutedOrder(String stateSignature, Action action) {

        Integer order = lastExecutedOrder.get(key(stateSignature, action));

        return order == null ? -1 : order;
    }

    public void clear() {
        lastExecutedOrder.clear();
    }

    private String key(String stateSignature, Action action) {
        return stateSignature + "|" + action.type() + "|" + action.target();
    }
}
