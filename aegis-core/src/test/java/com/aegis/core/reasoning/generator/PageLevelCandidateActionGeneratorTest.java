package com.aegis.core.reasoning.generator;

import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import com.aegis.model.reasoning.CandidateAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageLevelCandidateActionGeneratorTest {

    private final PageLevelCandidateActionGenerator generator = new PageLevelCandidateActionGenerator();

    @Test
    void producesNothingByDefault() {

        MissionContext context = context(Map.of());

        assertTrue(generator.generate(context).isEmpty(),
                "must be opt-in — a mission that doesn't ask for interruptions shouldn't get any");
    }

    @Test
    void producesNothingWhenParameterIsSetToAnythingOtherThanEnabled() {

        MissionContext context = context(Map.of("interruptions", "true"));

        assertTrue(generator.generate(context).isEmpty());
    }

    @Test
    void producesRefreshAndBackWhenEnabled() {

        MissionContext context = context(Map.of("interruptions", "enabled"));

        List<CandidateAction> candidates = generator.generate(context);

        List<ActionType> types = candidates.stream()
                .map(candidate -> candidate.action().type())
                .collect(Collectors.toList());

        assertEquals(2, candidates.size());
        assertTrue(types.contains(ActionType.REFRESH));
        assertTrue(types.contains(ActionType.BACK));
    }

    @Test
    void candidatesAreNotTiedToAnyElement() {

        MissionContext context = context(Map.of("interruptions", "enabled"));

        for (CandidateAction candidate : generator.generate(context)) {
            assertEquals("", candidate.action().target());
            assertEquals("", candidate.action().elementTag());
        }
    }

    private MissionContext context(Map<String, String> parameters) {
        return new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", parameters));
    }
}
