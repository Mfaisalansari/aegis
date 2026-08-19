package com.aegis.api;

import com.aegis.core.AegisReport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes every format in an {@link AegisReport} to disk as
 * {@code aegis-report-<timestamp>.<ext>} files — extracted out of
 * {@link Launcher} so a second caller ({@code aegis-web}) can get the
 * same on-disk persistence without going through {@code Launcher}'s
 * console-output side effects or its {@code MissionStatus}-only return
 * type.
 */
public final class ReportWriter {

    private ReportWriter() {
    }

    public static WrittenReportPaths writeAll(AegisReport report, String reportsDirectory) {

        long timestamp = Instant.now().toEpochMilli();

        Path textReportPath = write(report.textReport(), timestamp, "txt", reportsDirectory);
        Path htmlReportPath = write(report.htmlReport(), timestamp, "html", reportsDirectory);
        Path jsonReportPath = write(report.jsonReport(), timestamp, "json", reportsDirectory);

        Map<String, Path> pluginReportPaths = new LinkedHashMap<>();
        for (Map.Entry<String, String> pluginReport : report.pluginReports().entrySet()) {
            pluginReportPaths.put(pluginReport.getKey(), write(pluginReport.getValue(), timestamp, pluginReport.getKey(), reportsDirectory));
        }

        return new WrittenReportPaths(textReportPath, htmlReportPath, jsonReportPath, Map.copyOf(pluginReportPaths));
    }

    private static Path write(String content, long timestamp, String extension, String reportsDirectory) {

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

    public record WrittenReportPaths(Path text, Path html, Path json, Map<String, Path> pluginReports) {

        public WrittenReportPaths {
            pluginReports = pluginReports == null ? Map.of() : Map.copyOf(pluginReports);
        }
    }
}
