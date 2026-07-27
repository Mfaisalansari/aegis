package com.aegis.api;

import com.aegis.core.AegisReport;
import com.aegis.core.mission.MissionPlan;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchResultTest {

    @Test
    void hasFailuresReflectsWhetherAnyMissionFailed() {

        assertFalse(new BatchResult(Map.of("a", report()), Map.of()).hasFailures());
        assertTrue(new BatchResult(Map.of(), Map.of("a", new RuntimeException("boom"))).hasFailures());
    }

    // Code-review follow-up: same defensive-copy pattern established
    // throughout Stage 5 (Mission, AegisReport, MissionPlan, EnterpriseConfig).
    @Test
    void reportsAndFailuresAreDefensivelyCopiedAndImmutable() {

        Map<String, AegisReport> callerReports = new HashMap<>(Map.of("a", report()));
        BatchResult result = new BatchResult(callerReports, Map.of());

        callerReports.remove("a");

        assertEquals(1, result.reports().size());
        assertThrows(UnsupportedOperationException.class, () -> result.reports().remove("a"));
        assertThrows(UnsupportedOperationException.class, () -> result.failures().put("x", new RuntimeException()));
    }

    @Test
    void nullMapsNormalizeToEmpty() {

        BatchResult result = new BatchResult(null, null);

        assertEquals(0, result.reports().size());
        assertEquals(0, result.failures().size());
        assertFalse(result.hasFailures());
    }

    private AegisReport report() {

        Mission mission = new Mission(UUID.randomUUID(), "Test", "Test", Map.of());
        MissionResult missionResult = new MissionResult(new MissionContext(mission), MissionStatus.SUCCESS);

        return new AegisReport(missionResult, new MissionPlan(List.of("step")), "text", "html", "json", Map.of());
    }
}
