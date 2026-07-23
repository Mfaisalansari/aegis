package com.aegis.model.action;

public record Action(
        ActionType type,
        String target,
        String value
) {
}