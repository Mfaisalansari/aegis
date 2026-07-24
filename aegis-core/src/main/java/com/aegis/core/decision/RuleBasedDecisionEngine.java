package com.aegis.core.decision;

import com.aegis.core.reasoning.GoalReasoner;
import com.aegis.model.action.Action;
import com.aegis.model.context.MissionContext;

public class RuleBasedDecisionEngine implements DecisionEngine {

    private final GoalReasoner goalReasoner;

    public RuleBasedDecisionEngine(GoalReasoner goalReasoner) {
        this.goalReasoner = goalReasoner;
    }

    @Override
    public Action decide(MissionContext context) {

        return goalReasoner.reason(context);

    }
}