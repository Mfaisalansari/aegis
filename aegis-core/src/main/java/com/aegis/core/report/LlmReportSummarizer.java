package com.aegis.core.report;

import com.aegis.core.bug.BugCluster;
import com.aegis.core.knowledge.InspectionCatalog;
import com.aegis.core.knowledge.InspectionFinding;
import com.aegis.core.knowledge.KnowledgeBase;
import com.aegis.core.knowledge.UxFinding;
import com.aegis.core.knowledge.UxFindingCatalog;
import com.aegis.core.llm.LlmChatClient;
import com.aegis.model.mission.MissionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Asks a real language model to do what the rule-based summary
 * structurally can't: read every individual finding's real detail (not
 * just counts) and synthesize across them — whether several findings
 * share a likely root cause, which single fix would resolve the most/
 * highest-impact issues, and anything genuinely notable a mechanical
 * count-and-template pass wouldn't surface. The rule-based fallback stays
 * deliberately mechanical (see {@code MissionReportData.computeFallbackPlainLanguageSummary})
 * so the gap between the two is real, not just a reworded version of the
 * same sentence. Same fallback discipline as every other LLM-backed class
 * in this project: any failure (network, timeout, empty response) falls
 * back to the rule-based paragraph rather than surfacing an error or
 * leaving the report blank.
 */
public class LlmReportSummarizer implements ReportSummarizer {

    private static final Logger log = LoggerFactory.getLogger(LlmReportSummarizer.class);

    private final LlmChatClient client;
    private final String appContext;

    public LlmReportSummarizer(LlmChatClient client) {
        this(client, null);
    }

    /**
     * {@code appContext} is the operator-authored Markdown context
     * document (see {@code MissionBuilder#appContext(String)}), when the
     * mission that produced this report had one configured — null/blank
     * when it didn't. This class's only caller, {@code MissionReportData}
     * (frozen), has no way to pass a new argument through {@link
     * #summarize}'s existing signature, so this is threaded in at
     * construction instead; the one call site is {@code Aegis.run(...)},
     * which already has the {@code Mission} in scope.
     */
    public LlmReportSummarizer(LlmChatClient client, String appContext) {
        this.client = client;
        this.appContext = appContext;
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

        return "You are a senior QA engineer explaining the results of an automated website test "
                + "to a non-technical stakeholder — a product manager or business owner who has "
                + "never read a bug report before. You will be given every individual finding from "
                + "the run, already described in plain words — your job is real analysis, not a "
                + "reworded list:\n"
                + "1. Look across every finding for signs several of them share one real, specific "
                + "cause — e.g. they name the exact same URL, the same domain, or the same broken "
                + "resource. If you find one, say so and describe (in your own words) which findings "
                + "it explains.\n"
                + "2. Only connect findings when the evidence actually supports it. Two findings are "
                + "NOT related just because they're both real problems, both happened on the same "
                + "page, or both sound similarly serious — e.g. a login error and an unlabeled button "
                + "are unrelated unless something concrete ties them together. If nothing shares a "
                + "real cause, say plainly that these look like separate, unrelated issues — do not "
                + "force a connection.\n"
                + "3. Identify the single fix that would resolve the most, or the most impactful, "
                + "findings at once — be specific about which findings it covers.\n"
                + "4. Call out anything genuinely surprising or worth a second look.\n"
                + "Write entirely in your own words. Never copy list formatting, dashes, brackets, "
                + "ALL_CAPS labels, or field names from the data you're given — describe every finding "
                + "as a sentence a non-technical person would say out loud. Never invent numbers or "
                + "positions for findings either (no \"finding 3\", no \"findings 8-25\") — the reader "
                + "cannot see a numbered list, only your paragraph, so a made-up number means nothing "
                + "to them. Refer to each finding by what it actually is (e.g. \"the login page's low-"
                + "contrast text\") or, for a group of similar ones, by an honest count and description "
                + "(e.g. \"about a dozen low-contrast text issues scattered across product pages\"). "
                + "Never use DOM, locator, "
                + "node, state, journey, contrast ratio, WCAG, or console/exception jargon — describe "
                + "user impact instead (e.g. \"some text may be hard to read for users with low "
                + "vision\"). Only reference facts actually given to you — never invent specifics. "
                + "Respond in plain text only: no markdown, no headings, no preamble like \"Sure, "
                + "here's...\". 6-9 sentences.";
    }

    private String userPrompt(
            String missionName, String missionGoal, MissionStatus status, ExplorationCoverage coverage,
            List<BugCluster> bugClusters, KnowledgeBase knowledgeBase, String recommendation, String ruleBasedFallback) {

        List<UxFinding> uxFindings = knowledgeBase.get(UxFindingCatalog.class).map(UxFindingCatalog::findings).orElse(List.of());
        List<InspectionFinding> inspectionFindings = knowledgeBase.get(InspectionCatalog.class).map(InspectionCatalog::findings).orElse(List.of());

        StringBuilder prompt = new StringBuilder();

        prompt.append("Mission: ").append(missionName).append('\n');
        prompt.append("Goal: ").append(missionGoal).append('\n');
        prompt.append("Result: ").append(status).append('\n');
        prompt.append("Coverage: ").append(String.format("%.0f%%", coverage.coveragePercent()))
                .append(" of interactive elements found were tried\n");

        if (appContext != null && !appContext.isBlank()) {
            prompt.append("\nContext about this application:\n").append(appContext).append('\n');
        }

        prompt.append('\n');

        if (bugClusters.isEmpty() && uxFindings.isEmpty() && inspectionFindings.isEmpty()) {
            prompt.append("No findings of any kind this run.\n");
        }

        prompt.append("Every finding this run, each on its own line as plain fact (not something to quote verbatim):\n");

        for (BugCluster cluster : bugClusters) {
            prompt.append("Issue, ").append(PlainLanguageGlossary.severityLabel(cluster.severity())).append(" importance: ")
                    .append(cluster.representativeSummary())
                    .append(". Happened ").append(cluster.occurrenceCount()).append(" time(s)");
            if (cluster.spansMultiplePages()) {
                prompt.append(" across these pages: ").append(String.join(", ", cluster.urls()));
            }
            prompt.append(".\n");
        }

        for (UxFinding finding : uxFindings) {
            prompt.append("Issue, ").append(PlainLanguageGlossary.severityLabel(finding.severity())).append(" importance: ")
                    .append(PlainLanguageGlossary.uxFindingLabel(finding.type())).append(" — ").append(finding.summary())
                    .append(". Where: ").append(finding.evidence()).append(".\n");
        }

        for (InspectionFinding finding : inspectionFindings) {
            prompt.append("Issue, ").append(PlainLanguageGlossary.severityLabel(finding.severity())).append(" importance: ")
                    .append(PlainLanguageGlossary.inspectionCheckLabel(finding.type())).append(" — ").append(finding.summary())
                    .append(". Where: ").append(finding.evidence()).append(".\n");
        }

        prompt.append("\nA mechanical, count-only summary already produced: \"").append(ruleBasedFallback).append("\"\n");
        prompt.append("Go beyond it — do the real analysis described above using the actual findings listed, "
                + "for a reader who has never seen this report before.");

        return prompt.toString();
    }
}
