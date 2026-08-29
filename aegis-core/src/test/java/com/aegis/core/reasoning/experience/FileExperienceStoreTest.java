package com.aegis.core.reasoning.experience;

import com.aegis.core.reasoning.learning.DefaultPatternAnalyzer;
import com.aegis.core.reasoning.learning.PatternStatistics;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.experience.Experience;
import com.aegis.model.experience.ExperienceOutcome;
import com.aegis.model.mission.Mission;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileExperienceStoreTest {

    private final DefaultPatternAnalyzer analyzer = new DefaultPatternAnalyzer();

    @Test
    void roundTripsTotalSuccessfulAndFailedCountsThroughPatternAnalyzer(@TempDir Path dir) {

        FileExperienceStore store = new FileExperienceStore(dir);
        Mission mission = mission("https://example.com", "Log in");

        store.recordOutcome(mission, List.of(
                experience(mission, click(), ExperienceOutcome.SUCCESS),
                experience(mission, click(), ExperienceOutcome.SUCCESS),
                experience(mission, click(), ExperienceOutcome.SUCCESS),
                experience(mission, click(), ExperienceOutcome.ERROR)
        ));

        List<Experience> reloaded = store.loadPriorExperiences(mission);
        Map<Action, PatternStatistics> stats = analyzer.analyze(reloaded);

        assertEquals(1, stats.size());
        PatternStatistics only = stats.values().iterator().next();
        assertEquals(4, only.totalExecutions());
        assertEquals(3, only.successfulExecutions());
        assertEquals(1, only.failedExecutions());
    }

    @Test
    void twoDifferentConfigurationsGetSeparateHistory(@TempDir Path dir) {

        FileExperienceStore store = new FileExperienceStore(dir);
        Mission login = mission("https://example.com", "Log in");
        Mission checkout = mission("https://example.com", "Check out");

        store.recordOutcome(login, List.of(experience(login, click(), ExperienceOutcome.SUCCESS)));

        assertEquals(1, store.loadPriorExperiences(login).size());
        assertTrue(store.loadPriorExperiences(checkout).isEmpty(), "a different description must not share history");
    }

    @Test
    void sameParametersAndDescriptionShareHistoryDespiteDifferentMissionIdAndName(@TempDir Path dir) {

        FileExperienceStore store = new FileExperienceStore(dir);
        Mission firstSubmission = mission("https://example.com", "Log in");

        store.recordOutcome(firstSubmission, List.of(experience(firstSubmission, click(), ExperienceOutcome.SUCCESS)));

        // A fresh submission of the identical configuration: new random Mission id, same parameters+description.
        Mission secondSubmission = new Mission(
                UUID.randomUUID(), "A different name", "Log in", firstSubmission.parameters());

        assertEquals(1, store.loadPriorExperiences(secondSubmission).size());
    }

    @Test
    void recordOutcomeCalledTwiceAccumulatesRatherThanOverwrites(@TempDir Path dir) {

        FileExperienceStore store = new FileExperienceStore(dir);
        Mission mission = mission("https://example.com", "Log in");
        Action click = click();

        store.recordOutcome(mission, List.of(experience(mission, click, ExperienceOutcome.SUCCESS)));
        store.recordOutcome(mission, List.of(
                experience(mission, click, ExperienceOutcome.SUCCESS),
                experience(mission, click, ExperienceOutcome.ERROR)
        ));

        Map<Action, PatternStatistics> stats = analyzer.analyze(store.loadPriorExperiences(mission));
        PatternStatistics only = stats.values().iterator().next();

        assertEquals(3, only.totalExecutions());
        assertEquals(2, only.successfulExecutions());
    }

    @Test
    void missingHistoryReturnsEmptyAndRecordOutcomeCreatesTheDirectory() throws IOException {

        Path dir = Files.createTempDirectory("aegis-experience-test").resolve("nested");
        FileExperienceStore store = new FileExperienceStore(dir);
        Mission mission = mission("https://example.com", "Log in");

        assertTrue(store.loadPriorExperiences(mission).isEmpty());

        store.recordOutcome(mission, List.of(experience(mission, click(), ExperienceOutcome.SUCCESS)));

        assertTrue(Files.isDirectory(dir));
        assertEquals(1, store.loadPriorExperiences(mission).size());
    }

    @Test
    void aCorruptFileIsSkippedWithoutThrowing(@TempDir Path dir) throws IOException {

        Mission mission = mission("https://example.com", "Log in");
        FileExperienceStore store = new FileExperienceStore(dir);

        // Force the file to exist first so we know its exact fingerprinted name, then corrupt it.
        store.recordOutcome(mission, List.of(experience(mission, click(), ExperienceOutcome.SUCCESS)));
        try (var files = Files.list(dir)) {
            Path file = files.findFirst().orElseThrow();
            Files.writeString(file, "{ not valid json ");
        }

        assertTrue(store.loadPriorExperiences(mission).isEmpty());
    }

    @Test
    void anUnknownActionTypeEntryIsSkippedWithoutBreakingTheOthers(@TempDir Path dir) throws IOException {

        Mission mission = mission("https://example.com", "Log in");
        FileExperienceStore store = new FileExperienceStore(dir);

        store.recordOutcome(mission, List.of(experience(mission, click(), ExperienceOutcome.SUCCESS)));

        try (var files = Files.list(dir)) {
            Path file = files.findFirst().orElseThrow();
            String original = Files.readString(file);
            // Insert a bad entry before the final closing "]" of the "actions" array — a plain
            // replace("]", ...) would also corrupt the "[id='submit']" target value's own "]".
            int lastBracket = original.lastIndexOf(']');
            String withBadEntry = original.substring(0, lastBracket)
                    + ",{\"actionType\":\"NOT_A_REAL_TYPE\",\"actionTarget\":\"x\",\"total\":1,\"successful\":1}"
                    + original.substring(lastBracket);
            Files.writeString(file, withBadEntry);
        }

        assertEquals(1, store.loadPriorExperiences(mission).size());
    }

    @Test
    void noOpStoreNeverPersistsOrReturnsAnything(@TempDir Path dir) {

        Mission mission = mission("https://example.com", "Log in");

        ExperienceStore.NO_OP.recordOutcome(mission, List.of(experience(mission, click(), ExperienceOutcome.SUCCESS)));

        assertTrue(ExperienceStore.NO_OP.loadPriorExperiences(mission).isEmpty());
    }

    private Mission mission(String baseUrl, String description) {
        return new Mission(UUID.randomUUID(), "AEGIS Mission", description, Map.of("baseUrl", baseUrl));
    }

    private Action click() {
        return new Action(
                UUID.randomUUID(), ActionType.CLICK, "[id='submit']", "", "test",
                1.0, "test", Duration.ofSeconds(5), Instant.now(), "");
    }

    private Experience experience(Mission mission, Action action, ExperienceOutcome outcome) {

        MissionContext context = new MissionContext(mission);

        Observation observation = new Observation(
                "https://example.com", "Title", List.of(), List.of(), List.of(), List.of(), List.of(), Instant.now());

        return Experience.create(
                context, observation, new CandidateAction(action, action.confidence(), action.reasoning()),
                outcome, Duration.ofMillis(50));
    }
}
