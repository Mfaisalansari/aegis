package com.aegis.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code aegis report <path-to-json-report>}
 *
 * Reads one of the {@code .json} report files {@code Launcher} already
 * writes on every mission run (Phase 7 Stage 3) and prints a compact
 * human-readable summary — useful as a lightweight "what happened" check
 * from a separate CI step, without opening the full HTML/JSON report.
 *
 * Deliberately does NOT re-render text/HTML from the JSON — {@code
 * Launcher} already writes all 3 formats every run, and building a full
 * JSON→{@code MissionReportData} deserializer to enable regeneration
 * would be new machinery for a need nobody has. This command only reads.
 */
final class ReportCommand {

    private ReportCommand() {
    }

    static int run(String[] args) {

        if (args.length == 0) {
            return fail("Missing required <path-to-json-report>");
        }

        Path path = Path.of(args[0]);
        ObjectMapper mapper = new ObjectMapper();

        JsonNode root;

        try {
            root = mapper.readTree(Files.readString(path));
        } catch (IOException e) {
            return fail("Failed to read report: " + path + " (" + e.getMessage() + ")");
        }

        JsonNode mission = root.path("mission");
        JsonNode coverage = root.path("coverage");
        JsonNode findings = root.path("findings");
        JsonNode bugClusters = root.path("bugClusters");

        String status = mission.path("status").asText("UNKNOWN");

        System.out.println("Mission     : " + mission.path("name").asText("?"));
        System.out.println("Goal        : " + mission.path("goal").asText("?"));
        System.out.println("Status      : " + status);
        System.out.println("Duration    : " + mission.path("durationMs").asLong(0) + "ms");
        System.out.println("Actions     : " + mission.path("actionsExecuted").asInt(0));
        System.out.println("Avg conf.   : " + mission.path("averageConfidence").asDouble(0));
        System.out.println("Coverage    : " + coverage.path("coveragePercent").asDouble(0) + "% ("
                + coverage.path("elementsInteracted").asInt(0) + "/" + coverage.path("elementsDiscovered").asInt(0) + " elements)");
        System.out.println("Findings    : " + findings.size());

        if (bugClusters.size() > 0) {
            System.out.println();
            System.out.println("Bug clusters:");
            for (JsonNode cluster : bugClusters) {
                System.out.println("  [" + cluster.path("severity").asText("?") + "] "
                        + cluster.path("summary").asText("?")
                        + " (x" + cluster.path("occurrenceCount").asInt(0) + ")");
            }
        }

        String recommendation = root.path("recommendation").asText(null);
        if (recommendation != null && !recommendation.isBlank()) {
            System.out.println();
            System.out.println("Recommendation: " + recommendation);
        }

        return switch (status) {
            case "SUCCESS" -> 0;
            case "FAILED" -> 1;
            case "PARTIAL" -> 2;
            default -> 1;
        };
    }

    private static int fail(String message) {
        System.err.println("Error: " + message);
        printUsage();
        return 1;
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  aegis report <path-to-json-report>

                Prints a compact summary of a JSON report already written by
                `aegis run` (or any Aegis.run(...) caller). Exit code mirrors the
                mission's own status: 0 = SUCCESS, 1 = FAILED, 2 = PARTIAL.
                """);
    }
}
