package com.aegis.core.reasoning.scorer;

import com.aegis.model.context.MissionContext;
import com.aegis.model.reasoning.CandidateAction;

import java.util.Comparator;
import java.util.List;

public class HighestConfidenceActionScorer implements ActionScorer {

    @Override
    public CandidateAction choose(MissionContext context, List<CandidateAction> candidates) {

        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("No candidate actions available.");
        }

        return candidates.stream()
                .max(Comparator.comparingDouble(CandidateAction::confidence))
                .orElseThrow(() ->
                        new IllegalStateException("No candidate actions available."));
    }
}