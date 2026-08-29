package com.aegis.core.report;

import com.aegis.core.reasoning.learning.PatternStatistics;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningHonestyGlossaryTest {

    @Test
    void aSingleAttemptIsAFirstTryRegardlessOfOutcome() {
        assertEquals("First try", LearningHonestyGlossary.statusLabel(stats(1, 1)));
    }

    @Test
    void repeatedAndConsistentlySuccessfulIsConfirmedReliable() {
        assertEquals("Confirmed reliable", LearningHonestyGlossary.statusLabel(stats(3, 3)));
    }

    @Test
    void repeatedAndMostlyFailingIsConfirmedUnreliable() {
        assertEquals("Confirmed unreliable", LearningHonestyGlossary.statusLabel(stats(3, 1)));
    }

    @Test
    void repeatedWithMixedResultsIsMixedResults() {
        assertEquals("Mixed results", LearningHonestyGlossary.statusLabel(stats(3, 2)));
    }

    @Test
    void confirmedCountsIgnoreSingleAttemptActions() {

        List<PatternStatistics> actionPerformance = List.of(stats(1, 1), stats(1, 1), stats(3, 3));

        assertEquals(1, LearningHonestyGlossary.confirmedReliableCount(actionPerformance));
        assertEquals(0, LearningHonestyGlossary.confirmedUnreliableCount(actionPerformance));
    }

    @Test
    void confirmedUnreliableCountOnlyCountsRepeatedFailures() {

        List<PatternStatistics> actionPerformance = List.of(stats(1, 0), stats(3, 1));

        assertEquals(0, LearningHonestyGlossary.confirmedReliableCount(actionPerformance));
        assertEquals(1, LearningHonestyGlossary.confirmedUnreliableCount(actionPerformance));
    }

    @Test
    void honestLearningLineForAnAllFirstTryMissionNeverClaimsImprovement() {

        LearningSummary summary = new LearningSummary(3, 0, 3, 0, List.of(stats(1, 1), stats(1, 1), stats(1, 1)));

        String line = LearningHonestyGlossary.honestLearningLine(summary);

        assertTrue(line.contains("3 new"), line);
        assertTrue(line.contains("first attempt"), line);
        assertTrue(!line.contains("improved"), line);
        assertTrue(!line.contains("confirmed reliable"), line);
    }

    @Test
    void honestLearningLineForAMissionWithRepeatsShowsConfirmedCounts() {

        List<PatternStatistics> actionPerformance = List.of(stats(1, 1), stats(3, 3), stats(3, 1));
        LearningSummary summary = new LearningSummary(1, 2, 2, 1, actionPerformance);

        String line = LearningHonestyGlossary.honestLearningLine(summary);

        assertTrue(line.contains("1 new"), line);
        assertTrue(line.contains("2 repeated"), line);
        assertTrue(line.contains("1 confirmed reliable"), line);
        assertTrue(line.contains("1 confirmed unreliable"), line);
    }

    private PatternStatistics stats(long total, long successful) {
        Action action = new Action(
                UUID.randomUUID(), ActionType.CLICK, "[id='x']", null, "test", 0.8, "test",
                Duration.ofSeconds(5), Instant.now(), "button");
        return new PatternStatistics(action, total, successful, total - successful, (double) successful / total);
    }
}
