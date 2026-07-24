package com.aegis.core.reasoning.value;

import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InputValueResolverRegistryTest {

    private final InputValueResolver realistic = new DefaultInputValueResolver();
    private final InputValueResolver edgeCase = new EdgeCaseInputValueResolver();

    private final InputValueResolverRegistry registry = new InputValueResolverRegistry(
            Map.of("realistic", realistic, "edge-case", edgeCase));

    @Test
    void defaultsToRealisticWhenMissionSetsNoInputStrategy() {

        assertSame(realistic, registry.select(context(Map.of())));
    }

    @Test
    void selectsEdgeCaseWhenMissionRequestsIt() {

        assertSame(edgeCase, registry.select(context(Map.of("inputStrategy", "edge-case"))));
    }

    @Test
    void rejectsAnUnknownStrategyInsteadOfSilentlyFallingBack() {

        MissionContext context = context(Map.of("inputStrategy", "made-up"));

        assertThrows(IllegalArgumentException.class, () -> registry.select(context));
    }

    private MissionContext context(Map<String, String> parameters) {
        return new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", parameters));
    }
}
