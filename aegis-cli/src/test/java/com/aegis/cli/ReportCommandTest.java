package com.aegis.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportCommandTest {

    @Test
    void summarizesASuccessfulReportAndExitsZero() throws IOException {

        Path report = writeReport("""
                {
                  "mission": {"name": "SauceDemo Login", "goal": "Log in", "status": "SUCCESS", "durationMs": 4210, "actionsExecuted": 3, "averageConfidence": 0.82},
                  "coverage": {"elementsDiscovered": 32, "elementsInteracted": 3, "coveragePercent": 9.4},
                  "findings": [],
                  "bugClusters": [],
                  "recommendation": "No issues found."
                }
                """);

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int exitCode = captureStdout(out, () -> ReportCommand.run(new String[] {report.toString()}));

            assertEquals(0, exitCode);
            String printed = out.toString(StandardCharsets.UTF_8);
            assertTrue(printed.contains("SauceDemo Login"));
            assertTrue(printed.contains("SUCCESS"));
            assertTrue(printed.contains("No issues found."));

        } finally {
            Files.deleteIfExists(report);
        }
    }

    @Test
    void summarizesBugClustersAndExitsOneOnFailure() throws IOException {

        Path report = writeReport("""
                {
                  "mission": {"name": "OrangeHRM Login", "goal": "Log in", "status": "FAILED", "durationMs": 9000, "actionsExecuted": 20, "averageConfidence": 0.4},
                  "coverage": {"elementsDiscovered": 10, "elementsInteracted": 5, "coveragePercent": 50.0},
                  "findings": [{"severity": "HIGH", "summary": "Timeout"}],
                  "bugClusters": [{"severity": "HIGH", "summary": "Timeout clicking submit", "occurrenceCount": 3}],
                  "recommendation": "Investigate the submit button."
                }
                """);

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int exitCode = captureStdout(out, () -> ReportCommand.run(new String[] {report.toString()}));

            assertEquals(1, exitCode);
            String printed = out.toString(StandardCharsets.UTF_8);
            assertTrue(printed.contains("Timeout clicking submit"));
            assertTrue(printed.contains("x3"));

        } finally {
            Files.deleteIfExists(report);
        }
    }

    @Test
    void missingFileFailsClearly() {
        assertEquals(1, ReportCommand.run(new String[] {"/no/such/report.json"}));
    }

    @Test
    void missingArgumentFails() {
        assertEquals(1, ReportCommand.run(new String[0]));
    }

    private Path writeReport(String json) throws IOException {
        Path path = Files.createTempFile("aegis-report-test", ".json");
        Files.writeString(path, json);
        return path;
    }

    private int captureStdout(ByteArrayOutputStream out, java.util.function.IntSupplier action) {

        PrintStream original = System.out;

        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            return action.getAsInt();
        } finally {
            System.setOut(original);
        }
    }
}
