package com.aegis.core.bug;

import java.util.List;

/**
 * Default, always-available RecommendationEngine: no model call, just
 * the rule-based fallback text MissionReportData already computes. What
 * every report uses unless AI recommendations are explicitly opted into
 * (see LlmRecommendationEngine).
 */
public class RuleBasedRecommendationEngine implements RecommendationEngine {

    @Override
    public String recommend(List<BugCluster> clusters, String ruleBasedFallback) {
        return ruleBasedFallback;
    }
}
