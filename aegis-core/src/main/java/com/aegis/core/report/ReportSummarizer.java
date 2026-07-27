package com.aegis.core.report;

import com.aegis.core.bug.BugCluster;
import com.aegis.core.knowledge.KnowledgeBase;
import com.aegis.model.mission.MissionStatus;

import java.util.List;
import java.util.Map;

/**
 * Produces one plain-language paragraph summarizing an entire mission
 * report for a non-technical reader — distinct from {@link
 * com.aegis.core.bug.BugExplainer} (one cluster at a time) and {@link
 * com.aegis.core.bug.RecommendationEngine} (a prioritized next step): this
 * is the "what happened, in plain English" a reader wants before any of
 * that. Always given the rule-based fallback alongside the raw data, so an
 * implementation that can't or won't do better has something correct to
 * return instead of nothing. A flat parameter list rather than a {@code
 * MissionReportData} — the finished record doesn't exist yet at the one
 * call site (see {@code MissionReportData.from}), it's still being built
 * from these same locals.
 */
public interface ReportSummarizer {

    String summarize(
            String missionName, String missionGoal, MissionStatus status,
            ExplorationCoverage coverage, List<BugCluster> bugClusters,
            Map<FindingCategory, List<BugCluster>> findingsByCategory,
            String recommendation, KnowledgeBase knowledgeBase,
            String ruleBasedFallback);

}
