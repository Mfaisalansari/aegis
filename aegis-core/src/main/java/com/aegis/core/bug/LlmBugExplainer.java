package com.aegis.core.bug;

import com.aegis.core.llm.LlmChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase 8 "Bug explanation": asks a real language model to write a short
 * triage note for a BugCluster instead of relying only on the fixed,
 * per-signal-type sentence MissionReportData.explain() produces.
 *
 * Same fallback discipline as LlmActionScorer: a network failure, a
 * timeout, or an empty response all fall back to the rule-based
 * explanation rather than surfacing an error or blank text in the
 * report. The model is explicitly told not to invent specifics beyond
 * what it's given — this is meant to read the same evidence a human
 * triager would and phrase it more usefully, not to speculate about a
 * root cause it has no data for.
 */
public class LlmBugExplainer implements BugExplainer {

    private static final Logger log = LoggerFactory.getLogger(LlmBugExplainer.class);

    private final LlmChatClient client;

    public LlmBugExplainer(LlmChatClient client) {
        this.client = client;
    }

    @Override
    public String explain(BugCluster cluster, String ruleBasedFallback) {

        try {

            String response = client.complete(systemPrompt(), userPrompt(cluster, ruleBasedFallback)).strip();

            return response.isEmpty() ? ruleBasedFallback : response;

        } catch (Exception e) {
            log.warn("LLM bug explanation failed ({}); falling back to rule-based explanation.", e.toString());
            return ruleBasedFallback;
        }
    }

    private String systemPrompt() {

        return "You are a QA engineer writing a short triage note for another engineer about a bug found during "
                + "automated exploratory testing. Given the bug's signal type and message, how many times it "
                + "happened, and which page(s) it occurred on, write 1-3 plain-English sentences explaining what "
                + "likely went wrong and why it matters. Only use the information given — never invent specifics "
                + "(stack traces, root causes, ticket numbers) that aren't present in the data. Respond with "
                + "plain text only: no markdown, no JSON, no preamble like \"Sure, here's...\".";
    }

    private String userPrompt(BugCluster cluster, String ruleBasedFallback) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("Bug summary: ").append(cluster.representativeSummary()).append('\n');
        prompt.append("Severity: ").append(cluster.severity()).append('\n');
        prompt.append("Occurred ").append(cluster.occurrenceCount()).append(" time(s)\n");
        prompt.append("Seen on ").append(cluster.urls().size()).append(" distinct page(s): ")
                .append(String.join(", ", cluster.urls())).append('\n');
        prompt.append("A rule-based classifier already produced this generic explanation: \"")
                .append(ruleBasedFallback).append("\"\n");
        prompt.append("Write something more specific to this bug's actual details, in your own words.");

        return prompt.toString();
    }
}
