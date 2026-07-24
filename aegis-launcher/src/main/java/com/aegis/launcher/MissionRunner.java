package com.aegis.launcher;

import com.aegis.core.Aegis;
import com.aegis.core.AegisReport;
import com.aegis.model.mission.Mission;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Shared CLI entry-point logic: run a mission via the public {@link Aegis}
 * facade, write all three report formats to disk, print a summary. Each
 * Main-style class just builds a Mission and hands it here. File-writing
 * and console output are launcher/CLI concerns — {@link Aegis} itself
 * stays disk-free so a library consumer isn't forced into this module's
 * choice of where reports live.
 */
final class MissionRunner {

    private MissionRunner() {
    }

    static void run(Mission mission) {

        System.out.println("=================================");
        System.out.println("         AEGIS v1.0");
        System.out.println("=================================");
        System.out.println();
        System.out.println("Mission: " + mission.name());
        System.out.println("Target : " + mission.parameter("baseUrl"));
        System.out.println();

        AegisReport report = Aegis.run(mission);

        System.out.println("Mission Plan:");
        report.plan().steps().forEach(step -> System.out.println("  - " + step));
        System.out.println();

        long timestamp = Instant.now().toEpochMilli();

        Path textReportPath = writeReport(report.textReport(), timestamp, "txt");
        Path htmlReportPath = writeReport(report.htmlReport(), timestamp, "html");
        Path jsonReportPath = writeReport(report.jsonReport(), timestamp, "json");

        System.out.println();
        System.out.println("=================================");
        System.out.println("Mission Finished");
        System.out.println("Status     : " + report.status());
        System.out.println("Report     : " + textReportPath);
        System.out.println("HTML Report: " + htmlReportPath);
        System.out.println("JSON Report: " + jsonReportPath);
        System.out.println("=================================");
    }

    private static Path writeReport(String content, long timestamp, String extension) {

        Path directory = Path.of("reports");
        Path path = directory.resolve("aegis-report-" + timestamp + "." + extension);

        try {
            Files.createDirectories(directory);
            Files.writeString(path, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + extension + " report", e);
        }

        return path;
    }
}
