package com.aegis.model.action;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record Action(

        UUID id,

        ActionType type,

        String target,

        String value,

        String reasoning,

        double confidence,

        String expectedOutcome,

        Duration timeout,

        Instant createdAt,

        String elementTag

) {
}