package com.aegis.core.reasoning.factory;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.observation.ElementInfo;

public interface ActionFactory {

    Action create(
            ActionType actionType,
            ElementInfo element,
            String value,
            String reasoning,
            double confidence);

}