package com.aegis.core.reasoning.generator;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositeCandidateActionGeneratorTest {

    @Test
    void combinesCandidatesFromEverySource() {

        CandidateActionGenerator first = context -> List.of(candidate(ActionType.CLICK, "#a"));
        CandidateActionGenerator second = context -> List.of(candidate(ActionType.REFRESH, ""));

        CompositeCandidateActionGenerator composite =
                new CompositeCandidateActionGenerator(List.of(first, second));

        List<CandidateAction> combined = composite.generate(context());

        assertEquals(2, combined.size());
    }

    @Test
    void handlesASourceThatProducesNothing() {

        CandidateActionGenerator empty = context -> List.of();
        CandidateActionGenerator withOne = context -> List.of(candidate(ActionType.REFRESH, ""));

        CompositeCandidateActionGenerator composite =
                new CompositeCandidateActionGenerator(List.of(empty, withOne));

        assertTrue(composite.generate(context()).size() == 1);
    }

    private MissionContext context() {
        return new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", Map.of()));
    }

    private CandidateAction candidate(ActionType type, String target) {
        Action action = new Action(UUID.randomUUID(), type, target, "", "reasoning", 0.5,
                "expected", Duration.ofSeconds(1), Instant.now(), "");
        return new CandidateAction(action, 0.5, "reasoning");
    }
}
