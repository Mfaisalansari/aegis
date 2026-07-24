package com.aegis.core.reasoning.scorer;

import com.aegis.model.context.MissionContext;
import com.aegis.model.reasoning.CandidateAction;

import java.util.List;

public interface ActionScorer {

    /**
     * @param context    the full mission context (goal, parameters,
     *                   history, findings so far) — most strategies
     *                   ignore it and decide from the candidate list
     *                   alone, but a context-aware strategy (e.g. an
     *                   LLM-backed one) needs to know what the mission
     *                   is actually trying to achieve.
     * @param candidates the candidate actions to choose from
     */
    CandidateAction choose(MissionContext context, List<CandidateAction> candidates);

}
