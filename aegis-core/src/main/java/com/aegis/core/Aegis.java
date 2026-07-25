package com.aegis.core;

import com.aegis.core.browser.BrowserConfig;
import com.aegis.core.bug.BugExplainer;
import com.aegis.core.bug.LlmBugExplainer;
import com.aegis.core.bug.LlmRecommendationEngine;
import com.aegis.core.bug.RecommendationEngine;
import com.aegis.core.bug.RuleBasedBugExplainer;
import com.aegis.core.bug.RuleBasedRecommendationEngine;
import com.aegis.core.engine.EngineFactory;
import com.aegis.core.llm.OpenAiCompatibleChatClient;
import com.aegis.core.mission.LlmMissionPlanner;
import com.aegis.core.mission.MissionPlan;
import com.aegis.core.mission.MissionPlanner;
import com.aegis.core.mission.RuleBasedMissionPlanner;
import com.aegis.core.report.ExplainabilityReportGenerator;
import com.aegis.core.report.HtmlExplainabilityReportGenerator;
import com.aegis.core.report.JsonReportGenerator;
import com.aegis.core.resilience.ScreenshotSample;
import com.aegis.model.experience.Experience;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionResult;

import java.util.List;

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

        // Generated before execution — a preview of intent, not something
        // the live reasoning pipeline ever reads (see MissionPlan).
        MissionPlanner planner = missionPlanningEnabled()
                ? new LlmMissionPlanner(OpenAiCompatibleChatClient.fromEnvironment())
                : new RuleBasedMissionPlanner();

        MissionPlan plan = planner.plan(mission);

        EngineFactory.CreatedEngine created = EngineFactory.create(browserConfig);

        MissionResult result = created.engine().execute(mission);

        List<Experience> experiences = created.experienceRepository().findByMission(mission);
        List<ScreenshotSample> screenshots = created.screenshots().get();

        BugExplainer bugExplainer = bugExplanationsEnabled()
                ? new LlmBugExplainer(OpenAiCompatibleChatClient.fromEnvironment())
                : new RuleBasedBugExplainer();

        RecommendationEngine recommender = recommendationsEnabled()
                ? new LlmRecommendationEngine(OpenAiCompatibleChatClient.fromEnvironment())
                : new RuleBasedRecommendationEngine();

        String textReport = new ExplainabilityReportGenerator()
                .generate(result.context(), result.status(), bugExplainer, recommender, plan, experiences, screenshots);

        String htmlReport = new HtmlExplainabilityReportGenerator()
                .generate(result.context(), result.status(), bugExplainer, recommender, plan, experiences, screenshots);

        String jsonReport = new JsonReportGenerator()
                .generate(result.context(), result.status(), bugExplainer, recommender, plan, experiences, screenshots);

        return new AegisReport(result, plan, textReport, htmlReport, jsonReport);
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
}
