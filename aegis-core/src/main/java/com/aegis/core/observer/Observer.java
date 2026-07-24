package com.aegis.core.observer;

import com.aegis.model.context.MissionContext;

public interface Observer {

    void observe(MissionContext context);

}