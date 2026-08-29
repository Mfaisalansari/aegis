package com.aegis.web.mission;

import com.aegis.core.AegisReport;
import com.aegis.core.mission.MissionPlan;
import com.aegis.core.report.MissionReportData;
import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionResult;
import com.aegis.model.mission.MissionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionHistoryStoreTest {

    @Test
    void aDoneJobRoundTripsEveryField(@TempDir Path dir) {

        MissionHistoryStore store = new MissionHistoryStore(dir);
        MissionJob job = doneJob(MissionStatus.SUCCESS);

        store.save(job);
        MissionJob reloaded = onlyJob(store);

        assertEquals(job.id(), reloaded.id());
        assertEquals(job.mission().id(), reloaded.mission().id());
        assertEquals(job.mission().name(), reloaded.mission().name());
        assertEquals(job.mission().parameters(), reloaded.mission().parameters());
        assertEquals(job.browserType(), reloaded.browserType());
        // Persisted as epoch millis, so sub-millisecond precision from Instant.now() is expected to be lost.
        assertEquals(job.submittedAt().toEpochMilli(), reloaded.submittedAt().toEpochMilli());
        assertEquals(job.finishedAt().toEpochMilli(), reloaded.finishedAt().toEpochMilli());
        assertEquals(MissionJob.State.DONE, reloaded.state());
        assertEquals(job.summary().status(), reloaded.summary().status());
        assertEquals(job.htmlReport(), reloaded.htmlReport());
        assertEquals(job.jsonReport(), reloaded.jsonReport());
        assertEquals(job.textReport(), reloaded.textReport());
        assertEquals(job.redesignedHtmlReport(), reloaded.redesignedHtmlReport());
        assertTrue(job.hasLiveReport());
        assertEquals(false, reloaded.hasLiveReport());
    }

    @Test
    void anErrorJobRoundTripsWithNoSummaryOrReportStrings(@TempDir Path dir) {

        MissionHistoryStore store = new MissionHistoryStore(dir);
        MissionJob job = new MissionJob(UUID.randomUUID().toString(), testMission(), "chromium");
        job.fail("boom");

        store.save(job);
        MissionJob reloaded = onlyJob(store);

        assertEquals(MissionJob.State.ERROR, reloaded.state());
        assertEquals("boom", reloaded.errorMessage());
        assertNull(reloaded.summary());
        assertNull(reloaded.htmlReport());
        assertEquals(false, reloaded.hasLiveReport());
    }

    @Test
    void savingAStillRunningJobIsRejected(@TempDir Path dir) {

        MissionHistoryStore store = new MissionHistoryStore(dir);
        MissionJob running = new MissionJob(UUID.randomUUID().toString(), testMission(), "chromium");

        assertThrows(IllegalArgumentException.class, () -> store.save(running));
    }

    @Test
    void aCorruptFileIsSkippedWithoutBreakingTheOthers(@TempDir Path dir) throws Exception {

        MissionHistoryStore store = new MissionHistoryStore(dir);
        store.save(doneJob(MissionStatus.SUCCESS));

        Files.writeString(dir.resolve("garbage.json"), "{ not valid json ");

        List<MissionJob> loaded = store.loadAll();

        assertEquals(1, loaded.size());
    }

    private MissionJob onlyJob(MissionHistoryStore store) {
        List<MissionJob> loaded = store.loadAll();
        assertEquals(1, loaded.size());
        return loaded.get(0);
    }

    private MissionJob doneJob(MissionStatus status) {

        Mission mission = testMission();
        MissionJob job = new MissionJob(UUID.randomUUID().toString(), mission, "chromium");

        MissionResult result = new MissionResult(new MissionContext(mission), status);
        AegisReport report = new AegisReport(result, new MissionPlan(List.of("step one", "step two")),
                MissionReportData.from(result.context(), result.status()), "text", "html", "json", Map.of());
        job.complete(report);

        return job;
    }

    private Mission testMission() {
        return new Mission(UUID.randomUUID(), "Test", "Test", Map.of("baseUrl", "https://example.com"));
    }
}
