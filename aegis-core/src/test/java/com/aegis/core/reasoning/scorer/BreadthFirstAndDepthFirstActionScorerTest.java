package com.aegis.core.reasoning.scorer;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import com.aegis.model.reasoning.CandidateAction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BreadthFirstAndDepthFirstActionScorerTest {

    private final MissionContext context = new MissionContext(
            new Mission(UUID.randomUUID(), "Test", "Test", Map.of()));

    @Test
    void breadthFirstPrefersLocalActionOverALink() {

        CandidateAction typeUsername = candidate(ActionType.TYPE, "#username", "input", 0.85);
        CandidateAction followLink = candidate(ActionType.CLICK, "a:nth-of-type(2)", "a", 0.95);

        CandidateAction chosen = new BreadthFirstActionScorer()
                .choose(context, List.of(typeUsername, followLink));

        assertEquals("#username", chosen.action().target());
    }

    @Test
    void breadthFirstFollowsALinkOnlyWhenNothingLocalIsLeft() {

        CandidateAction followLink = candidate(ActionType.CLICK, "a:nth-of-type(2)", "a", 0.95);

        CandidateAction chosen = new BreadthFirstActionScorer()
                .choose(context, List.of(followLink));

        assertEquals("a:nth-of-type(2)", chosen.action().target());
    }

    @Test
    void depthFirstPrefersALinkOverALocalAction() {

        CandidateAction typeUsername = candidate(ActionType.TYPE, "#username", "input", 0.95);
        CandidateAction followLink = candidate(ActionType.CLICK, "a:nth-of-type(2)", "a", 0.40);

        CandidateAction chosen = new DepthFirstActionScorer()
                .choose(context, List.of(typeUsername, followLink));

        assertEquals("a:nth-of-type(2)", chosen.action().target());
    }

    @Test
    void depthFirstFallsBackToLocalActionWhenNoLinkExists() {

        CandidateAction typeUsername = candidate(ActionType.TYPE, "#username", "input", 0.85);

        CandidateAction chosen = new DepthFirstActionScorer()
                .choose(context, List.of(typeUsername));

        assertEquals("#username", chosen.action().target());
    }

    private CandidateAction candidate(ActionType type, String target, String elementTag, double confidence) {

        Action action = new Action(
                UUID.randomUUID(),
                type,
                target,
                "",
                "test",
                confidence,
                "test",
                Duration.ofSeconds(5),
                Instant.now(),
                elementTag
        );

        return new CandidateAction(action, confidence, "test");
    }
}
