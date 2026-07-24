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
 * screenshotDataUri is the one exception: real screenshots (Phase 9+
 * follow-up), captured live by SelfHealingBrowser (the browser layer
 * isn't frozen, unlike Observer) after every EXECUTION event's action,
 * matched back to the nearest EXECUTION event by timestamp — see
 * MissionReportData.buildTimeline. A full "data:image/png;base64,..."
 * URI, not a filesystem path, so the HTML report stays self-contained
 * with no external assets. Every other event kind (observations,
 * reasoning, findings, mission start/finish) has no direct browser
 * action to capture against, so this stays null for those — the 4-arg
 * constructor is what they're built with.
 */
public record TimelineEvent(
        Instant timestamp,
        TimelineEventKind kind,
        String headline,
        String detail,
        String screenshotDataUri
) {

    public TimelineEvent(Instant timestamp, TimelineEventKind kind, String headline, String detail) {
        this(timestamp, kind, headline, detail, null);
    }
}
