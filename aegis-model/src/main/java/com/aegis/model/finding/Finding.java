package com.aegis.model.finding;

public record Finding(
        FindingSeverity severity,
        String summary
) {
}