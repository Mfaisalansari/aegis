package com.aegis.web.mission;

import com.aegis.core.AegisReport;
import com.aegis.core.mission.MissionPlan;
import com.aegis.core.report.MissionReportData;
import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionResult;
import com.aegis.model.mission.MissionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DashboardStatsTest {

    @Test
    void emptyStoreProducesZeroedStatsAndADashForPassRate() {

        DashboardStats stats = DashboardStats.compute(List.of());

        assertEquals(0, stats.total());
        assertEquals("—", stats.passRateLabel());
        assertEquals(0, stats.avgDurationSeconds());
        assertEquals(0, stats.active());
    }

    @Test
    void runningJobsCountAsActiveAndDoNotAffectPassRate() {

        DashboardStats stats = DashboardStats.compute(List.of(newRunningJob()));

        assertEquals(1, stats.total());
        assertEquals(1, stats.active());
        assertEquals("—", stats.passRateLabel());
    }

    @Test
    void mixedOutcomesComputeAPassRateOverFinishedJobsOnly() {

        MissionJob success1 = newDoneJob(MissionStatus.SUCCESS);
        MissionJob success2 = newDoneJob(MissionStatus.SUCCESS);
        MissionJob failed = newDoneJob(MissionStatus.FAILED);
        MissionJob running = newRunningJob();

        DashboardStats stats = DashboardStats.compute(List.of(success1, success2, failed, running));

        assertEquals(4, stats.total());
        assertEquals(1, stats.active());
        assertEquals("67%", stats.passRateLabel());
    }

    @Test
    void errorJobsCountAsFinishedButNeverAsPasses() {

        MissionJob success = newDoneJob(MissionStatus.SUCCESS);
        MissionJob error = newErrorJob();

        DashboardStats stats = DashboardStats.compute(List.of(success, error));

        assertEquals("50%", stats.passRateLabel());
    }

    private static MissionJob newRunningJob() {
        return new MissionJob(UUID.randomUUID().toString(), testMission(), "chromium");
    }

    private static MissionJob newDoneJob(MissionStatus status) {

        MissionJob job = newRunningJob();

        MissionResult result = new MissionResult(new MissionContext(testMission()), status);
        AegisReport report = new AegisReport(result, new MissionPlan(List.of()),
                MissionReportData.from(result.context(), result.status()), "text", "html", "json", Map.of());
        job.complete(report);

        return job;
    }

    private static MissionJob newErrorJob() {
        MissionJob job = newRunningJob();
        job.fail("boom");
        return job;
    }

    private static Mission testMission() {
        return new Mission(UUID.randomUUID(), "Test", "Test", Map.of("baseUrl", "https://example.com"));
    }
}
