package com.aegis.reporting;

import com.aegis.core.knowledge.KnowledgeBase;
import com.aegis.core.knowledge.KnowledgeBaseBuilder;
import com.aegis.core.knowledge.KnowledgeBaseTextRenderer;
import com.aegis.core.knowledge.KnowledgeConfig;
import com.aegis.core.report.ExplainabilityReportGenerator;
import com.aegis.core.report.HtmlExplainabilityReportGenerator;
import com.aegis.core.report.JsonReportGenerator;
import com.aegis.core.report.MissionReportData;
import com.aegis.observation.ObservationResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * AEGIS Platform — Shared Reporting. Turns an {@link ObservationResult}
 * (whichever Observation Source produced it — {@code aegis-selenium},
 * {@code aegis-playwright}, or hand-rolled {@code ObservationSession} use)
 * into a report, via the exact same frozen {@code aegis-core} report
 * generators a live AEGIS mission's own report already goes through.
 * Pure delegation — no report-building logic of its own, so output is
 * always identical to calling the underlying generator directly.
 */
public final class AegisReporting {

    private AegisReporting() {
    }

    public static String htmlReport(ObservationResult result) {
        return new HtmlExplainabilityReportGenerator().generate(result.context(), result.status());
    }

    /**
     * The same technical report as {@link #htmlReport}, redesigned —
     * instrument-panel visual language and a content-aware world-model
     * graph layout in place of the original's fixed-spacing one — built as
     * a separate, additive generator ({@link RedesignedMissionReportGenerator})
     * rather than editing the frozen {@link HtmlExplainabilityReportGenerator}.
     * Same data, same tabs, same sections; visuals only.
     */
    public static String redesignedHtmlReport(ObservationResult result) {
        return new RedesignedMissionReportGenerator().generate(
                MissionReportData.from(result.context(), result.status()));
    }

    public static String jsonReport(ObservationResult result) {
        return new JsonReportGenerator().generate(result.context(), result.status());
    }

    public static String textReport(ObservationResult result) {
        return new ExplainabilityReportGenerator().generate(result.context(), result.status());
    }

    /** The "World Model Summary" — Node Dictionary, Flows, Journeys, UX Quality, Page Inspection, Navigation Graph, Experience Score. */
    public static String knowledgeSummary(String applicationName, ObservationResult result) {

        KnowledgeBase knowledgeBase = KnowledgeBaseBuilder.standard().build(
                applicationName,
                result.context().getExecutionState().getObservations(),
                result.context().getExecutionState().getActions(),
                KnowledgeConfig.empty());

        return new KnowledgeBaseTextRenderer().render(applicationName, knowledgeBase);
    }

    public static Path writeHtmlReport(ObservationResult result, Path outputFile) {

        try {
            if (outputFile.getParent() != null) {
                Files.createDirectories(outputFile.getParent());
            }
            Files.writeString(outputFile, htmlReport(result));
            return outputFile;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write AEGIS HTML report to " + outputFile, e);
        }
    }

    public static Path writeHtmlReport(ObservationResult result, Path directory, String fileNameWithoutExtension) {
        return writeHtmlReport(result, directory.resolve(fileNameWithoutExtension + ".html"));
    }

    public static Path writeRedesignedHtmlReport(ObservationResult result, Path outputFile) {

        try {
            if (outputFile.getParent() != null) {
                Files.createDirectories(outputFile.getParent());
            }
            Files.writeString(outputFile, redesignedHtmlReport(result));
            return outputFile;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write AEGIS redesigned HTML report to " + outputFile, e);
        }
    }

    public static Path writeRedesignedHtmlReport(ObservationResult result, Path directory, String fileNameWithoutExtension) {
        return writeRedesignedHtmlReport(result, directory.resolve(fileNameWithoutExtension + ".html"));
    }
}
