package com.aegis.core.reasoning.evaluator;

import com.aegis.model.context.MissionContext;
import com.aegis.model.reasoning.CandidateAction;

public interface CandidateEvaluator {

    double score(
            MissionContext context,
            CandidateAction candidate
    );

}