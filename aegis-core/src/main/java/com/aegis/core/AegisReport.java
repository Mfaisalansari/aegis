package com.aegis.core;

import com.aegis.core.mission.MissionPlan;
import com.aegis.core.report.MissionReportData;
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
 * {@code reportData} is the same {@link MissionReportData} the 3 built-in
 * generators and every {@code ReportRenderer} plugin above were built
 * from — kept here so an additional report format (e.g. a new generator
 * added after this mission already ran) can reuse the real screenshots,
 * experiences, and AI-generated plan/explanations/recommendations
 * exactly as they were, instead of an approximation rebuilt later from
 * just {@code missionResult}.
 *
 * {@code pluginReports} (Stage 2 "Report Plugin") holds output from every
 * discovered {@code ReportRenderer}, keyed by its {@code name()} — empty
 * when no plugin renderers are on the classpath, same shape either way.
 */
public record AegisReport(
        MissionResult missionResult,
        MissionPlan plan,
        MissionReportData reportData,
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
