package com.aegis.core.mission;

import com.aegis.model.mission.Mission;

public interface MissionPlanner {

    MissionPlan plan(Mission mission);

}
