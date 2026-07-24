package com.aegis.core.engine;

import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionResult;

public interface MissionEngine {

    MissionResult execute(Mission mission);

}