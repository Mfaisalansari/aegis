package com.aegis.core.knowledge;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeAwareActionScorerTest {

    @Test
    void prefersACandidateResolvingToANodeNotYetCoveredByAPriorRunEvenAtLowerRawConfidence() throws IOException {

        CoverageStore store = new CoverageStore(Files.createTempDirectory("aegis-coverage-test"));
        store.merge("TestApp", Set.of("login"));

        MissionContext context = contextWithHistory("TestApp",
                observation("https://app/login"),
                action(ActionType.CLICK, "checkout-link"),
                observation("https://app/checkout"));

        CandidateAction leadsToUncovered = candidate(ActionType.CLICK, "checkout-link", 0.5);
        CandidateAction noKnownDestination = candidate(ActionType.CLICK, "unrelated-link", 0.6);

        KnowledgeAwareActionScorer scorer = new KnowledgeAwareActionScorer(store);
        CandidateAction chosen = scorer.choose(context, List.of(leadsToUncovered, noKnownDestination));

        assertSame(leadsToUncovered, chosen);
    }

    @Test
    void givesNoBonusToACandidateResolvingToANodeAlreadyCoveredByAPriorRun() throws IOException {

        CoverageStore store = new CoverageStore(Files.createTempDirectory("aegis-coverage-test"));
        store.merge("TestApp", Set.of("login"));

        MissionContext context = contextWithHistory("TestApp",
                observation("https://app/dashboard"),
                action(ActionType.CLICK, "login-link"),
                observation("https://app/login"));

        CandidateAction leadsToAlreadyCovered = candidate(ActionType.CLICK, "login-link", 0.5);
        CandidateAction higherConfidenceNoDestination = candidate(ActionType.CLICK, "unrelated-link", 0.6);

        KnowledgeAwareActionScorer scorer = new KnowledgeAwareActionScorer(store);
        CandidateAction chosen = scorer.choose(context, List.of(leadsToAlreadyCovered, higherConfidenceNoDestination));

        assertSame(higherConfidenceNoDestination, chosen);
    }

    @Test
    void throwsForAnEmptyCandidateList() throws IOException {

        CoverageStore store = new CoverageStore(Files.createTempDirectory("aegis-coverage-test"));
        MissionContext context = contextWithHistory("TestApp");

        KnowledgeAwareActionScorer scorer = new KnowledgeAwareActionScorer(store);

        assertThrows(IllegalArgumentException.class, () -> scorer.choose(context, List.of()));
    }

    @Test
    void strategyNameIsKnowledgeAware() {
        assertEquals("knowledge-aware", new KnowledgeAwareActionScorer().strategyName());
    }

    private MissionContext contextWithHistory(String missionName, Object... events) {

        MissionContext context = new MissionContext(new Mission(UUID.randomUUID(), missionName, "Test", Map.of()));

        for (Object event : events) {
            if (event instanceof Observation observation) {
                context.getExecutionState().setCurrentObservation(observation);
            } else if (event instanceof Action action) {
                context.getExecutionState().addAction(action);
            }
        }

        return context;
    }

    private Observation observation(String url) {
        return new Observation(url, "Title", List.of(), List.of(), List.of(), List.of(), List.of(), Instant.now());
    }

    private Action action(ActionType type, String target) {
        return new Action(UUID.randomUUID(), type, target, null, "test", 0.5, "test", Duration.ofSeconds(5), Instant.now(), "button");
    }

    private CandidateAction candidate(ActionType type, String target, double confidence) {
        return new CandidateAction(action(type, target), confidence, "test");
    }
}
