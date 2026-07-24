package com.aegis.core.reasoning.learning;

import com.aegis.model.action.Action;

public record PatternStatistics(
        Action action,
        long totalExecutions,
        long successfulExecutions,
        long failedExecutions,
        double successRate
) {
}