package com.aegis.core.report;

import java.time.Instant;

/**
 * Reporting v2 "Mission Timeline" — a single moment in a finished
 * mission's history, reconstructed purely from data already recorded in
 * ExecutionState (Observations, ReasoningSteps, Findings) plus this
 * mission's Experiences. Nothing here is captured live during
 * execution; it's all derived after the fact from timestamps that
 * already exist (Observation.capturedAt, ReasoningStep.timestamp,
 * Experience.createdAt, Finding.detectedAt) — reporting reconstructs a
 * timeline, it doesn't get fed one.
 *
 * screenshotPath is Stage 3's "screenshot hook": always null today —
 * actually capturing a screenshot at the right moment would mean
 * instrumenting the live execution path (Observer or the browser
 * layer), both frozen — but every event already has a well-defined
 * moment and a place to put a path once that capture mechanism exists.
 * The 4-arg constructor is what every event is built with today; the
 * 5-arg one is the hook.
 */
public record TimelineEvent(
        Instant timestamp,
        TimelineEventKind kind,
        String headline,
        String detail,
        String screenshotPath
) {

    public TimelineEvent(Instant timestamp, TimelineEventKind kind, String headline, String detail) {
        this(timestamp, kind, headline, detail, null);
    }
}
