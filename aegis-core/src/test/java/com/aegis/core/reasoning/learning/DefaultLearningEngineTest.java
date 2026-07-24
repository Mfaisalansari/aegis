package com.aegis.core.reasoning.learning;

import com.aegis.core.reasoning.experience.ExperienceRepository;
import com.aegis.core.reasoning.experience.InMemoryExperienceRepository;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.experience.Experience;
import com.aegis.model.experience.ExperienceOutcome;
import com.aegis.model.mission.Mission;
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

class DefaultLearningEngineTest {

    private final ExperienceRepository repository = new InMemoryExperienceRepository();

    private final DefaultLearningEngine learningEngine =
            new DefaultLearningEngine(repository, new DefaultPatternAnalyzer());

    @Test
    void missionWithNoExperiencesProducesAnEmptyResult() {

        MissionContext context = new MissionContext(mission());

        LearningResult result = learningEngine.learn(context);

        assertTrue(result.isEmpty());
        assertEquals(0.0, result.adjustmentFor(ActionType.CLICK, "#submit"));
    }

    @Test
    void highSuccessRateProducesAPositiveAdjustment() {

        Mission mission = mission();

        // 9/10 SUCCESS -> 0.90 success rate -> +0.20 per DefaultLearningEngine.calculateAdjustment
        record(mission, ActionType.CLICK, "#submit", ExperienceOutcome.SUCCESS, 9);
        record(mission, ActionType.CLICK, "#submit", ExperienceOutcome.ERROR, 1);

        LearningResult result = learningEngine.learn(new MissionContext(mission));

        assertEquals(0.20, result.adjustmentFor(ActionType.CLICK, "#submit"), 0.0001);
    }

    @Test
    void lowSuccessRateProducesANegativeAdjustment() {

        Mission mission = mission();

        // 1/5 SUCCESS -> 0.20 success rate -> -0.20 per DefaultLearningEngine.calculateAdjustment
        record(mission, ActionType.CLICK, "#flaky", ExperienceOutcome.SUCCESS, 1);
        record(mission, ActionType.CLICK, "#flaky", ExperienceOutcome.ERROR, 4);

        LearningResult result = learningEngine.learn(new MissionContext(mission));

        assertEquals(-0.20, result.adjustmentFor(ActionType.CLICK, "#flaky"), 0.0001);
    }

    @Test
    void adjustmentIsScopedToItsOwnActionKeyOnly() {

        Mission mission = mission();

        record(mission, ActionType.CLICK, "#submit", ExperienceOutcome.SUCCESS, 9);
        record(mission, ActionType.CLICK, "#submit", ExperienceOutcome.ERROR, 1);

        LearningResult result = learningEngine.learn(new MissionContext(mission));

        // A target never seen before has no learned adjustment.
        assertEquals(0.0, result.adjustmentFor(ActionType.CLICK, "#never-seen"));
        assertEquals(0.0, result.adjustmentFor(ActionType.TYPE, "#submit"));
    }

    private void record(Mission mission, ActionType type, String target, ExperienceOutcome outcome, int count) {

        for (int i = 0; i < count; i++) {

            Action action = new Action(
                    UUID.randomUUID(), type, target, "", "test",
                    1.0, "test", Duration.ofSeconds(5), Instant.now(), ""
            );

            MissionContext context = new MissionContext(mission);

            Observation observation = new Observation(
                    "https://example.com", "Title",
                    List.of(), List.of(), List.of(), List.of(), List.of(),
                    Instant.now()
            );

            Experience experience = Experience.create(
                    context,
                    observation,
                    new CandidateAction(action, action.confidence(), action.reasoning()),
                    outcome,
                    Duration.ofMillis(50)
            );

            repository.save(experience);
        }
    }

    private Mission mission() {
        return new Mission(UUID.randomUUID(), "Test", "Test", Map.of());
    }
}
