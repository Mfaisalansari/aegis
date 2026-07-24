package com.aegis.core.resilience;

import java.time.Instant;

/**
 * A single screenshot captured by {@link SelfHealingBrowser} after an
 * action, paired with when it was taken. Kept as raw PNG bytes here —
 * base64/data-URI encoding is a rendering concern, done at report-build
 * time (see {@code MissionReportData.buildTimeline}), not here.
 */
public record ScreenshotSample(Instant capturedAt, byte[] pngBytes) {
}
