package com.aegis.model.reasoning;

import com.aegis.model.action.ActionType;

public record NavigationEdge(
        String fromState,
        ActionType actionType,
        String actionTarget,
        String toState
) {
}
