package com.aegis.core.report;

import com.aegis.core.mission.MissionPlan;
import com.aegis.core.resilience.ScreenshotSample;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.experience.Experience;
import com.aegis.model.experience.ExperienceOutcome;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionStatus;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MissionReportDataScreenshotTest {

    @Test
    void executionEventGetsTheNearestScreenshotWithinTolerance() {

        Instant executedAt = Instant.now();
        byte[] png = {1, 2, 3};

        MissionReportData data = withExecutionAndScreenshots(
                executedAt, List.of(new ScreenshotSample(executedAt.plusMillis(100), png)));

        TimelineEvent execution = executionEvent(data);

        assertEquals("data:image/png;base64," + Base64.getEncoder().encodeToString(png), execution.screenshotDataUri());
    }

    @Test
    void executionEventGetsNoScreenshotWhenNoneAreWithinTolerance() {

        Instant executedAt = Instant.now();

        MissionReportData data = withExecutionAndScreenshots(
                executedAt, List.of(new ScreenshotSample(executedAt.plusSeconds(5), new byte[]{1})));

        assertNull(executionEvent(data).screenshotDataUri());
    }

    @Test
    void executionEventPicksTheClosestScreenshotWhenMultipleExist() {

        Instant executedAt = Instant.now();
        byte[] closer = {9, 9};
        byte[] farther = {1, 1};

        MissionReportData data = withExecutionAndScreenshots(
                executedAt,
                List.of(
                        new ScreenshotSample(executedAt.plusMillis(800), farther),
                        new ScreenshotSample(executedAt.plusMillis(50), closer)));

        assertEquals(
                "data:image/png;base64," + Base64.getEncoder().encodeToString(closer),
                executionEvent(data).screenshotDataUri());
    }

    @Test
    void observationEventsNeverGetAScreenshotEvenWhenOneIsVeryClose() {

        Mission mission = mission();
        MissionContext context = new MissionContext(mission);
        Instant t = Instant.now();

        context.getExecutionState().setCurrentObservation(
                new Observation("https://example.com", "Title", List.of(input("#a")), List.of(),
                        List.of(input("#a")), List.of(), List.of(), t));

        MissionReportData data = MissionReportData.from(
                context, MissionStatus.SUCCESS,
                (cluster, fallback) -> fallback,
                (clusters, fallback) -> fallback,
                new MissionPlan(List.of()),
                List.of(),
                List.of(new ScreenshotSample(t, new byte[]{1, 2, 3})));

        TimelineEvent observation = data.timeline().stream()
                .filter(e -> e.kind() == TimelineEventKind.OBSERVATION)
                .findFirst().orElseThrow();

        assertNull(observation.screenshotDataUri());
    }

    private MissionReportData withExecutionAndScreenshots(Instant executedAt, List<ScreenshotSample> screenshots) {

        Mission mission = mission();
        MissionContext context = new MissionContext(mission);
        Observation obs = new Observation("https://example.com", "Title", List.of(input("#a")), List.of(),
                List.of(input("#a")), List.of(), List.of(), executedAt);

        Experience experience = new Experience(
                UUID.randomUUID(), context, obs, new CandidateAction(type("#a"), 0.9, "test"),
                ExperienceOutcome.SUCCESS, Duration.ofMillis(10), executedAt);

        return MissionReportData.from(
                context, MissionStatus.SUCCESS,
                (cluster, fallback) -> fallback,
                (clusters, fallback) -> fallback,
                new MissionPlan(List.of()),
                List.of(experience),
                screenshots);
    }

    private TimelineEvent executionEvent(MissionReportData data) {
        return data.timeline().stream()
                .filter(e -> e.kind() == TimelineEventKind.EXECUTION)
                .findFirst().orElseThrow();
    }

    private Mission mission() {
        return new Mission(UUID.randomUUID(), "Test", "Test", Map.of());
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
