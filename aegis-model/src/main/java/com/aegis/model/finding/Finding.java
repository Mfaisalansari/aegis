package com.aegis.model.finding;

import java.time.Instant;

public record Finding(
        FindingSeverity severity,
        String summary,
        String url,
        Instant detectedAt
) {
}
