package com.aegis.core.impl.planner;

import com.aegis.core.decision.DecisionEngine;
import com.aegis.core.planner.Planner;
import com.aegis.model.action.Action;
import com.aegis.model.context.MissionContext;

public class MockPlanner implements Planner {

    private final DecisionEngine decisionEngine;

    public MockPlanner(DecisionEngine decisionEngine) {
        this.decisionEngine = decisionEngine;
    }

    @Override
    public Action plan(MissionContext context) {
        return decisionEngine.decide(context);
    }
}