package com.aegis.core.report;

import com.aegis.core.mission.MissionPlan;
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
import com.aegis.model.reasoning.ReasoningStep;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionReportDataTimelineTest {

    @Test
    void timelineAlwaysHasMissionStartedFirstAndMissionFinishedLast() {

        MissionContext context = new MissionContext(mission());

        MissionReportData data = MissionReportData.from(context, MissionStatus.SUCCESS);

        List<TimelineEvent> timeline = data.timeline();

        assertEquals(TimelineEventKind.MISSION_STARTED, timeline.get(0).kind());
        assertEquals(TimelineEventKind.MISSION_FINISHED, timeline.get(timeline.size() - 1).kind());
    }

    @Test
    void timelineIncludesObservationEventsWithNewStateDetection() {

        MissionContext context = new MissionContext(mission());

        // Explicit, strictly increasing timestamps — a real revisit is a
        // fresh Observation capture with a later capturedAt, same
        // StateSignature. Reusing one frozen Instant.now() object for
        // both the first visit and the revisit would tie its timestamp
        // to the earlier value, letting a stable sort reorder it ahead
        // of the intervening event depending on clock resolution.
        Instant t0 = Instant.now();

        Observation stateA = observation("https://example.com/a", t0, input("#a"));
        Observation stateB = observation("https://example.com/b", t0.plusMillis(10), input("#b"));
        Observation stateARevisit = observation("https://example.com/a", t0.plusMillis(20), input("#a"));

        context.getExecutionState().setCurrentObservation(stateA);
        context.getExecutionState().setCurrentObservation(stateB);
        context.getExecutionState().setCurrentObservation(stateARevisit);

        MissionReportData data = MissionReportData.from(context, MissionStatus.SUCCESS);

        List<TimelineEvent> observations = data.timeline().stream()
                .filter(e -> e.kind() == TimelineEventKind.OBSERVATION)
                .toList();

        assertEquals(3, observations.size());
        assertTrue(observations.get(0).detail().contains("new state"));
        assertTrue(observations.get(1).detail().contains("new state"));
        assertTrue(observations.get(2).detail().contains("revisiting"));
    }

    @Test
    void timelineIncludesReasoningEventsPerStep() {

        MissionContext context = new MissionContext(mission());

        CandidateAction candidate = new CandidateAction(type("#a"), 0.9, "test reasoning");

        context.getExecutionState().addReasoningStep(new ReasoningStep(1, List.of(candidate), candidate, Instant.now(), "greedy"));

        MissionReportData data = MissionReportData.from(context, MissionStatus.SUCCESS);

        List<TimelineEvent> reasoningEvents = data.timeline().stream()
                .filter(e -> e.kind() == TimelineEventKind.REASONING)
                .toList();

        assertEquals(2, reasoningEvents.size());
        assertTrue(reasoningEvents.get(0).headline().contains("Generated"));
        assertTrue(reasoningEvents.get(1).headline().contains("Selected"));
        assertTrue(reasoningEvents.get(1).detail().contains("test reasoning"));
    }

    @Test
    void timelineIncludesExecutionEventsFromExperiencesWithCorrectOutcome() {

        MissionContext context = new MissionContext(mission());
        Observation obs = observation("https://example.com", input("#a"));

        Experience success = Experience.create(
                context, obs, new CandidateAction(type("#a"), 0.9, "test"),
                ExperienceOutcome.SUCCESS, Duration.ofMillis(50));

        MissionReportData data = withExperiences(context, List.of(success));

        List<TimelineEvent> executionEvents = data.timeline().stream()
                .filter(e -> e.kind() == TimelineEventKind.EXECUTION)
                .toList();

        assertEquals(1, executionEvents.size());
        assertEquals("Execution Successful", executionEvents.get(0).headline());
    }

    @Test
    void timelineMarksFailedExecutionsCorrectly() {

        MissionContext context = new MissionContext(mission());
        Observation obs = observation("https://example.com", input("#a"));

        Experience failure = Experience.create(
                context, obs, new CandidateAction(type("#a"), 0.9, "test"),
                ExperienceOutcome.ERROR, Duration.ofMillis(50));

        MissionReportData data = withExperiences(context, List.of(failure));

        TimelineEvent event = data.timeline().stream()
                .filter(e -> e.kind() == TimelineEventKind.EXECUTION)
                .findFirst().orElseThrow();

        assertEquals("Execution Failed", event.headline());
    }

    @Test
    void timelineIncludesFindingEvents() {

        MissionContext context = new MissionContext(mission());

        context.getExecutionState().addFinding(
                new Finding(FindingSeverity.HIGH, "CRASH: tab crashed", "https://example.com", Instant.now()));

        MissionReportData data = MissionReportData.from(context, MissionStatus.FAILED);

        List<TimelineEvent> findingEvents = data.timeline().stream()
                .filter(e -> e.kind() == TimelineEventKind.FINDING)
                .toList();

        assertEquals(1, findingEvents.size());
        assertTrue(findingEvents.get(0).headline().contains("CRASH"));
    }

    @Test
    void timelineEventsAreSortedChronologically() {

        MissionContext context = new MissionContext(mission());

        context.getExecutionState().setCurrentObservation(observation("https://example.com/a", input("#a")));
        context.getExecutionState().setCurrentObservation(observation("https://example.com/b", input("#b")));

        MissionReportData data = MissionReportData.from(context, MissionStatus.SUCCESS);

        List<Instant> timestamps = data.timeline().stream().map(TimelineEvent::timestamp).toList();

        for (int i = 1; i < timestamps.size(); i++) {
            assertFalse(timestamps.get(i).isBefore(timestamps.get(i - 1)));
        }
    }

    @Test
    void durationIsNeverNegative() {

        MissionContext context = new MissionContext(mission());

        context.getExecutionState().setCurrentObservation(observation("https://example.com", input("#a")));

        MissionReportData data = MissionReportData.from(context, MissionStatus.SUCCESS);

        assertFalse(data.duration().isNegative());
    }

    @Test
    void actionsExecutedMatchesRecordedActionCount() {

        MissionContext context = new MissionContext(mission());

        context.getExecutionState().addAction(type("#a"));
        context.getExecutionState().addAction(type("#b"));

        MissionReportData data = MissionReportData.from(context, MissionStatus.SUCCESS);

        assertEquals(2, data.actionsExecuted());
    }

    @Test
    void averageConfidenceIsComputedAcrossReasoningSteps() {

        MissionContext context = new MissionContext(mission());

        CandidateAction c1 = new CandidateAction(type("#a"), 0.8, "test");
        CandidateAction c2 = new CandidateAction(type("#b"), 0.6, "test");

        context.getExecutionState().addReasoningStep(new ReasoningStep(1, List.of(c1), c1, Instant.now(), "greedy"));
        context.getExecutionState().addReasoningStep(new ReasoningStep(2, List.of(c2), c2, Instant.now(), "greedy"));

        MissionReportData data = MissionReportData.from(context, MissionStatus.SUCCESS);

        assertEquals(0.7, data.averageConfidence(), 0.0001);
    }

    @Test
    void learningSummaryIsEmptyWhenNoExperiencesGiven() {

        MissionContext context = new MissionContext(mission());

        MissionReportData data = MissionReportData.from(context, MissionStatus.SUCCESS);

        assertEquals(LearningSummary.empty(), data.learningSummary());
    }

    @Test
    void learningSummaryCountsNewVsUpdatedActions() {

        MissionContext context = new MissionContext(mission());
        Observation obs = observation("https://example.com", input("#a"));

        // "#a" executed once -> new; "#b" executed twice -> updated.
        List<Experience> experiences = List.of(
                Experience.create(context, obs, new CandidateAction(type("#a"), 0.9, "test"),
                        ExperienceOutcome.SUCCESS, Duration.ofMillis(10)),
                Experience.create(context, obs, new CandidateAction(type("#b"), 0.9, "test"),
                        ExperienceOutcome.SUCCESS, Duration.ofMillis(10)),
                Experience.create(context, obs, new CandidateAction(type("#b"), 0.9, "test"),
                        ExperienceOutcome.SUCCESS, Duration.ofMillis(10))
        );

        MissionReportData data = withExperiences(context, experiences);

        assertEquals(1, data.learningSummary().newExperiences());
        assertEquals(1, data.learningSummary().updatedActions());
    }

    @Test
    void learningSummaryCountsImprovedVsDeclined() {

        MissionContext context = new MissionContext(mission());
        Observation obs = observation("https://example.com", input("#a"));

        List<Experience> experiences = List.of(
                Experience.create(context, obs, new CandidateAction(type("#good"), 0.9, "test"),
                        ExperienceOutcome.SUCCESS, Duration.ofMillis(10)),
                Experience.create(context, obs, new CandidateAction(type("#bad"), 0.9, "test"),
                        ExperienceOutcome.ERROR, Duration.ofMillis(10))
        );

        MissionReportData data = withExperiences(context, experiences);

        assertEquals(1, data.learningSummary().confidenceIncreased());
        assertEquals(1, data.learningSummary().confidenceReduced());
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
        return observation(url, Instant.now(), elements);
    }

    private Observation observation(String url, Instant capturedAt, ElementInfo... elements) {
        return new Observation(url, "Title", List.of(elements), List.of(), List.of(elements), List.of(), List.of(), capturedAt);
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
