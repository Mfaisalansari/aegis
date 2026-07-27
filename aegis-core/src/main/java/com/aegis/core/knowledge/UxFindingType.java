package com.aegis.core.knowledge;

/**
 * Deliberately limited to what {@link UxAnalysisCatalogProvider} actually
 * detects today — same "only what's real" discipline as {@code
 * FindingCategory} in {@code com.aegis.core.report}.
 */
public enum UxFindingType {

    BACKTRACKING,

    JOURNEY_DIVERGENCE,

    NAVIGATION_FRICTION,

    MISSING_ACCESSIBLE_NAME

}
