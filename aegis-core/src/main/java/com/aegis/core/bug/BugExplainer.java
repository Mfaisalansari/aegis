package com.aegis.core.bug;

/**
 * Produces a natural-language explanation for a BugCluster. Always given
 * the rule-based fallback text alongside the cluster, so an
 * implementation that can't or won't do better (no model configured, a
 * network failure) has something correct to return instead of nothing.
 */
public interface BugExplainer {

    String explain(BugCluster cluster, String ruleBasedFallback);

}
