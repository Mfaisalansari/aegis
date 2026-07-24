package com.aegis.launcher;

import com.aegis.core.engine.EngineFactory;
import com.aegis.core.engine.MissionEngine;
import com.aegis.core.report.ExplainabilityReportGenerator;
import com.aegis.core.report.HtmlExplainabilityReportGenerator;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Shared entry-point logic: run a mission, write both report formats,
 * print a summary. Each Main-style class just builds a Mission and hands
 * it here, instead of duplicating this wiring per target site.
 */
final class MissionRunner {

    private MissionRunner() {
    }

    static void run(Mission mission) {

        System.out.println("=================================");
        System.out.println("         AEGIS v0.1");
        System.out.println("=================================");
        System.out.println();
        System.out.println("Mission: " + mission.name());
        System.out.println("Target : " + mission.parameter("baseUrl"));
        System.out.println();

        MissionEngine engine = EngineFactory.create();

        MissionResult result = engine.execute(mission);

        long timestamp = Instant.now().toEpochMilli();

        Path textReportPath = writeReport(
                new ExplainabilityReportGenerator().generate(result.context(), result.status()),
                timestamp,
                "txt"
        );

        Path htmlReportPath = writeReport(
                new HtmlExplainabilityReportGenerator().generate(result.context(), result.status()),
                timestamp,
                "html"
        );

        System.out.println();
        System.out.println("=================================");
        System.out.println("Mission Finished");
        System.out.println("Status     : " + result.status());
        System.out.println("Report     : " + textReportPath);
        System.out.println("HTML Report: " + htmlReportPath);
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
