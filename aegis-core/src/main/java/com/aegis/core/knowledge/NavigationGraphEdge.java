package com.aegis.core.knowledge;

/**
 * One observed directed transition between two nodes this run — {@code
 * fromNodeKey} to {@code toNodeKey}, with how many times that exact
 * transition happened. Self-transitions (the same node re-observed back
 * to back) are never recorded — see {@link NavigationGraphCatalogProvider}.
 *
 * <b>Not the same type as {@code com.aegis.model.reasoning.NavigationEdge}</b>
 * (the frozen edge type {@code MissionReportData} builds, carrying the
 * triggering {@code ActionType}/target) — deliberately named differently
 * to avoid confusion between the two. This one is a plain node-key-to-
 * node-key fact for the Knowledge layer; that one is action-attributed
 * and lives in the frozen reporting pipeline.
 */
public record NavigationGraphEdge(String fromNodeKey, String toNodeKey, int traversalCount) {
}
