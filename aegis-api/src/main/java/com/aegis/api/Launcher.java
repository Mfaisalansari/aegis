package com.aegis.api;

import com.aegis.core.Aegis;
import com.aegis.core.AegisReport;
import com.aegis.core.browser.BrowserConfig;
import com.aegis.model.mission.Mission;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Runs a mission end-to-end and writes all three report formats to disk —
 * {@code aegis-launcher}'s {@code MissionRunner} logic promoted up a
 * layer and generalized. {@link Aegis} itself stays disk-free; this is
 * the batteries-included convenience default a sample project's
 * {@code main()} calls with one line.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void run(AegisApplication application) {

        System.out.println("Application: " + application.name());
        System.out.println();

        AegisConfig config = application.config();
        run(application.mission(), config.browser(), config.report().directory());
    }

    public static void run(Mission mission) {
        run(mission, BrowserConfig.defaults());
    }

    public static void run(Mission mission, BrowserConfig browserConfig) {
        run(mission, browserConfig, ReportConfig.defaults().directory());
    }

    public static void run(Mission mission, BrowserConfig browserConfig, String reportsDirectory) {

        System.out.println("=================================");
        System.out.println("         AEGIS v1.0");
        System.out.println("=================================");
        System.out.println();
        System.out.println("Mission: " + mission.name());
        System.out.println("Target : " + mission.parameter("baseUrl"));
        System.out.println();

        AegisReport report = Aegis.run(mission, browserConfig);

        System.out.println("Mission Plan:");
        report.plan().steps().forEach(step -> System.out.println("  - " + step));
        System.out.println();

        long timestamp = Instant.now().toEpochMilli();

        Path textReportPath = writeReport(report.textReport(), timestamp, "txt", reportsDirectory);
        Path htmlReportPath = writeReport(report.htmlReport(), timestamp, "html", reportsDirectory);
        Path jsonReportPath = writeReport(report.jsonReport(), timestamp, "json", reportsDirectory);

        System.out.println();
        System.out.println("=================================");
        System.out.println("Mission Finished");
        System.out.println("Status     : " + report.status());
        System.out.println("Report     : " + textReportPath);
        System.out.println("HTML Report: " + htmlReportPath);
        System.out.println("JSON Report: " + jsonReportPath);
        System.out.println("=================================");
    }

    private static Path writeReport(String content, long timestamp, String extension, String reportsDirectory) {

        Path directory = Path.of(reportsDirectory);
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
