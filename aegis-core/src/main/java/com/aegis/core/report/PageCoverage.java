package com.aegis.core.report;

/**
 * Element coverage (see ExplorationCoverage) broken down per page, rather
 * than one mission-wide number — "the login page got 100% coverage but
 * the inventory page only got 15%" is a lot more actionable than a
 * single blended percentage. Still elements-on-this-page coverage, not a
 * claim about what fraction of the whole site this page represents.
 */
public record PageCoverage(
        String url,
        int elementsDiscovered,
        int elementsInteracted,
        double coveragePercent
) {
}
