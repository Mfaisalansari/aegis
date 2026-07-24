package com.aegis.core.bug;

import com.aegis.core.llm.LlmChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Phase 8 "Recommendation engine": asks a real language model to look
 * across an entire mission's BugClusters at once (not one at a time,
 * like LlmBugExplainer) and write a short, prioritized recommendation —
 * the "so what do I actually do first" a reader wants above the raw
 * findings list.
 *
 * Same fallback discipline as LlmBugExplainer/LlmActionScorer: any
 * failure — network, timeout, empty response — falls back to the
 * rule-based recommendation rather than surfacing an error or leaving
 * the report blank. Skips the model call entirely when there are no
 * clusters to recommend against; there's nothing useful for it to add.
 */
public class LlmRecommendationEngine implements RecommendationEngine {

    private static final Logger log = LoggerFactory.getLogger(LlmRecommendationEngine.class);

    private final LlmChatClient client;

    public LlmRecommendationEngine(LlmChatClient client) {
        this.client = client;
    }

    @Override
    public String recommend(List<BugCluster> clusters, String ruleBasedFallback) {

        if (clusters == null || clusters.isEmpty()) {
            return ruleBasedFallback;
        }

        try {

            String response = client.complete(systemPrompt(), userPrompt(clusters, ruleBasedFallback)).strip();

            return response.isEmpty() ? ruleBasedFallback : response;

        } catch (Exception e) {
            log.warn("LLM recommendation failed ({}); falling back to rule-based recommendation.", e.toString());
            return ruleBasedFallback;
        }
    }

    private String systemPrompt() {

        return "You are a QA lead triaging a list of bugs found during automated exploratory testing. Given a "
                + "list of bug clusters (severity, summary, occurrence count, pages affected), write a short "
                + "(2-4 sentence) prioritized recommendation for what an engineering team should look at first "
                + "and why. Only reference bugs actually in the list — never invent additional issues or "
                + "specifics not present in the data. Respond with plain text only: no markdown, no JSON, no "
                + "preamble like \"Sure, here's...\".";
    }

    private String userPrompt(List<BugCluster> clusters, String ruleBasedFallback) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("Bugs found this mission, already sorted most severe first:\n");

        int index = 1;

        for (BugCluster cluster : clusters) {

            prompt.append(index++).append(") [").append(cluster.severity()).append("] ")
                    .append(cluster.representativeSummary())
                    .append(" — occurred ").append(cluster.occurrenceCount()).append(" time(s)");

            if (cluster.spansMultiplePages()) {
                prompt.append(", seen on ").append(cluster.urls().size()).append(" different pages");
            }

            prompt.append('\n');
        }

        prompt.append("\nA rule-based summary already produced: \"").append(ruleBasedFallback).append("\"\n");
        prompt.append("Write a more useful, specific recommendation in your own words.");

        return prompt.toString();
    }
}
