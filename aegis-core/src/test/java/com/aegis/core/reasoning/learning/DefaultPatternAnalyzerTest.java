package com.aegis.core.reasoning.learning;

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DefaultPatternAnalyzerTest {

    private final DefaultPatternAnalyzer analyzer = new DefaultPatternAnalyzer();

    @Test
    void groupsExperiencesByTypeAndTargetEvenThoughEachActionIsAFreshRecordInstance() {

        // Each of these is a distinct Action instance (fresh random UUID and
        // createdAt), exactly as a real candidate generator would produce
        // on every iteration. If analyze() grouped by the raw Action record
        // (as it did before the fix), Action's equals()/hashCode() would
        // treat every one of these as different and each would land in its
        // own singleton group.
        Action click1 = click();
        Action click2 = click();
        Action click3 = click();
        Action click4 = click();

        assertNotEquals(click1, click2, "test setup should use distinct Action instances");

        List<Experience> experiences = List.of(
                experience(click1, ExperienceOutcome.SUCCESS),
                experience(click2, ExperienceOutcome.SUCCESS),
                experience(click3, ExperienceOutcome.SUCCESS),
                experience(click4, ExperienceOutcome.ERROR)
        );

        Map<Action, PatternStatistics> result = analyzer.analyze(experiences);

        assertEquals(1, result.size(), "same type+target should collapse into a single group");

        PatternStatistics stats = result.values().iterator().next();

        assertEquals(4, stats.totalExecutions());
        assertEquals(3, stats.successfulExecutions());
        assertEquals(1, stats.failedExecutions());
        assertEquals(0.75, stats.successRate(), 0.0001);
    }

    @Test
    void keepsDifferentTargetsInSeparateGroups() {

        List<Experience> experiences = List.of(
                experience(click(), ExperienceOutcome.SUCCESS),
                experience(new Action(
                        UUID.randomUUID(), ActionType.CLICK, "#other", "", "test",
                        1.0, "test", Duration.ofSeconds(5), Instant.now(), ""
                ), ExperienceOutcome.SUCCESS)
        );

        Map<Action, PatternStatistics> result = analyzer.analyze(experiences);

        assertEquals(2, result.size());
    }

    @Test
    void emptyInputProducesEmptyResult() {
        assertEquals(Map.of(), analyzer.analyze(List.of()));
        assertEquals(Map.of(), analyzer.analyze(null));
    }

    private Action click() {
        return new Action(
                UUID.randomUUID(), ActionType.CLICK, "#submit", "", "test",
                1.0, "test", Duration.ofSeconds(5), Instant.now(), ""
        );
    }

    private Experience experience(Action action, ExperienceOutcome outcome) {

        MissionContext context = new MissionContext(
                new Mission(UUID.randomUUID(), "Test", "Test", Map.of()));

        Observation observation = new Observation(
                "https://example.com", "Title",
                List.of(), List.of(), List.of(), List.of(), List.of(),
                Instant.now()
        );

        return Experience.create(
                context,
                observation,
                new CandidateAction(action, action.confidence(), action.reasoning()),
                outcome,
                Duration.ofMillis(50)
        );
    }
}
