package com.aegis.core.reasoning.learning;

import com.aegis.model.action.Action;

import java.util.Map;

public record LearningResult(Map<Action, Double> scoreAdjustments) {

    public static LearningResult empty() {
        return new LearningResult(Map.of());
    }

    public double adjustmentFor(Action action) {
        return scoreAdjustments.getOrDefault(action, 0.0);
    }

    public boolean isEmpty() {
        return scoreAdjustments.isEmpty();
    }
}