package com.aegis.core.action.handler;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;

/**
 * Contract for handling a specific type of action.
 *
 * Each implementation is responsible for exactly one ActionType.
 */
public interface ActionHandler {

    /**
     * Returns the ActionType supported by this handler.
     *
     * @return supported action type
     */
    ActionType supports();

    /**
     * Executes the supplied action.
     *
     * @param action  action to execute
     * @param context current mission context
     */
    void execute(Action action, MissionContext context);
}