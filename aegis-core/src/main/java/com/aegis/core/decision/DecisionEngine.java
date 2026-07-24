package com.aegis.core.decision;

import com.aegis.model.action.Action;
import com.aegis.model.context.MissionContext;

public interface DecisionEngine {

    Action decide(MissionContext context);

}