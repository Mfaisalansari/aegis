package com.aegis.api;

import com.aegis.core.browser.BrowserConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnterpriseConfigTest {

    private final EnterpriseConfig config = new EnterpriseConfig(
            Map.of(
                    "dev", new EnvironmentProfile(
                            new ApplicationConfig("https://dev.example.com", "dev-user", "dev-pass", "dev-success"),
                            new BrowserConfig("chromium", true)),
                    "production", new EnvironmentProfile(
                            new ApplicationConfig("https://example.com", "prod-user", "prod-pass", "prod-success"),
                            new BrowserConfig("chromium", true))
            ),
            Map.of(
                    "smoke-test", new MissionProfile(
                            new MissionConfig("Smoke Test", "Quick check", "greedy", 10, null, false, false, false),
                            null),
                    "full-regression", new MissionProfile(
                            new MissionConfig("Full Regression", "Thorough", "adaptive", 50, null, false, false, false),
                            new ApplicationConfig(null, null, null, "regression-success"))
            ),
            new ReportConfig("reports")
    );

    @Test
    void resolvesEnvironmentApplicationAndBrowserUnchangedWhenMissionHasNoOverride() {

        AegisConfig resolved = config.resolve("dev", "smoke-test");

        assertEquals("https://dev.example.com", resolved.application().baseUrl());
        assertEquals("dev-user", resolved.application().username());
        assertEquals("dev-success", resolved.application().successUrlContains());
        assertTrue(resolved.browser().headless());
    }

    @Test
    void resolvesMissionSettingsFromTheMissionProfile() {

        AegisConfig resolved = config.resolve("production", "full-regression");

        assertEquals("adaptive", resolved.mission().strategy());
        assertEquals(50, resolved.mission().maxIterations());
    }

    @Test
    void missionApplicationOverrideWinsOverEnvironmentForThatField() {

        AegisConfig resolved = config.resolve("production", "full-regression");

        // baseUrl/username/password still come from the environment;
        // only successUrlContains was overridden by the mission profile.
        assertEquals("https://example.com", resolved.application().baseUrl());
        assertEquals("prod-user", resolved.application().username());
        assertEquals("regression-success", resolved.application().successUrlContains());
    }

    @Test
    void unknownEnvironmentThrowsAClearError() {

        AegisConfigException e = assertThrows(AegisConfigException.class,
                () -> config.resolve("staging", "smoke-test"));

        assertTrue(e.getMessage().contains("staging"));
    }

    @Test
    void unknownMissionThrowsAClearError() {

        AegisConfigException e = assertThrows(AegisConfigException.class,
                () -> config.resolve("dev", "load-test"));

        assertTrue(e.getMessage().contains("load-test"));
    }
}
