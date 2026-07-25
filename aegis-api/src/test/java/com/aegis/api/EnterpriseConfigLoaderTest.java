package com.aegis.api;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnterpriseConfigLoaderTest {

    @Test
    void parsesEnvironmentsAndMissionsCorrectly() {

        String yaml = """
                environments:
                  dev:
                    application:
                      baseUrl: https://dev.example.com
                      username: dev-user
                    browser:
                      headless: true
                  production:
                    application:
                      baseUrl: https://example.com
                      password: test-prefix:secret
                missions:
                  smoke-test:
                    mission:
                      strategy: greedy
                      maxIterations: 10
                  full-regression:
                    mission:
                      strategy: adaptive
                    application:
                      successUrlContains: done
                report:
                  directory: custom-reports
                """;

        EnterpriseConfig config = EnterpriseConfigLoader.load(inputStream(yaml));

        assertEquals(2, config.environments().size());
        assertEquals("https://dev.example.com", config.environments().get("dev").application().baseUrl());
        assertEquals("dev-user", config.environments().get("dev").application().username());
        assertTrue(config.environments().get("dev").browser().headless());

        // Real ServiceLoader-discovered credential resolution (see
        // AegisConfigLoaderTest / TestUppercaseCredentialProvider),
        // proven here too since environments go through the same path.
        assertEquals("SECRET", config.environments().get("production").application().password());

        assertEquals(2, config.missions().size());
        assertEquals("greedy", config.missions().get("smoke-test").mission().strategy());
        assertEquals(10, config.missions().get("smoke-test").mission().maxIterations());
        assertEquals("done", config.missions().get("full-regression").applicationOverrides().successUrlContains());

        assertEquals("custom-reports", config.report().directory());
    }

    @Test
    void aMissionWithNoApplicationSectionHasNoOverride() {

        String yaml = """
                missions:
                  smoke-test:
                    mission:
                      strategy: greedy
                """;

        EnterpriseConfig config = EnterpriseConfigLoader.load(inputStream(yaml));

        assertEquals(null, config.missions().get("smoke-test").applicationOverrides());
    }

    @Test
    void isEnterpriseShapedDetectsEnvironmentsOrMissionsKeys() throws Exception {

        assertTrue(isEnterpriseShaped("environments:\n  dev: {}\n"));
        assertTrue(isEnterpriseShaped("missions:\n  smoke-test: {}\n"));
        assertFalse(isEnterpriseShaped("application:\n  baseUrl: https://example.com\n"));
        assertFalse(isEnterpriseShaped(""));
    }

    private boolean isEnterpriseShaped(String yaml) throws Exception {

        Path file = Files.createTempFile("aegis-enterprise-test", ".yml");

        try {
            Files.writeString(file, yaml);
            return EnterpriseConfigLoader.isEnterpriseShaped(file);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private InputStream inputStream(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }
}
