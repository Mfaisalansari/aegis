package com.aegis.api;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AegisConfigLoaderTest {

    @Test
    void resolvesUsernameAndPasswordThroughARealDiscoveredCredentialProvider() {

        String yaml = """
                application:
                  baseUrl: https://example.com
                  username: test-prefix:bob
                  password: test-prefix:secret
                """;

        AegisConfig config = AegisConfigLoader.load(inputStream(yaml));

        // TestUppercaseCredentialProvider, discovered for real via
        // META-INF/services (src/test/resources) — not a fake standing
        // in for the ServiceLoader mechanism, the actual thing.
        assertEquals("BOB", config.application().username());
        assertEquals("SECRET", config.application().password());
    }

    @Test
    void leavesValuesUnresolvedWhenNoDiscoveredProviderSupportsThem() {

        String yaml = """
                application:
                  username: plain-value
                """;

        AegisConfig config = AegisConfigLoader.load(inputStream(yaml));

        assertEquals("plain-value", config.application().username());
    }

    @Test
    void parsesAFullYamlFileCorrectly() {

        String yaml = """
                application:
                  baseUrl: https://example.com
                  username: bob
                  password: secret
                  successUrlContains: done
                  contextFile: context.md
                browser:
                  type: firefox
                  headless: true
                mission:
                  name: My Mission
                  description: A description
                  strategy: coverage-aware
                  maxIterations: 15
                  inputStrategy: edge-case
                  interruptions: true
                  doubleClicks: true
                  raceConditions: true
                report:
                  directory: custom-reports
                """;

        AegisConfig config = AegisConfigLoader.load(inputStream(yaml));

        assertEquals("https://example.com", config.application().baseUrl());
        assertEquals("bob", config.application().username());
        assertEquals("secret", config.application().password());
        assertEquals("done", config.application().successUrlContains());
        assertEquals("context.md", config.application().contextFile());

        assertEquals("firefox", config.browser().type());
        assertTrue(config.browser().headless());

        assertEquals("My Mission", config.mission().name());
        assertEquals("A description", config.mission().description());
        assertEquals("coverage-aware", config.mission().strategy());
        assertEquals(15, config.mission().maxIterations());
        assertEquals("edge-case", config.mission().inputStrategy());
        assertTrue(config.mission().interruptions());
        assertTrue(config.mission().doubleClicks());
        assertTrue(config.mission().raceConditions());

        assertEquals("custom-reports", config.report().directory());
    }

    @Test
    void missingReportSectionFallsBackToTheDefaultDirectory() {

        AegisConfig config = AegisConfigLoader.load(inputStream("application:\n  baseUrl: https://example.com\n"));

        assertEquals("reports", config.report().directory());
    }

    @Test
    void blankReportDirectoryFallsBackToTheDefault() {

        AegisConfig config = AegisConfigLoader.load(inputStream("report:\n  directory: \"\"\n"));

        assertEquals("reports", config.report().directory());
    }

    @Test
    void missingSectionsFallBackToDefaults() {

        String yaml = """
                application:
                  baseUrl: https://example.com
                """;

        AegisConfig config = AegisConfigLoader.load(inputStream(yaml));

        assertEquals("chromium", config.browser().type());
        assertFalse(config.browser().headless());
        assertEquals(MissionConfig.defaults().name(), config.mission().name());
        assertNull(config.mission().strategy());
        assertNull(config.mission().maxIterations());
        assertFalse(config.mission().interruptions());
        assertEquals(ReportConfig.defaults().directory(), config.report().directory());
    }

    @Test
    void missingFieldsWithinASectionFallBackIndividually() {

        String yaml = """
                mission:
                  strategy: adaptive
                """;

        AegisConfig config = AegisConfigLoader.load(inputStream(yaml));

        assertEquals("adaptive", config.mission().strategy());
        assertEquals(MissionConfig.defaults().name(), config.mission().name());
        assertNull(config.mission().maxIterations());
    }

    @Test
    void anEmptyFileProducesAllDefaults() {

        AegisConfig config = AegisConfigLoader.load(inputStream(""));

        assertEquals(AegisConfig.defaults(), config);
    }

    @Test
    void malformedYamlThrowsAegisConfigException() {

        String malformed = "application:\n  baseUrl: [unterminated";

        assertThrows(AegisConfigException.class, () -> AegisConfigLoader.load(inputStream(malformed)));
    }

    @Test
    void aTopLevelListInsteadOfAMappingThrowsAegisConfigException() {

        String yaml = "- one\n- two\n";

        assertThrows(AegisConfigException.class, () -> AegisConfigLoader.load(inputStream(yaml)));
    }

    @Test
    void loadFromPathReadsARealFile() throws Exception {

        Path file = Files.createTempFile("aegis-config-test", ".yml");
        Files.writeString(file, "application:\n  baseUrl: https://example.com\n");

        try {
            AegisConfig config = AegisConfigLoader.load(file);
            assertEquals("https://example.com", config.application().baseUrl());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void loadFromAMissingPathThrowsAegisConfigExceptionWithActionableGuidance() {

        AegisConfigException exception = assertThrows(AegisConfigException.class,
                () -> AegisConfigLoader.load(Path.of("does-not-exist.yml")));

        // Not just "file not found" — resolving where it looked and how to
        // fix a working-directory mismatch (e.g. an IDE run configuration
        // that doesn't default to the module's own directory) is the whole
        // point of this message; a bare NoSuchFileException gives neither.
        assertTrue(exception.getMessage().contains("does-not-exist.yml"));
        assertTrue(exception.getMessage().contains("working directory"));
    }

    private InputStream inputStream(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }
}
