package com.aegis.reporting;

import com.aegis.core.report.ExplainabilityReportGenerator;
import com.aegis.core.report.HtmlExplainabilityReportGenerator;
import com.aegis.core.report.JsonReportGenerator;
import com.aegis.model.mission.MissionStatus;
import com.aegis.observation.ObservationResult;
import com.aegis.observation.ObservationSession;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AegisReportingTest {

    @Test
    void htmlReportIsByteIdenticalToCallingTheFrozenGeneratorDirectly() {

        ObservationResult result = sampleResult();

        String viaAegisReporting = AegisReporting.htmlReport(result);
        String viaDirectCall = new HtmlExplainabilityReportGenerator().generate(result.context(), result.status());

        assertEquals(viaDirectCall, viaAegisReporting);
    }

    @Test
    void jsonReportIsByteIdenticalToCallingTheFrozenGeneratorDirectly() {

        ObservationResult result = sampleResult();

        String viaAegisReporting = AegisReporting.jsonReport(result);
        String viaDirectCall = new JsonReportGenerator().generate(result.context(), result.status());

        assertEquals(viaDirectCall, viaAegisReporting);
    }

    @Test
    void textReportIsByteIdenticalToCallingTheFrozenGeneratorDirectly() {

        ObservationResult result = sampleResult();

        String viaAegisReporting = AegisReporting.textReport(result);
        String viaDirectCall = new ExplainabilityReportGenerator().generate(result.context(), result.status());

        assertEquals(viaDirectCall, viaAegisReporting);
    }

    @Test
    void knowledgeSummaryContainsTheWorldModelSummaryHeader() {

        ObservationResult result = sampleResult();

        String summary = AegisReporting.knowledgeSummary("Test App", result);

        assertTrue(summary.contains("World Model Summary"));
        assertTrue(summary.contains("Application: Test App"));
    }

    @Test
    void writeHtmlReportWritesARealFile() throws IOException {

        ObservationResult result = sampleResult();
        Path directory = Files.createTempDirectory("aegis-reporting-test");

        Path written = AegisReporting.writeHtmlReport(result, directory, "sample-report");

        assertTrue(Files.exists(written));
        assertEquals(AegisReporting.htmlReport(result), Files.readString(written));
    }

    @Test
    void writeHtmlReportCreatesMissingParentDirectories() throws IOException {

        ObservationResult result = sampleResult();
        Path directory = Files.createTempDirectory("aegis-reporting-test").resolve("nested").resolve("dirs");

        Path written = AegisReporting.writeHtmlReport(result, directory.resolve("report.html"));

        assertTrue(Files.exists(written));
    }

    @Test
    void redesignedHtmlReportProducesTheSameFiveTabsAsTheFrozenGenerator() {

        ObservationResult result = sampleResult();

        String redesigned = AegisReporting.redesignedHtmlReport(result);

        assertTrue(redesigned.contains("Sample Mission"));
        assertTrue(redesigned.contains("data-tab=\"overview\""));
        assertTrue(redesigned.contains("data-tab=\"findings\""));
        assertTrue(redesigned.contains("data-tab=\"reasoning\""));
        assertTrue(redesigned.contains("data-tab=\"timeline\""));
        assertTrue(redesigned.contains("data-tab=\"world-model\""));
    }

    @Test
    void redesignedHtmlReportDoesNotAffectTheFrozenGeneratorsOwnOutput() {

        ObservationResult result = sampleResult();

        String frozenBefore = AegisReporting.htmlReport(result);
        AegisReporting.redesignedHtmlReport(result);
        String frozenAfter = AegisReporting.htmlReport(result);

        assertEquals(frozenBefore, frozenAfter);
    }

    @Test
    void writeRedesignedHtmlReportWritesARealFile() throws IOException {

        ObservationResult result = sampleResult();
        Path directory = Files.createTempDirectory("aegis-reporting-test");

        Path written = AegisReporting.writeRedesignedHtmlReport(result, directory, "redesigned-report");

        assertTrue(Files.exists(written));
        assertEquals(AegisReporting.redesignedHtmlReport(result), Files.readString(written));
    }

    private ObservationResult sampleResult() {

        ObservationSession session = new ObservationSession("Selenium", "Sample Mission", "A sample session for AegisReportingTest");

        session.recordObservation("https://app/login", "Login", List.of(), List.of(), List.of(), List.of());
        session.recordObservation("https://app/dashboard", "Dashboard", List.of(), List.of(), List.of(), List.of());

        return session.finish(MissionStatus.SUCCESS);
    }
}
