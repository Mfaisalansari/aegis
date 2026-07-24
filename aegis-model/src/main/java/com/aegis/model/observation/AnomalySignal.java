package com.aegis.model.observation;

import java.time.Instant;

/**
 * A raw, unclassified signal captured from the browser — a console error,
 * an uncaught page exception, a failed network request, a crash. Severity
 * and meaning are assigned later by an AnomalyDetector; this is just the
 * fact that something happened.
 */
public record AnomalySignal(
        String type,
        String detail,
        String url,
        Instant occurredAt
) {
}
