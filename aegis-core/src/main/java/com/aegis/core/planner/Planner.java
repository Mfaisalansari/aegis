package com.aegis.core.planner;

import com.aegis.model.action.Action;
import com.aegis.model.context.MissionContext;

public interface Planner {

    Action plan(MissionContext context);

}