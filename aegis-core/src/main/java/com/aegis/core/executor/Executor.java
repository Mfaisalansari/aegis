package com.aegis.core.executor;

import com.aegis.model.action.Action;
import com.aegis.model.context.MissionContext;

public interface Executor {

    void execute(Action action, MissionContext context);

}