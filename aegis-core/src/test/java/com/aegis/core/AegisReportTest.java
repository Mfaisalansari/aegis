package com.aegis.core;

import com.aegis.core.mission.MissionPlan;
import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionResult;
import com.aegis.model.mission.MissionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AegisReportTest {

    @Test
    void statusDelegatesToTheWrappedMissionResult() {

        Mission mission = new Mission(UUID.randomUUID(), "Test", "Test", Map.of());
        MissionResult missionResult = new MissionResult(new MissionContext(mission), MissionStatus.SUCCESS);

        AegisReport report = new AegisReport(
                missionResult, new MissionPlan(List.of("step")), "text", "html", "json", Map.of());

        assertEquals(MissionStatus.SUCCESS, report.status());
    }
}
