package com.aegis.core.reasoning.memory;

import com.aegis.model.action.Action;

import java.util.HashSet;
import java.util.Set;

public class ExecutionMemory {

    private final Set<String> executedActions = new HashSet<>();

    public boolean hasExecuted(Action action) {
        return executedActions.contains(key(action));
    }

    public void remember(Action action) {
        executedActions.add(key(action));
    }

    public void clear() {
        executedActions.clear();
    }

    private String key(Action action) {
        return action.type() + "|" + action.target();
    }
}