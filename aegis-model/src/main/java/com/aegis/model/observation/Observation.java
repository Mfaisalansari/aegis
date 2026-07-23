package com.aegis.model.observation;

import java.time.Instant;

public record Observation(
        Instant timestamp,
        String description
) {
}