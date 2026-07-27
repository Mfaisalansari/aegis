package com.aegis.core.knowledge;

import java.util.List;

/**
 * Operator-declared tuning for the Page Inspection Layer's live capture
 * and checks — a {@code knowledge.yml} sibling of {@code nodes:}/{@code
 * flows:}/{@code journeys:}, same file, same loader, but a different kind
 * of declaration (how much to capture and at what threshold, not business
 * meaning). Everything defaults to a safe, low-noise, capture-off-by-
 * default posture via {@link #disabled()} — {@code captureDom} is the
 * only thing that costs anything per-state, so it's the one flag that
 * must be explicitly turned on.
 */
public record InspectionConfig(
        boolean captureDom,
        boolean consoleWarnings,
        double contrastThreshold,
        boolean probeLinks,
        List<String> noiseDenyPatterns
) {

    public InspectionConfig {
        noiseDenyPatterns = noiseDenyPatterns == null ? List.of() : List.copyOf(noiseDenyPatterns);
    }

    public static InspectionConfig disabled() {
        return new InspectionConfig(false, false, 4.5, false, List.of());
    }
}
