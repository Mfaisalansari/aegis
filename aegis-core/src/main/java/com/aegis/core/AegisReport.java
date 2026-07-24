package com.aegis.core;

import com.aegis.core.mission.MissionPlan;
import com.aegis.model.mission.MissionResult;
import com.aegis.model.mission.MissionStatus;

/**
 * Everything a single {@link Aegis#run} call produces: the raw mission
 * outcome, the plan generated before execution, and all three report
 * formats as in-memory content. Deliberately doesn't write anything to
 * disk — where (or whether) to persist a report is the caller's decision,
 * not this library's.
 */
public record AegisReport(
        MissionResult missionResult,
        MissionPlan plan,
        String textReport,
        String htmlReport,
        String jsonReport
) {

    public MissionStatus status() {
        return missionResult.status();
    }
}
