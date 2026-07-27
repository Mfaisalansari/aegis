package com.aegis.core.report;

import com.aegis.core.bug.BugCluster;
import com.aegis.core.knowledge.KnowledgeBase;
import com.aegis.model.mission.MissionStatus;

import java.util.List;
import java.util.Map;

/**
 * Default, always-available ReportSummarizer: no model call, just the
 * rule-based fallback paragraph {@code MissionReportData} already
 * computes. What every report uses unless the plain-language summary is
 * explicitly opted into (see {@link LlmReportSummarizer}).
 */
public class RuleBasedReportSummarizer implements ReportSummarizer {

    @Override
    public String summarize(
            String missionName, String missionGoal, MissionStatus status,
            ExplorationCoverage coverage, List<BugCluster> bugClusters,
            Map<FindingCategory, List<BugCluster>> findingsByCategory,
            String recommendation, KnowledgeBase knowledgeBase,
            String ruleBasedFallback) {

        return ruleBasedFallback;
    }
}
