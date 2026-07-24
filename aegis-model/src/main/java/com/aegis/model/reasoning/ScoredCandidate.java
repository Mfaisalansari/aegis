package com.aegis.model.reasoning;

public record ScoredCandidate(

        CandidateAction candidate,

        double baseScore,

        double learningAdjustment,

        double finalScore

) {

}