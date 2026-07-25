package com.aegis.api;

import com.aegis.core.Aegis;
import com.aegis.core.AegisReport;
import com.aegis.core.browser.BrowserConfig;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionStatus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

/**
 * Runs a mission end-to-end and writes all three report formats to disk —
 * {@code aegis-launcher}'s {@code MissionRunner} logic promoted up a
 * layer and generalized. {@link Aegis} itself stays disk-free; this is
 * the batteries-included convenience default a sample project's
 * {@code main()} calls with one line.
 *
 * Returns the mission's {@link MissionStatus} (Stage 3) so a caller —
 * chiefly {@code aegis-cli}'s {@code CliMain} — can translate it into a
 * process exit code for CI/CD. Existing callers that ignore the return
 * value (every sample's {@code Main}, {@code aegis-launcher}'s
 * {@code MissionRunner}) keep compiling unchanged.
 */
public final class Launcher {

    private Launcher() {
    }

    public static MissionStatus run(AegisApplication application) {

        System.out.println("Application: " + application.name());
        System.out.println();

        AegisConfig config = application.config();
        return run(application.mission(), config.browser(), config.report().directory());
    }

    public static MissionStatus run(Mission mission) {
        return run(mission, BrowserConfig.defaults());
    }

    public static MissionStatus run(Mission mission, BrowserConfig browserConfig) {
        return run(mission, browserConfig, ReportConfig.defaults().directory());
    }

    public static MissionStatus run(Mission mission, BrowserConfig browserConfig, String reportsDirectory) {

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

        // Stage 2 "Report Plugin" — one extra file per discovered
        // ReportRenderer, named after it (e.g. aegis-report-<ts>.markdown).
        for (Map.Entry<String, String> pluginReport : report.pluginReports().entrySet()) {
            Path path = writeReport(pluginReport.getValue(), timestamp, pluginReport.getKey(), reportsDirectory);
            System.out.println(pluginReport.getKey() + " Report: " + path);
        }

        System.out.println("=================================");

        return report.status();
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
