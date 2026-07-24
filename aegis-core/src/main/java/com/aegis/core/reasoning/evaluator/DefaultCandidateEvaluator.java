package com.aegis.core.reasoning.evaluator;

import com.aegis.model.context.MissionContext;
import com.aegis.model.reasoning.CandidateAction;

public class DefaultCandidateEvaluator implements CandidateEvaluator {

    @Override
    public double score(
            MissionContext context,
            CandidateAction candidate) {

        // Sprint 1
        return candidate.confidence();
    }
}