package com.aegis.core.reasoning;

import com.aegis.model.action.Action;
import com.aegis.model.context.MissionContext;

public interface GoalReasoner {

    Action reason(MissionContext context);

}