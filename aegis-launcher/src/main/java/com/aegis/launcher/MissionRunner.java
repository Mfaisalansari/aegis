package com.aegis.launcher;

import com.aegis.api.Launcher;
import com.aegis.model.mission.Mission;

/**
 * Thin delegate to {@code aegis-api}'s {@link Launcher} — kept as a
 * package-private wrapper (rather than switching every {@code *Main}
 * class to call {@code Launcher} directly) so this module's existing
 * entry points don't change shape.
 */
final class MissionRunner {

    private MissionRunner() {
    }

    static void run(Mission mission) {
        Launcher.run(mission);
    }
}
