package com.aegis.core.bug;

import java.util.List;

/**
 * Produces one prioritized, actionable recommendation across an entire
 * mission's BugClusters — distinct from BugExplainer, which explains one
 * cluster at a time. Always given the rule-based fallback alongside the
 * clusters, so an implementation that can't or won't do better has
 * something correct to return instead of nothing.
 */
public interface RecommendationEngine {

    String recommend(List<BugCluster> clusters, String ruleBasedFallback);

}
