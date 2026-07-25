package com.aegis.core.engine;

import com.aegis.core.plugin.AuthenticatedSession;
import com.aegis.core.plugin.SessionProvider;
import com.aegis.model.mission.Mission;

import java.util.Optional;

/**
 * Test-only {@link SessionProvider}, registered for real via {@code
 * META-INF/services} (see {@code src/test/resources}) — same discipline
 * as {@code TestUppercaseCredentialProvider} in {@code aegis-api}: it
 * only ever acts on a deliberately marked test mission (the
 * "test.throwOnSessionProvider" parameter), returning {@link
 * Optional#empty()} for every other mission so it's harmless to any
 * other test in this module that happens to exercise {@code
 * EngineFactory.create(Mission, ...)}.
 */
public class TestThrowingSessionProvider implements SessionProvider {

    @Override
    public Optional<AuthenticatedSession> createSession(Mission mission) {

        if ("true".equals(mission.parameter("test.throwOnSessionProvider"))) {
            throw new RuntimeException("simulated SessionProvider failure");
        }

        return Optional.empty();
    }
}
