package com.aegis.model.reasoning;

import com.aegis.model.action.Action;

public record CandidateAction(

        Action action,

        double confidence,

        String reasoning
) {
}