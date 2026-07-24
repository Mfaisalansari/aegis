package com.aegis.core.report;

import com.aegis.core.mission.MissionPlan;
import com.aegis.core.reasoning.learning.PatternStatistics;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.experience.Experience;
import com.aegis.model.experience.ExperienceOutcome;
import com.aegis.model.finding.Finding;
import com.aegis.model.finding.FindingSeverity;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionStatus;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reporting v2 Stage 2: Learning's Best/Worst Performing Actions and the Findings Dashboard's category grouping. */
class MissionReportDataStage2Test {

    @Test
    void actionPerformanceIsSortedBestSuccessRateFirst() {

        MissionContext context = new MissionContext(mission());
        Observation obs = observation("https://example.com", input("#a"));

        List<Experience> experiences = List.of(
                Experience.create(context, obs, new CandidateAction(type("#bad"), 0.9, "test"),
                        ExperienceOutcome.ERROR, Duration.ofMillis(10)),
                Experience.create(context, obs, new CandidateAction(type("#good"), 0.9, "test"),
                        ExperienceOutcome.SUCCESS, Duration.ofMillis(10))
        );

        MissionReportData data = withExperiences(context, experiences);

        List<PatternStatistics> performance = data.learningSummary().actionPerformance();

        assertEquals(2, performance.size());
        assertEquals("#good", performance.get(0).action().target());
        assertEquals("#bad", performance.get(1).action().target());
    }

    @Test
    void actionPerformanceIsEmptyWhenNoExperiences() {

        MissionContext context = new MissionContext(mission());

        MissionReportData data = MissionReportData.from(context, MissionStatus.SUCCESS);

        assertTrue(data.learningSummary().actionPerformance().isEmpty());
    }

    @Test
    void findingsAreGroupedIntoTheCorrectCategories() {

        MissionContext context = new MissionContext(mission());

        context.getExecutionState().addFinding(
                new Finding(FindingSeverity.CRITICAL, "CRASH: tab crashed", "https://example.com", Instant.now()));
        context.getExecutionState().addFinding(
                new Finding(FindingSeverity.HIGH, "PAGE_ERROR: uncaught exception", "https://example.com", Instant.now()));
        context.getExecutionState().addFinding(
                new Finding(FindingSeverity.MEDIUM, "REQUEST_FAILED: /api/x failed", "https://example.com", Instant.now()));
        context.getExecutionState().addFinding(
                new Finding(FindingSeverity.LOW, "DIALOG: alert shown", "https://example.com", Instant.now()));
        context.getExecutionState().addFinding(
                new Finding(FindingSeverity.HIGH, "Engine error: TimeoutError waiting for element",
                        "https://example.com", Instant.now()));

        MissionReportData data = MissionReportData.from(context, MissionStatus.FAILED);

        assertEquals(1, data.findingsByCategory().get(FindingCategory.STABILITY).size());
        assertEquals(1, data.findingsByCategory().get(FindingCategory.JAVASCRIPT).size());
        assertEquals(1, data.findingsByCategory().get(FindingCategory.NETWORK).size());
        assertEquals(1, data.findingsByCategory().get(FindingCategory.NAVIGATION).size());
        assertEquals(1, data.findingsByCategory().get(FindingCategory.TIMEOUT).size());
    }

    @Test
    void findingsByCategoryIsEmptyWhenThereAreNoFindings() {

        MissionContext context = new MissionContext(mission());

        MissionReportData data = MissionReportData.from(context, MissionStatus.SUCCESS);

        assertTrue(data.findingsByCategory().isEmpty());
    }

    private MissionReportData withExperiences(MissionContext context, List<Experience> experiences) {
        return MissionReportData.from(
                context, MissionStatus.SUCCESS,
                (cluster, fallback) -> fallback,
                (clusters, fallback) -> fallback,
                new MissionPlan(List.of()),
                experiences);
    }

    private Mission mission() {
        return new Mission(UUID.randomUUID(), "Test", "Test", Map.of());
    }

    private Observation observation(String url, ElementInfo... elements) {
        return new Observation(url, "Title", List.of(elements), List.of(), List.of(elements), List.of(), List.of(), Instant.now());
    }

    private ElementInfo input(String locator) {
        return new ElementInfo("input", "", "", "", "text", "", true, true, locator);
    }

    private Action type(String target) {
        return new Action(
                UUID.randomUUID(), ActionType.TYPE, target, "value", "test",
                1.0, "test", Duration.ofSeconds(5), Instant.now(), "input"
        );
    }
}
