package com.aegis.web.form;

import com.aegis.model.mission.Mission;

import java.util.Map;

/**
 * Every field the mission form can submit, exactly as raw strings/
 * booleans — numeric/enum parsing and validation is
 * {@link MissionRequestMapper}'s job, not this record's, so an invalid
 * value (e.g. {@code maxIterations=abc}) can still be echoed back to the
 * user in a sticky re-render instead of failing before validation runs.
 * A checkbox is present in the submitted form body (value {@code "on"})
 * only when checked — absent means false.
 */
public record MissionFormRequest(
        String name,
        String description,
        String baseUrl,
        String username,
        String password,
        String successUrlContains,
        String browserType,
        boolean headless,
        String strategy,
        String maxIterations,
        String inputStrategy,
        boolean interruptions,
        boolean doubleClicks,
        boolean raceConditions,
        String reportDirectory,
        boolean captureDom,
        boolean consoleWarnings,
        String contrastThreshold,
        boolean probeLinks,
        String noiseDenyPatterns,
        String knowledgeYaml,
        /** The raw natural-language text, if this form was pre-filled by {@code POST /run/parse} — kept only for sticky redisplay of the NL panel. */
        String instruction
) {

    public static MissionFormRequest defaults() {
        return new MissionFormRequest(
                "AEGIS Mission", "Autonomous exploration",
                "", "", "", "",
                "chromium", false,
                "greedy", "10", "realistic", false, false, false,
                "reports",
                false, false, "4.5", false, "",
                "", "");
    }

    public static MissionFormRequest fromSubmittedValues(Map<String, String> values) {
        return new MissionFormRequest(
                string(values, "name"),
                string(values, "description"),
                string(values, "baseUrl"),
                string(values, "username"),
                string(values, "password"),
                string(values, "successUrlContains"),
                string(values, "browserType"),
                checkbox(values, "headless"),
                string(values, "strategy"),
                string(values, "maxIterations"),
                string(values, "inputStrategy"),
                checkbox(values, "interruptions"),
                checkbox(values, "doubleClicks"),
                checkbox(values, "raceConditions"),
                string(values, "reportDirectory"),
                checkbox(values, "captureDom"),
                checkbox(values, "consoleWarnings"),
                string(values, "contrastThreshold"),
                checkbox(values, "probeLinks"),
                string(values, "noiseDenyPatterns"),
                string(values, "knowledgeYaml"),
                string(values, "instruction"));
    }

    /**
     * Maps a {@code MissionParser} result onto form fields for review before it runs. Only
     * {@code baseUrl}/{@code username}/{@code password}/{@code successUrlContains} ever come
     * from the parser (both {@code RuleBasedMissionParser} and {@code LlmMissionParser} only
     * ever populate those four parameter keys) — everything else (strategy, browser, iteration
     * limits, inspection settings) stays at the same defaults a blank form would have.
     */
    public static MissionFormRequest fromParsedMission(Mission mission, String instruction) {

        MissionFormRequest blank = defaults();

        return new MissionFormRequest(
                blankToDefault(mission.name(), blank.name()),
                blankToDefault(mission.description(), blank.description()),
                orEmpty(mission.parameter("baseUrl")),
                orEmpty(mission.parameter("username")),
                orEmpty(mission.parameter("password")),
                orEmpty(mission.parameter("successUrlContains")),
                blank.browserType(),
                blank.headless(),
                blank.strategy(),
                blank.maxIterations(),
                blank.inputStrategy(),
                blank.interruptions(),
                blank.doubleClicks(),
                blank.raceConditions(),
                blank.reportDirectory(),
                blank.captureDom(),
                blank.consoleWarnings(),
                blank.contrastThreshold(),
                blank.probeLinks(),
                blank.noiseDenyPatterns(),
                blank.knowledgeYaml(),
                instruction == null ? "" : instruction);
    }

    private static String string(Map<String, String> values, String key) {
        return values.getOrDefault(key, "");
    }

    private static boolean checkbox(Map<String, String> values, String key) {
        return values.containsKey(key);
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
