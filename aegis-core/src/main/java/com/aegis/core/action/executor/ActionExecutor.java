package com.aegis.core.action.executor;

import com.aegis.core.action.handler.ActionHandler;
import com.aegis.core.executor.Executor;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ActionExecutor implements Executor {

    private final Map<ActionType, ActionHandler> handlers =
            new EnumMap<>(ActionType.class);

    public ActionExecutor(List<ActionHandler> handlers) {

        Objects.requireNonNull(handlers);

        handlers.forEach(handler ->
                this.handlers.put(handler.supports(), handler));

    }

    public void execute(Action action, MissionContext context) {

        Objects.requireNonNull(action);

        ActionHandler handler = handlers.get(action.type());

        if (handler == null) {

            throw new IllegalStateException(
                    "No handler registered for " + action.type());

        }

        handler.execute(action, context);
    }

}