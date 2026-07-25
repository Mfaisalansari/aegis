package com.aegis.core.engine;

import com.aegis.core.browser.BrowserConfig;
import com.aegis.model.mission.Mission;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 5 hardening regression test: an already-launched browser must
 * not leak its Playwright driver subprocess if a discovered
 * {@code SessionProvider} throws — see the try/catch added around the
 * Identity Integration block in {@code EngineFactory.create(Mission, BrowserConfig)}.
 * Uses a real {@code BrowserConfig.type} matched against a test-only
 * {@code BrowserFactory}/{@code SessionProvider} discovered for real via
 * {@code META-INF/services}, not a hand-wired fake standing in for
 * ServiceLoader — same discipline as {@code AegisConfigLoaderTest}'s
 * {@code CredentialProvider} coverage.
 */
class EngineFactoryResourceLeakTest {

    @Test
    void aThrowingSessionProviderStillClosesTheBrowser() {

        Mission mission = new Mission(
                UUID.randomUUID(), "Leak Probe", "desc",
                Map.of("test.throwOnSessionProvider", "true"));

        BrowserConfig config = new BrowserConfig("test-leak-probe", true);

        assertThrows(RuntimeException.class, () -> EngineFactory.create(mission, config));

        assertNotNull(TestLeakProbeBrowserFactory.last());
        assertTrue(TestLeakProbeBrowserFactory.last().wasClosed());
    }

    @Test
    void aSuccessfulCreateDoesNotCloseTheBrowser() {

        Mission mission = new Mission(UUID.randomUUID(), "No Leak", "desc", Map.of());

        BrowserConfig config = new BrowserConfig("test-leak-probe", true);

        EngineFactory.CreatedEngine created = EngineFactory.create(mission, config);

        assertNotNull(created.engine());
        assertNotNull(TestLeakProbeBrowserFactory.last());
        assertFalse(TestLeakProbeBrowserFactory.last().wasClosed());
    }
}
