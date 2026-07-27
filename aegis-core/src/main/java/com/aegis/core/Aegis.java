package com.aegis.core;

import com.aegis.core.browser.BrowserConfig;
import com.aegis.core.bug.BugExplainer;
import com.aegis.core.bug.LlmBugExplainer;
import com.aegis.core.bug.LlmRecommendationEngine;
import com.aegis.core.bug.RecommendationEngine;
import com.aegis.core.bug.RuleBasedBugExplainer;
import com.aegis.core.bug.RuleBasedRecommendationEngine;
import com.aegis.core.engine.EngineFactory;
import com.aegis.core.knowledge.KnowledgeConfig;
import com.aegis.core.knowledge.SignalLog;
import com.aegis.core.llm.OpenAiCompatibleChatClient;
import com.aegis.core.mission.LlmMissionPlanner;
import com.aegis.core.mission.MissionPlan;
import com.aegis.core.mission.MissionPlanner;
import com.aegis.core.mission.RuleBasedMissionPlanner;
import com.aegis.core.plugin.ReportRenderer;
import com.aegis.core.report.ExplainabilityReportGenerator;
import com.aegis.core.report.HtmlExplainabilityReportGenerator;
import com.aegis.core.report.JsonReportGenerator;
import com.aegis.core.report.LlmReportSummarizer;
import com.aegis.core.report.MissionReportData;
import com.aegis.core.report.ReportSummarizer;
import com.aegis.core.report.RuleBasedReportSummarizer;
import com.aegis.core.resilience.ScreenshotSample;
import com.aegis.model.experience.Experience;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * AEGIS's public entry point: give it a {@link Mission}, get back the
 * outcome and all three report formats. This is the v1.0 stable API — see
 * the "Public API" section of README.MD for exactly what that promise
 * covers and what stays free to change (everything else — EngineFactory's
 * internal wiring, the Stable Components list's interfaces, and so on —
 * is architecture, not a versioned external contract).
 *
 * Everything below mirrors what {@code aegis-launcher}'s MissionRunner has
 * always done by hand; this is that same flow promoted into aegis-core so
 * a consumer only needs this module, not the launcher's example {@code
 * *Main} classes, to embed AEGIS.
 */
public final class Aegis {

    private Aegis() {
    }

    public static AegisReport run(Mission mission) {
        return run(mission, BrowserConfig.defaults());
    }

    /** Same as {@link #run(Mission)}, with control over browser engine/headless mode instead of the chromium/headed default. */
    public static AegisReport run(Mission mission, BrowserConfig browserConfig) {
        return run(mission, browserConfig, KnowledgeConfig.empty());
    }

    /**
     * Same as {@link #run(Mission, BrowserConfig)}, plus an organization-
     * declared {@link KnowledgeConfig} — node/flow/journey naming
     * overrides, and Page Inspection Layer tuning ({@link
     * KnowledgeConfig#inspection()}). Drives both halves from one object:
     * {@code inspection} decides what the engine captures live *during*
     * the mission (passed to {@link EngineFactory#create(Mission,
     * BrowserConfig, com.aegis.core.knowledge.InspectionConfig)}), and the
     * whole config decides how the resulting report labels/groups what
     * was found. Pass {@link KnowledgeConfig#empty()} (what the 2-arg
     * overload above does) for auto-generated names and inspection
     * capture disabled.
     */
    public static AegisReport run(Mission mission, BrowserConfig browserConfig, KnowledgeConfig knowledgeConfig) {

        // Generated before execution — a preview of intent, not something
        // the live reasoning pipeline ever reads (see MissionPlan).
        MissionPlanner planner = missionPlanningEnabled()
                ? new LlmMissionPlanner(OpenAiCompatibleChatClient.fromEnvironment())
                : new RuleBasedMissionPlanner();

        MissionPlan plan = planner.plan(mission);

        EngineFactory.CreatedEngine created = EngineFactory.create(mission, browserConfig, knowledgeConfig.inspection());

        MissionResult result = created.engine().execute(mission);

        List<Experience> experiences = created.experienceRepository().findByMission(mission);
        List<ScreenshotSample> screenshots = created.screenshots().get();
        SignalLog signalLog = created.signals().get();

        BugExplainer bugExplainer = bugExplanationsEnabled()
                ? new LlmBugExplainer(OpenAiCompatibleChatClient.fromEnvironment())
                : new RuleBasedBugExplainer();

        RecommendationEngine recommender = recommendationsEnabled()
                ? new LlmRecommendationEngine(OpenAiCompatibleChatClient.fromEnvironment())
                : new RuleBasedRecommendationEngine();

        ReportSummarizer summarizer = plainLanguageSummaryEnabled()
                ? new LlmReportSummarizer(OpenAiCompatibleChatClient.fromEnvironment())
                : new RuleBasedReportSummarizer();

        // Built once and shared by the 3 built-in generators and every
        // discovered ReportRenderer plugin below, instead of each
        // independently rebuilding the same data from raw pieces.
        MissionReportData data = MissionReportData.from(
                result.context(), result.status(), bugExplainer, recommender, plan, experiences, screenshots,
                knowledgeConfig, signalLog, summarizer);

        String textReport = new ExplainabilityReportGenerator().generate(data);
        String htmlReport = new HtmlExplainabilityReportGenerator().generate(data);
        String jsonReport = new JsonReportGenerator().generate(data);

        Map<String, String> pluginReports = new LinkedHashMap<>();
        for (ReportRenderer renderer : ServiceLoader.load(ReportRenderer.class)) {
            pluginReports.put(renderer.name(), renderer.render(data));
        }

        return new AegisReport(result, plan, textReport, htmlReport, jsonReport, pluginReports);
    }

    /**
     * Opt-in via environment variable, not a mission parameter — mission
     * parameters get serialized into every report, and this decides
     * whether an LLM gets called at all, which shouldn't be something a
     * mission definition silently controls.
     */
    private static boolean bugExplanationsEnabled() {
        return "enabled".equalsIgnoreCase(System.getenv("AEGIS_LLM_BUG_EXPLANATIONS"));
    }

    /** Separate opt-in from bug explanations — a run can want one AI feature without the other. */
    private static boolean recommendationsEnabled() {
        return "enabled".equalsIgnoreCase(System.getenv("AEGIS_LLM_RECOMMENDATIONS"));
    }

    /** Separate opt-in again — mission planning is advisory-only and independent of the other two. */
    private static boolean missionPlanningEnabled() {
        return "enabled".equalsIgnoreCase(System.getenv("AEGIS_LLM_MISSION_PLANNING"));
    }

    /** Separate opt-in again — a whole-report plain-language rewrite is independent of the other three. */
    private static boolean plainLanguageSummaryEnabled() {
        return "enabled".equalsIgnoreCase(System.getenv("AEGIS_LLM_REPORT_SUMMARY"));
    }
}
