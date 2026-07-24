package com.aegis.model.context;

import com.aegis.model.mission.Mission;

public class MissionContext {

    private final Mission mission;

    private final ExecutionState executionState;

    public MissionContext(Mission mission) {

        this.mission = mission;
        this.executionState = new ExecutionState();

    }

    public Mission getMission() {
        return mission;
    }

    public ExecutionState getExecutionState() {
        return executionState;
    }

}