package com.aegis.core;

import com.aegis.core.mission.MissionPlan;
import com.aegis.model.mission.MissionResult;
import com.aegis.model.mission.MissionStatus;

import java.util.Map;

/**
 * Everything a single {@link Aegis#run} call produces: the raw mission
 * outcome, the plan generated before execution, and all three report
 * formats as in-memory content. Deliberately doesn't write anything to
 * disk — where (or whether) to persist a report is the caller's decision,
 * not this library's.
 *
 * {@code pluginReports} (Stage 2 "Report Plugin") holds output from every
 * discovered {@code ReportRenderer}, keyed by its {@code name()} — empty
 * when no plugin renderers are on the classpath, same shape either way.
 */
public record AegisReport(
        MissionResult missionResult,
        MissionPlan plan,
        String textReport,
        String htmlReport,
        String jsonReport,
        Map<String, String> pluginReports
) {

    // Stage 5 hardening — same defensive-copy pattern as AuthenticatedSession (Stage 2).
    public AegisReport {
        pluginReports = pluginReports == null ? Map.of() : Map.copyOf(pluginReports);
    }

    public MissionStatus status() {
        return missionResult.status();
    }
}
