package com.aegis.core.reasoning;

import com.aegis.core.reasoning.filter.CandidateFilter;
import com.aegis.core.reasoning.generator.CandidateActionGenerator;
import com.aegis.core.reasoning.scorer.ActionScorer;
import com.aegis.core.reasoning.scorer.ActionScorerRegistry;
import com.aegis.model.action.Action;
import com.aegis.model.context.MissionContext;
import com.aegis.model.reasoning.CandidateAction;
import com.aegis.model.reasoning.ReasoningStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

public class RuleBasedGoalReasoner implements GoalReasoner {

    private static final Logger log =
            LoggerFactory.getLogger(RuleBasedGoalReasoner.class);

    private final CandidateActionGenerator generator;
    private final CandidateFilter filter;
    private final ActionScorerRegistry scorerRegistry;

    public RuleBasedGoalReasoner(
            CandidateActionGenerator generator,
            CandidateFilter filter,
            ActionScorerRegistry scorerRegistry) {

        this.generator = generator;
        this.filter = filter;
        this.scorerRegistry = scorerRegistry;
    }

    @Override
    public Action reason(MissionContext context) {

        List<CandidateAction> candidates = generator.generate(context);

        candidates = filter.filter(context, candidates);

        String strategyKey = scorerRegistry.resolveKey(context);
        ActionScorer scorer = scorerRegistry.resolve(context);

        CandidateAction best = scorer.choose(context, candidates);

        context.getExecutionState().addReasoningStep(
                new ReasoningStep(
                        context.getExecutionState()
                                .getReasoningSteps()
                                .size() + 1,
                        candidates,
                        best,
                        Instant.now(),
                        strategyKey
                )
        );

        return best.action();
    }
}