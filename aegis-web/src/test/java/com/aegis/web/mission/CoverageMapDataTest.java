package com.aegis.web.mission;

import com.aegis.core.AegisReport;
import com.aegis.core.mission.MissionPlan;
import com.aegis.core.report.MissionReportData;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionResult;
import com.aegis.model.mission.MissionStatus;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverageMapDataTest {

    @Test
    void emptyJobListProducesNoSites() {

        CoverageMapData data = CoverageMapData.compute(List.of());

        assertTrue(data.sites().isEmpty());
    }

    @Test
    void runningAndErrorJobsAreExcludedFromAggregation() {

        MissionJob running = new MissionJob(UUID.randomUUID().toString(), mission("https://example.com"), "chromium");
        MissionJob error = new MissionJob(UUID.randomUUID().toString(), mission("https://example.com"), "chromium");
        error.fail("boom");

        CoverageMapData data = CoverageMapData.compute(List.of(running, error));

        assertTrue(data.sites().isEmpty());
    }

    @Test
    void pagesWithinASiteAreSortedByCoverageAscending() {

        MissionJob job = doneJob("https://example.com", context -> {
            context.getExecutionState().setCurrentObservation(observation("https://example.com/covered", "el-1"));
            context.getExecutionState().addAction(click("el-1"));
            context.getExecutionState().setCurrentObservation(observation("https://example.com/uncovered", "el-2"));
        });

        CoverageMapData data = CoverageMapData.compute(List.of(job));

        assertEquals(1, data.sites().size());
        List<CoverageMapData.PageSummary> pages = data.sites().get(0).pages();
        assertEquals(2, pages.size());
        assertEquals("https://example.com/uncovered", pages.get(0).url());
        assertEquals(0.0, pages.get(0).coveragePercent());
        assertEquals("https://example.com/covered", pages.get(1).url());
        assertEquals(100.0, pages.get(1).coveragePercent());
    }

    @Test
    void theSamePageAcrossMultipleMissionsCountsAllVisitsButShowsTheMostRecentSnapshot() {

        MissionJob newer = doneJob("https://example.com", context -> {
            context.getExecutionState().setCurrentObservation(observation("https://example.com/", "el-1"));
            context.getExecutionState().addAction(click("el-1"));
        });
        MissionJob older = doneJob("https://example.com", context ->
                context.getExecutionState().setCurrentObservation(observation("https://example.com/", "el-1")));

        // Input order matters: CoverageMapData trusts most-recent-first, same contract as MissionJobStore.list().
        CoverageMapData data = CoverageMapData.compute(List.of(newer, older));

        assertEquals(1, data.sites().size());
        CoverageMapData.PageSummary page = data.sites().get(0).pages().get(0);
        assertEquals(100.0, page.coveragePercent(), "should reflect the newer (first) job's snapshot, not the older one's");
        assertEquals(2, page.missionCount(), "both visits should still count toward missionCount");
    }

    @Test
    void differentBaseUrlsProduceSeparateSiteGroupsWithTheirOwnAverage() {

        MissionJob siteA = doneJob("https://a.example.com", context -> {
            context.getExecutionState().setCurrentObservation(observation("https://a.example.com/", "el-1"));
            context.getExecutionState().addAction(click("el-1"));
        });
        MissionJob siteB = doneJob("https://b.example.com", context ->
                context.getExecutionState().setCurrentObservation(observation("https://b.example.com/", "el-1")));

        CoverageMapData data = CoverageMapData.compute(List.of(siteA, siteB));

        assertEquals(2, data.sites().size());
        assertEquals("https://a.example.com", data.sites().get(0).baseUrl());
        assertEquals(100.0, data.sites().get(0).averageCoveragePercent());
        assertEquals("https://b.example.com", data.sites().get(1).baseUrl());
        assertEquals(0.0, data.sites().get(1).averageCoveragePercent());
    }

    private MissionJob doneJob(String baseUrl, java.util.function.Consumer<MissionContext> setup) {

        Mission mission = mission(baseUrl);
        MissionJob job = new MissionJob(UUID.randomUUID().toString(), mission, "chromium");

        MissionContext context = new MissionContext(mission);
        setup.accept(context);

        MissionResult result = new MissionResult(context, MissionStatus.SUCCESS);
        AegisReport report = new AegisReport(result, new MissionPlan(List.of()),
                MissionReportData.from(result.context(), result.status()), "text", "html", "json", Map.of());
        job.complete(report);

        return job;
    }

    private Mission mission(String baseUrl) {
        return new Mission(UUID.randomUUID(), "Test", "Test", Map.of("baseUrl", baseUrl));
    }

    private Observation observation(String url, String elementLocator) {
        List<ElementInfo> elements = List.of(new ElementInfo("button", null, null, null, "button", null, true, true, elementLocator));
        return new Observation(url, "Test Page", elements, elements, List.of(), List.of(), List.of(), Instant.now());
    }

    private Action click(String target) {
        return new Action(UUID.randomUUID(), ActionType.CLICK, target, null, "test", 0.8, "test", Duration.ofSeconds(5), Instant.now(), "button");
    }
}
