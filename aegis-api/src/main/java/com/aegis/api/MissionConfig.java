package com.aegis.api;

/**
 * The "mission" section of an {@code application.yml} — how AEGIS should
 * explore, not what the app is. {@code strategy}/{@code inputStrategy}
 * are the same registry keys documented in USAGE.md §5/§4; {@code null}
 * means "use that registry's own default" rather than forcing a choice.
 */
public record MissionConfig(
        String name,
        String description,
        String strategy,
        Integer maxIterations,
        String inputStrategy,
        boolean interruptions,
        boolean doubleClicks,
        boolean raceConditions
) {

    public static MissionConfig defaults() {
        return new MissionConfig("AEGIS Mission", "Autonomous exploration", null, null, null, false, false, false);
    }
}
