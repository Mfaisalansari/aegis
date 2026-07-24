package com.aegis.core.controller;

import com.aegis.model.context.MissionContext;

public interface MissionController {

    boolean shouldContinue(MissionContext context);

}