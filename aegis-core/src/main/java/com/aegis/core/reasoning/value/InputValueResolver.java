package com.aegis.core.reasoning.value;

import com.aegis.model.context.MissionContext;
import com.aegis.model.observation.ElementInfo;

public interface InputValueResolver {

    String resolve(
            MissionContext context,
            ElementInfo element
    );

}