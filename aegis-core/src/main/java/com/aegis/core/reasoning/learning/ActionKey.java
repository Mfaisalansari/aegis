package com.aegis.core.reasoning.learning;

import com.aegis.model.action.Action;

/**
 * A stable identity for an Action, independent of the random id and
 * timestamp every Action instance carries. Two candidates of the same
 * type against the same target are "the same action" for learning
 * purposes even though a fresh Action object (new UUID, new createdAt)
 * gets generated every time that candidate reappears — grouping or
 * looking up by the raw Action record (whose equals() includes id and
 * createdAt) would otherwise never match the same logical action twice.
 * Same convention ExecutionMemory already uses for the same reason.
 */
final class ActionKey {

    private ActionKey() {
    }

    static String of(Action action) {
        return action.type() + "|" + action.target();
    }
}
