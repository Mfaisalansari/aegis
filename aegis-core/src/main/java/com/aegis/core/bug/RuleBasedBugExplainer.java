package com.aegis.core.bug;

/**
 * Default, always-available BugExplainer: no model call, just the
 * rule-based fallback text MissionReportData already computes. This is
 * what every report uses unless AI bug explanations are explicitly
 * opted into (see LlmBugExplainer) — no network dependency, no cost,
 * identical output every run.
 */
public class RuleBasedBugExplainer implements BugExplainer {

    @Override
    public String explain(BugCluster cluster, String ruleBasedFallback) {
        return ruleBasedFallback;
    }
}
