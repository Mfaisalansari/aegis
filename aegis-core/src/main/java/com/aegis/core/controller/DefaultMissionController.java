package com.aegis.core.controller;

import com.aegis.model.context.MissionContext;

public class DefaultMissionController implements MissionController {

    private static final String PARAMETER_KEY = "maxIterations";
    private static final int DEFAULT_MAX_ITERATIONS = 10;

    @Override
    public boolean shouldContinue(MissionContext context) {

        return context.getExecutionState().getIteration() < maxIterations(context);

    }

    private int maxIterations(MissionContext context) {

        String configured = context.getMission().parameter(PARAMETER_KEY);

        if (configured == null || configured.isBlank()) {
            return DEFAULT_MAX_ITERATIONS;
        }

        try {
            return Integer.parseInt(configured.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_ITERATIONS;
        }
    }
}
