package com.aegis.core.report;

import com.aegis.core.bug.BugCluster;
import com.aegis.core.knowledge.InspectionCatalog;
import com.aegis.core.knowledge.KnowledgeBase;
import com.aegis.core.knowledge.UxFindingCatalog;
import com.aegis.core.llm.LlmChatClient;
import com.aegis.model.mission.MissionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Asks a real language model to rewrite the whole-mission rule-based
 * summary in plain, non-technical language — a report a business
 * stakeholder can read without decoding severity enums or knowing what a
 * "contrast ratio"/"locator"/"node" is. Same fallback discipline as every
 * other LLM-backed class in this project: any failure (network, timeout,
 * empty response) falls back to the rule-based paragraph rather than
 * surfacing an error or leaving the report blank.
 */
public class LlmReportSummarizer implements ReportSummarizer {

    private static final Logger log = LoggerFactory.getLogger(LlmReportSummarizer.class);

    private final LlmChatClient client;

    public LlmReportSummarizer(LlmChatClient client) {
        this.client = client;
    }

    @Override
    public String summarize(
            String missionName, String missionGoal, MissionStatus status,
            ExplorationCoverage coverage, List<BugCluster> bugClusters,
            Map<FindingCategory, List<BugCluster>> findingsByCategory,
            String recommendation, KnowledgeBase knowledgeBase,
            String ruleBasedFallback) {

        try {

            String response = client.complete(
                    systemPrompt(),
                    userPrompt(missionName, missionGoal, status, coverage, bugClusters, knowledgeBase, recommendation, ruleBasedFallback)
            ).strip();

            return response.isEmpty() ? ruleBasedFallback : response;

        } catch (Exception e) {
            log.warn("LLM report summary failed ({}); falling back to rule-based summary.", e.toString());
            return ruleBasedFallback;
        }
    }

    private String systemPrompt() {

        return "You are explaining the results of an automated website test to a non-technical "
                + "stakeholder — a product manager or business owner who has never read a bug report "
                + "before. Rewrite the given technical summary in plain, friendly language: what was "
                + "tested, whether it worked, what's broken in terms of what a real user would "
                + "actually experience (not technical causes), and what to do next. Never use "
                + "technical terms like DOM, locator, node, state, journey, contrast ratio, WCAG, "
                + "console error, or raw severity words like CRITICAL/HIGH/MEDIUM/LOW — describe "
                + "impact instead (e.g. \"some text may be hard to read for users with low vision\" "
                + "rather than \"LOW_CONTRAST severity MEDIUM\"). Only reference facts actually given "
                + "to you — never invent specifics. Respond in plain text only: no markdown, no "
                + "headings, no preamble like \"Sure, here's...\". Keep it to 4-6 sentences.";
    }

    private String userPrompt(
            String missionName, String missionGoal, MissionStatus status, ExplorationCoverage coverage,
            List<BugCluster> bugClusters, KnowledgeBase knowledgeBase, String recommendation, String ruleBasedFallback) {

        int uxQualityFindings = knowledgeBase.get(UxFindingCatalog.class).map(c -> c.findings().size()).orElse(0);
        int pageInspectionFindings = knowledgeBase.get(InspectionCatalog.class).map(c -> c.findings().size()).orElse(0);

        StringBuilder prompt = new StringBuilder();

        prompt.append("Mission: ").append(missionName).append('\n');
        prompt.append("Goal: ").append(missionGoal).append('\n');
        prompt.append("Result: ").append(status).append('\n');
        prompt.append("Coverage: ").append(String.format("%.0f%%", coverage.coveragePercent()))
                .append(" of interactive elements found were tried\n");
        prompt.append("Bugs found: ").append(bugClusters.size()).append('\n');
        prompt.append("Navigation/experience issues found: ").append(uxQualityFindings).append('\n');
        prompt.append("Page defects found (broken links, console errors, accessibility/contrast issues): ")
                .append(pageInspectionFindings).append('\n');
        prompt.append("Recommendation: ").append(recommendation).append('\n');

        prompt.append("\nA rule-based summary already produced: \"").append(ruleBasedFallback).append("\"\n");
        prompt.append("Rewrite it in plain language for a non-technical reader, keeping the same facts.");

        return prompt.toString();
    }
}
