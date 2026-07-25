package com.aegis.api;

import com.aegis.core.browser.BrowserConfig;
import com.aegis.core.reasoning.scorer.ActionScorerRegistry;
import com.aegis.core.reasoning.value.InputValueResolverRegistry;
import com.aegis.model.mission.Mission;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MissionBuilderTest {

    @Test
    void baseUrlSetsTheBaseUrlParameter() {
        Mission mission = MissionBuilder.create("Test", "Test").baseUrl("https://example.com").build();
        assertEquals("https://example.com", mission.parameter("baseUrl"));
    }

    @Test
    void credentialsSetsUsernameAndPassword() {
        Mission mission = MissionBuilder.create("Test", "Test").credentials("bob", "secret").build();
        assertEquals("bob", mission.parameter("username"));
        assertEquals("secret", mission.parameter("password"));
    }

    @Test
    void successWhenUrlContainsSetsSuccessUrlContains() {
        Mission mission = MissionBuilder.create("Test", "Test").successWhenUrlContains("done").build();
        assertEquals("done", mission.parameter("successUrlContains"));
    }

    @Test
    void strategyUsesTheSameKeyActionScorerRegistryReads() {
        Mission mission = MissionBuilder.create("Test", "Test").strategy("coverage-aware").build();
        assertEquals("coverage-aware", mission.parameter(ActionScorerRegistry.PARAMETER_KEY));
    }

    @Test
    void inputStrategyUsesTheSameKeyInputValueResolverRegistryReads() {
        Mission mission = MissionBuilder.create("Test", "Test").inputStrategy("edge-case").build();
        assertEquals("edge-case", mission.parameter(InputValueResolverRegistry.PARAMETER_KEY));
    }

    @Test
    void maxIterationsIsStoredAsAString() {
        Mission mission = MissionBuilder.create("Test", "Test").maxIterations(42).build();
        assertEquals("42", mission.parameter("maxIterations"));
    }

    @Test
    void booleanFlagsOnlySetTheParameterWhenEnabled() {

        Mission enabled = MissionBuilder.create("Test", "Test").interruptions(true).build();
        Mission disabled = MissionBuilder.create("Test", "Test").interruptions(false).build();

        assertEquals("enabled", enabled.parameter("interruptions"));
        assertNull(disabled.parameter("interruptions"));
    }

    @Test
    void doubleClicksAndRaceConditionsFollowTheSamePattern() {

        Mission mission = MissionBuilder.create("Test", "Test")
                .doubleClicks(true)
                .raceConditions(true)
                .build();

        assertEquals("enabled", mission.parameter("doubleClicks"));
        assertEquals("enabled", mission.parameter("raceConditions"));
    }

    @Test
    void parameterEscapeHatchSetsAnyKey() {
        Mission mission = MissionBuilder.create("Test", "Test").parameter("custom", "value").build();
        assertEquals("value", mission.parameter("custom"));
    }

    @Test
    void nullValuesAreNotStoredAtAll() {
        Mission mission = MissionBuilder.create("Test", "Test").credentials(null, null).build();
        assertNull(mission.parameter("username"));
        assertNull(mission.parameter("password"));
    }

    @Test
    void buildProducesADifferentIdEachTime() {

        MissionBuilder builder = MissionBuilder.create("Test", "Test");

        assertNotEquals(builder.build().id(), builder.build().id());
    }

    @Test
    void fromConfigPopulatesEveryFieldFromAegisConfig() {

        AegisConfig config = new AegisConfig(
                new ApplicationConfig("https://example.com", "bob", "secret", "done"),
                new BrowserConfig("chromium", true),
                new MissionConfig("My Mission", "Description", "coverage-aware", 15, "edge-case", true, true, true),
                ReportConfig.defaults()
        );

        Mission mission = MissionBuilder.from(config).build();

        assertEquals("My Mission", mission.name());
        assertEquals("Description", mission.description());
        assertEquals("https://example.com", mission.parameter("baseUrl"));
        assertEquals("bob", mission.parameter("username"));
        assertEquals("secret", mission.parameter("password"));
        assertEquals("done", mission.parameter("successUrlContains"));
        assertEquals("coverage-aware", mission.parameter(ActionScorerRegistry.PARAMETER_KEY));
        assertEquals("edge-case", mission.parameter(InputValueResolverRegistry.PARAMETER_KEY));
        assertEquals("15", mission.parameter("maxIterations"));
        assertEquals("enabled", mission.parameter("interruptions"));
        assertEquals("enabled", mission.parameter("doubleClicks"));
        assertEquals("enabled", mission.parameter("raceConditions"));
    }

    @Test
    void fromConfigOmitsMaxIterationsWhenNotSet() {

        Mission mission = MissionBuilder.from(AegisConfig.defaults()).build();

        assertNull(mission.parameter("maxIterations"));
    }
}
