package com.aegis.core.report;

/**
 * Reporting v2 "Findings Dashboard" — groups BugClusters by what kind of
 * problem they are, not just their normalized fingerprint (Phase 6).
 * Deliberately limited to categories AEGIS actually has a detector for
 * today (see BrowserSignalAnomalyDetector / DefaultMissionEngine's
 * "Engine error" findings) — no ACCESSIBILITY or PERFORMANCE category,
 * because nothing in this project currently checks for either. Adding
 * those honestly means building the detector first, not inventing a
 * label for data that was never collected.
 */
public enum FindingCategory {

    JAVASCRIPT,

    NETWORK,

    NAVIGATION,

    TIMEOUT,

    STABILITY,

    OTHER

}
