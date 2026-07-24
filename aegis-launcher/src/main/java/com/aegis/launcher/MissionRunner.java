package com.aegis.launcher;

import com.aegis.core.bug.BugExplainer;
import com.aegis.core.bug.LlmBugExplainer;
import com.aegis.core.bug.LlmRecommendationEngine;
import com.aegis.core.bug.RecommendationEngine;
import com.aegis.core.bug.RuleBasedBugExplainer;
import com.aegis.core.bug.RuleBasedRecommendationEngine;
import com.aegis.core.engine.EngineFactory;
import com.aegis.core.engine.MissionEngine;
import com.aegis.core.llm.OpenAiCompatibleChatClient;
import com.aegis.core.mission.LlmMissionPlanner;
import com.aegis.core.mission.MissionPlan;
import com.aegis.core.mission.MissionPlanner;
import com.aegis.core.mission.RuleBasedMissionPlanner;
import com.aegis.core.report.ExplainabilityReportGenerator;
import com.aegis.core.report.HtmlExplainabilityReportGenerator;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Shared entry-point logic: run a mission, write both report formats,
 * print a summary. Each Main-style class just builds a Mission and hands
 * it here, instead of duplicating this wiring per target site.
 */
final class MissionRunner {

    private MissionRunner() {
    }

    static void run(Mission mission) {

        System.out.println("=================================");
        System.out.println("         AEGIS v0.1");
        System.out.println("=================================");
        System.out.println();
        System.out.println("Mission: " + mission.name());
        System.out.println("Target : " + mission.parameter("baseUrl"));
        System.out.println();

        // Generated before execution — this is a preview of intent, not
        // something the live reasoning pipeline ever reads.
        MissionPlanner planner = missionPlanningEnabled()
                ? new LlmMissionPlanner(OpenAiCompatibleChatClient.fromEnvironment())
                : new RuleBasedMissionPlanner();

        MissionPlan plan = planner.plan(mission);

        System.out.println("Mission Plan:");
        plan.steps().forEach(step -> System.out.println("  - " + step));
        System.out.println();

        MissionEngine engine = EngineFactory.create();

        MissionResult result = engine.execute(mission);

        long timestamp = Instant.now().toEpochMilli();

        BugExplainer bugExplainer = bugExplanationsEnabled()
                ? new LlmBugExplainer(OpenAiCompatibleChatClient.fromEnvironment())
                : new RuleBasedBugExplainer();

        RecommendationEngine recommender = recommendationsEnabled()
                ? new LlmRecommendationEngine(OpenAiCompatibleChatClient.fromEnvironment())
                : new RuleBasedRecommendationEngine();

        Path textReportPath = writeReport(
                new ExplainabilityReportGenerator()
                        .generate(result.context(), result.status(), bugExplainer, recommender, plan),
                timestamp,
                "txt"
        );

        Path htmlReportPath = writeReport(
                new HtmlExplainabilityReportGenerator()
                        .generate(result.context(), result.status(), bugExplainer, recommender, plan),
                timestamp,
                "html"
        );

        System.out.println();
        System.out.println("=================================");
        System.out.println("Mission Finished");
        System.out.println("Status     : " + result.status());
        System.out.println("Report     : " + textReportPath);
        System.out.println("HTML Report: " + htmlReportPath);
        System.out.println("=================================");
    }

    /**
     * Opt-in via environment variable, not a mission parameter — same
     * reasoning as the rest of the AEGIS_LLM_* config (OpenAiCompatibleChatClient):
     * mission parameters get serialized into every report, and this
     * decides whether an LLM gets called at all, which shouldn't be
     * something a mission definition silently controls.
     */
    private static boolean bugExplanationsEnabled() {
        return "enabled".equalsIgnoreCase(System.getenv("AEGIS_LLM_BUG_EXPLANATIONS"));
    }

    /** Separate opt-in from bug explanations — a mission run can want one AI feature without the other. */
    private static boolean recommendationsEnabled() {
        return "enabled".equalsIgnoreCase(System.getenv("AEGIS_LLM_RECOMMENDATIONS"));
    }

    /** Separate opt-in again — mission planning is advisory-only and independent of the other two. */
    private static boolean missionPlanningEnabled() {
        return "enabled".equalsIgnoreCase(System.getenv("AEGIS_LLM_MISSION_PLANNING"));
    }

    private static Path writeReport(String content, long timestamp, String extension) {

        Path directory = Path.of("reports");
        Path path = directory.resolve("aegis-report-" + timestamp + "." + extension);

        try {
            Files.createDirectories(directory);
            Files.writeString(path, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + extension + " report", e);
        }

        return path;
    }
}
