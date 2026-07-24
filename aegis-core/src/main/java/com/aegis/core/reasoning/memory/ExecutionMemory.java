package com.aegis.core.reasoning.memory;

import com.aegis.model.action.Action;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks which actions have already been executed, scoped per state (see
 * StateSignature) rather than globally. An exact repeat of the same
 * action from the same state is blocked — that's what actually prevents
 * pointless looping. A locator that's genuinely present on more than one
 * page (a persistent nav link, say) can still be tried again once
 * reached from a state it hasn't been tried from yet; whether that's
 * worth pruning anyway is KnownDeadEndCandidateFilter's job, using
 * WorldModel history.
 */
public class ExecutionMemory {

    private final Set<String> executedActions = new HashSet<>();

    public boolean hasExecuted(String stateSignature, Action action) {
        return executedActions.contains(key(stateSignature, action));
    }

    public void remember(String stateSignature, Action action) {
        executedActions.add(key(stateSignature, action));
    }

    public void clear() {
        executedActions.clear();
    }

    private String key(String stateSignature, Action action) {
        return stateSignature + "|" + action.type() + "|" + action.target();
    }
}
