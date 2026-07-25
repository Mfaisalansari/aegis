package com.aegis.cli;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValidateCommandTest {

    @Test
    void validSingleMissionConfigResolvesCleanly() throws IOException {

        Path config = writeTempFile("""
                application:
                  baseUrl: https://example.com
                  username: someone
                  password: secret
                mission:
                  strategy: greedy
                """);

        try {
            assertEquals(0, ValidateCommand.run(new String[] {"--config", config.toString()}));
        } finally {
            Files.deleteIfExists(config);
        }
    }

    @Test
    void validEnterpriseConfigResolvesWithEnvAndMission() throws IOException {

        Path config = writeTempFile("""
                environments:
                  dev:
                    application:
                      baseUrl: https://dev.example.com
                missions:
                  smoke-test:
                    mission:
                      strategy: greedy
                """);

        try {
            assertEquals(0, ValidateCommand.run(new String[] {"--config", config.toString(), "--env", "dev", "--mission", "smoke-test"}));
        } finally {
            Files.deleteIfExists(config);
        }
    }

    @Test
    void enterpriseConfigWithoutEnvAndMissionFailsClearly() throws IOException {

        Path config = writeTempFile("""
                environments:
                  dev:
                    application:
                      baseUrl: https://dev.example.com
                missions:
                  smoke-test:
                    mission:
                      strategy: greedy
                """);

        try {
            assertEquals(1, ValidateCommand.run(new String[] {"--config", config.toString()}));
        } finally {
            Files.deleteIfExists(config);
        }
    }

    @Test
    void unknownMissionNameFails() throws IOException {

        Path config = writeTempFile("""
                environments:
                  dev:
                    application:
                      baseUrl: https://dev.example.com
                missions:
                  smoke-test:
                    mission:
                      strategy: greedy
                """);

        try {
            assertEquals(1, ValidateCommand.run(new String[] {"--config", config.toString(), "--env", "dev", "--mission", "does-not-exist"}));
        } finally {
            Files.deleteIfExists(config);
        }
    }

    @Test
    void missingFileFailsClearly() {
        assertEquals(1, ValidateCommand.run(new String[] {"--config", "/no/such/file.yml"}));
    }

    @Test
    void missingConfigFlagFails() {
        assertEquals(1, ValidateCommand.run(new String[0]));
    }

    private Path writeTempFile(String content) throws IOException {
        Path path = Files.createTempFile("aegis-validate-test", ".yml");
        Files.writeString(path, content);
        return path;
    }
}
