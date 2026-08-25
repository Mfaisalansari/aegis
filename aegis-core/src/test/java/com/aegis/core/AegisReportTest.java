package com.aegis.core;

import com.aegis.core.mission.MissionPlan;
import com.aegis.core.report.MissionReportData;
import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionResult;
import com.aegis.model.mission.MissionStatus;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AegisReportTest {

    @Test
    void statusDelegatesToTheWrappedMissionResult() {

        Mission mission = new Mission(UUID.randomUUID(), "Test", "Test", Map.of());
        MissionResult missionResult = new MissionResult(new MissionContext(mission), MissionStatus.SUCCESS);

        AegisReport report = new AegisReport(
                missionResult, new MissionPlan(List.of("step")), MissionReportData.from(missionResult.context(), missionResult.status()),
                "text", "html", "json", Map.of());

        assertEquals(MissionStatus.SUCCESS, report.status());
    }

    // Stage 5 hardening — same defensive-copy pattern as AuthenticatedSession (Stage 2).
    @Test
    void pluginReportsIsDefensivelyCopiedAndImmutable() {

        Mission mission = new Mission(UUID.randomUUID(), "Test", "Test", Map.of());
        MissionResult missionResult = new MissionResult(new MissionContext(mission), MissionStatus.SUCCESS);
        Map<String, String> callerMap = new HashMap<>(Map.of("markdown", "# Report"));

        AegisReport report = new AegisReport(
                missionResult, new MissionPlan(List.of("step")), MissionReportData.from(missionResult.context(), missionResult.status()),
                "text", "html", "json", callerMap);

        callerMap.put("markdown", "mutated");

        assertEquals("# Report", report.pluginReports().get("markdown"));
        assertThrows(UnsupportedOperationException.class, () -> report.pluginReports().put("x", "y"));
    }
}
