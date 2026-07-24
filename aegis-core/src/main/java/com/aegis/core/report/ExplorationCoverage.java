package com.aegis.core.report;

/**
 * How much of what AEGIS discovered it actually exercised: every distinct
 * interactive element locator seen across every observation this mission,
 * versus how many of those were the target of an executed action.
 * Deliberately narrow — this is elements-on-pages-actually-visited
 * coverage, not site-wide page coverage (that would need a sitemap or
 * DOM diffing this project doesn't have).
 */
public record ExplorationCoverage(
        int elementsDiscovered,
        int elementsInteracted,
        double coveragePercent
) {
}
