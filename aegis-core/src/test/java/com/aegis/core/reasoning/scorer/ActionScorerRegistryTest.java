package com.aegis.core.reasoning.scorer;

import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActionScorerRegistryTest {

    @Test
    void resolveKeyDefaultsToGreedyWhenTheParameterIsUnset() {

        ActionScorerRegistry registry = new ActionScorerRegistry(Map.of());
        MissionContext context = contextWithStrategy(null);

        assertEquals("greedy", registry.resolveKey(context));
    }

    @Test
    void resolveKeyReturnsTheExplicitlyRequestedStrategy() {

        ActionScorerRegistry registry = new ActionScorerRegistry(Map.of());
        MissionContext context = contextWithStrategy("coverage-aware");

        assertEquals("coverage-aware", registry.resolveKey(context));
    }

    @Test
    void resolveKeyMatchesWhicheverScorerResolveActuallyPicks() {

        ActionScorer coverageAware = (ctx, candidates) -> candidates.get(0);
        ActionScorerRegistry registry = new ActionScorerRegistry(Map.of("coverage-aware", coverageAware));
        MissionContext context = contextWithStrategy("coverage-aware");

        assertEquals("coverage-aware", registry.resolveKey(context));
        assertSame(coverageAware, registry.resolve(context));
    }

    @Test
    void resolveThrowsForAnUnknownStrategyKey() {

        ActionScorerRegistry registry = new ActionScorerRegistry(Map.of());
        MissionContext context = contextWithStrategy("not-a-real-strategy");

        assertThrows(IllegalArgumentException.class, () -> registry.resolve(context));
    }

    private static MissionContext contextWithStrategy(String strategy) {

        Map<String, String> parameters = strategy == null
                ? Map.of()
                : Map.of(ActionScorerRegistry.PARAMETER_KEY, strategy);

        return new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", parameters));
    }
}
