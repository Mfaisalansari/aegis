package com.aegis.core.report;

import com.aegis.core.bug.BugCluster;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.finding.Finding;
import com.aegis.model.finding.FindingSeverity;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionStatus;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionReportDataTest {

    @Test
    void coverageIsZeroWhenNothingWasEverObserved() {

        MissionContext context = new MissionContext(mission());

        MissionReportData data = MissionReportData.from(context, MissionStatus.FAILED);

        assertEquals(0, data.coverage().elementsDiscovered());
        assertEquals(0, data.coverage().elementsInteracted());
        assertEquals(0.0, data.coverage().coveragePercent());
    }

    @Test
    void coverageCountsDistinctInteractedElementsAgainstDistinctDiscoveredOnes() {

        MissionContext context = new MissionContext(mission());

        // Two elements discovered ("#a", "#b"); only "#a" gets interacted
        // with, twice (should count once — coverage is about distinct
        // elements, not action count).
        context.getExecutionState().setCurrentObservation(
                observation("https://example.com", input("#a"), input("#b")));

        context.getExecutionState().addAction(type("#a"));
        context.getExecutionState().addAction(type("#a"));

        MissionReportData data = MissionReportData.from(context, MissionStatus.SUCCESS);

        assertEquals(2, data.coverage().elementsDiscovered());
        assertEquals(1, data.coverage().elementsInteracted());
        assertEquals(50.0, data.coverage().coveragePercent(), 0.0001);
    }

    @Test
    void pageLevelActionsWithBlankTargetsDoNotAffectCoverage() {

        MissionContext context = new MissionContext(mission());

        context.getExecutionState().setCurrentObservation(
                observation("https://example.com", input("#a")));

        // REFRESH/BACK-style action: blank target, not a real element.
        context.getExecutionState().addAction(new Action(
                UUID.randomUUID(), ActionType.REFRESH, "", "", "test",
                1.0, "test", Duration.ofSeconds(5), Instant.now(), ""
        ));

        MissionReportData data = MissionReportData.from(context, MissionStatus.SUCCESS);

        assertEquals(1, data.coverage().elementsDiscovered());
        assertEquals(0, data.coverage().elementsInteracted());
    }

    @Test
    void fullCoverageIsOneHundredPercent() {

        MissionContext context = new MissionContext(mission());

        context.getExecutionState().setCurrentObservation(observation("https://example.com", input("#a")));
        context.getExecutionState().addAction(type("#a"));

        MissionReportData data = MissionReportData.from(context, MissionStatus.SUCCESS);

        assertEquals(100.0, data.coverage().coveragePercent(), 0.0001);
    }

    @Test
    void pageCoverageIsBrokenDownPerPageRatherThanBlendedMissionWide() {

        MissionContext context = new MissionContext(mission());

        // Page A: 2 elements discovered, 1 interacted (50%).
        context.getExecutionState().setCurrentObservation(
                observation("https://example.com/a", input("#a1"), input("#a2")));
        context.getExecutionState().addAction(type("#a1"));

        // Page B: 1 element discovered, 1 interacted (100%).
        context.getExecutionState().setCurrentObservation(
                observation("https://example.com/b", input("#b1")));
        context.getExecutionState().addAction(type("#b1"));

        MissionReportData data = MissionReportData.from(context, MissionStatus.SUCCESS);

        assertEquals(2, data.pageCoverage().size());

        PageCoverage pageA = data.pageCoverage().stream()
                .filter(p -> p.url().equals("https://example.com/a")).findFirst().orElseThrow();
        PageCoverage pageB = data.pageCoverage().stream()
                .filter(p -> p.url().equals("https://example.com/b")).findFirst().orElseThrow();

        assertEquals(2, pageA.elementsDiscovered());
        assertEquals(1, pageA.elementsInteracted());
        assertEquals(50.0, pageA.coveragePercent(), 0.0001);

        assertEquals(1, pageB.elementsDiscovered());
        assertEquals(1, pageB.elementsInteracted());
        assertEquals(100.0, pageB.coveragePercent(), 0.0001);
    }

    @Test
    void stateVisitCountsTrackRevisitsNotJustDistinctStates() {

        MissionContext context = new MissionContext(mission());

        Observation stateA = observation("https://example.com/a", input("#a"));
        Observation stateB = observation("https://example.com/b", input("#b"));

        context.getExecutionState().setCurrentObservation(stateA);
        context.getExecutionState().setCurrentObservation(stateB);
        context.getExecutionState().setCurrentObservation(stateA); // revisit
        context.getExecutionState().setCurrentObservation(stateA); // revisit again

        MissionReportData data = MissionReportData.from(context, MissionStatus.SUCCESS);

        String signatureA = com.aegis.core.reasoning.memory.StateSignature.of(stateA);
        String signatureB = com.aegis.core.reasoning.memory.StateSignature.of(stateB);

        // 4 observations total but only 2 distinct states -> states.size() stays 2
        assertEquals(2, data.states().size());
        assertEquals(3L, data.stateVisitCounts().get(signatureA));
        assertEquals(1L, data.stateVisitCounts().get(signatureB));
    }

    @Test
    void findingsAreClusteredIntoBugClusters() {

        MissionContext context = new MissionContext(mission());

        context.getExecutionState().addFinding(
                new Finding(FindingSeverity.MEDIUM, "REQUEST_FAILED: /api/item/4 failed", "https://example.com", Instant.now()));
        context.getExecutionState().addFinding(
                new Finding(FindingSeverity.MEDIUM, "REQUEST_FAILED: /api/item/7 failed", "https://example.com", Instant.now()));

        MissionReportData data = MissionReportData.from(context, MissionStatus.FAILED);

        assertEquals(1, data.bugClusters().size());
        assertEquals(2, data.bugClusters().get(0).occurrenceCount());
    }

    @Test
    void defaultFromOverloadUsesTheRuleBasedExplanation() {

        MissionContext context = new MissionContext(mission());

        context.getExecutionState().addFinding(
                new Finding(FindingSeverity.CRITICAL, "CRASH: tab crashed", "https://example.com", Instant.now()));

        MissionReportData data = MissionReportData.from(context, MissionStatus.FAILED);

        BugCluster cluster = data.bugClusters().get(0);

        assertEquals("The browser tab crashed outright — a real, serious defect.", data.explanationFor(cluster));
    }

    @Test
    void threeArgFromOverloadUsesTheSuppliedExplainer() {

        MissionContext context = new MissionContext(mission());

        context.getExecutionState().addFinding(
                new Finding(FindingSeverity.CRITICAL, "CRASH: tab crashed", "https://example.com", Instant.now()));

        MissionReportData data = MissionReportData.from(
                context, MissionStatus.FAILED, (cluster, fallback) -> "custom explanation");

        BugCluster cluster = data.bugClusters().get(0);

        assertEquals("custom explanation", data.explanationFor(cluster));
    }

    @Test
    void defaultRecommendationPointsAtTheHighestSeverityClusterWhenThereAreFindings() {

        MissionContext context = new MissionContext(mission());

        context.getExecutionState().addFinding(
                new Finding(FindingSeverity.LOW, "CONSOLE_ERROR: minor thing", "https://example.com", Instant.now()));
        context.getExecutionState().addFinding(
                new Finding(FindingSeverity.CRITICAL, "CRASH: tab crashed", "https://example.com", Instant.now()));

        MissionReportData data = MissionReportData.from(context, MissionStatus.FAILED);

        assertTrue(data.recommendation().contains("CRITICAL"));
        assertTrue(data.recommendation().contains("CRASH: tab crashed"));
    }

    @Test
    void defaultRecommendationSaysNothingToPrioritizeWhenThereAreNoFindings() {

        MissionContext context = new MissionContext(mission());

        MissionReportData data = MissionReportData.from(context, MissionStatus.SUCCESS);

        assertEquals("No issues found this run — nothing to prioritize.", data.recommendation());
    }

    @Test
    void fourArgFromOverloadUsesTheSuppliedRecommendationEngine() {

        MissionContext context = new MissionContext(mission());

        context.getExecutionState().addFinding(
                new Finding(FindingSeverity.CRITICAL, "CRASH: tab crashed", "https://example.com", Instant.now()));

        MissionReportData data = MissionReportData.from(
                context, MissionStatus.FAILED,
                (cluster, fallback) -> fallback,
                (clusters, fallback) -> "custom recommendation");

        assertEquals("custom recommendation", data.recommendation());
    }

    @Test
    void defaultFromOverloadIncludesARuleBasedMissionPlan() {

        MissionContext context = new MissionContext(mission());

        MissionReportData data = MissionReportData.from(context, MissionStatus.SUCCESS);

        assertFalse(data.plan().steps().isEmpty());
    }

    @Test
    void fiveArgFromOverloadUsesTheSuppliedPlan() {

        MissionContext context = new MissionContext(mission());

        com.aegis.core.mission.MissionPlan customPlan =
                new com.aegis.core.mission.MissionPlan(java.util.List.of("custom step one", "custom step two"));

        MissionReportData data = MissionReportData.from(
                context, MissionStatus.SUCCESS,
                (cluster, fallback) -> fallback,
                (clusters, fallback) -> fallback,
                customPlan);

        assertEquals(customPlan.steps(), data.plan().steps());
    }

    @Test
    void defaultFromOverloadProducesARuleBasedPlainLanguageSummary() {

        MissionContext context = new MissionContext(mission());

        MissionReportData data = MissionReportData.from(context, MissionStatus.SUCCESS);

        assertTrue(data.plainLanguageSummary().contains("Test"));
        assertTrue(data.plainLanguageSummary().contains("successfully"));
        assertTrue(data.plainLanguageSummary().contains("No problems were found"));
        assertTrue(data.plainLanguageSummary().contains("What to do next:"));
    }

    @Test
    void plainLanguageSummaryMentionsSeriousAndMinorIssueCountsInPlainWords() {

        MissionContext context = new MissionContext(mission());

        context.getExecutionState().addFinding(
                new Finding(FindingSeverity.CRITICAL, "CRASH: tab crashed", "https://example.com", Instant.now()));
        context.getExecutionState().addFinding(
                new Finding(FindingSeverity.LOW, "CONSOLE_ERROR: minor thing", "https://example.com", Instant.now()));

        MissionReportData data = MissionReportData.from(context, MissionStatus.FAILED);

        assertTrue(data.plainLanguageSummary().contains("1 serious issue"));
        assertTrue(data.plainLanguageSummary().contains("1 minor issue"));
    }

    @Test
    void plainLanguageSummaryMentionsNavigationExperienceIssuesFromTheUxQualityCatalog() {

        MissionContext context = new MissionContext(mission());

        // input(...) builds an ElementInfo with blank text/name/id — a
        // real, structural MISSING_ACCESSIBLE_NAME finding in the UX
        // Quality catalog, not a fabricated one for this test.
        context.getExecutionState().setCurrentObservation(observation("https://example.com", input("#a")));

        MissionReportData data = MissionReportData.from(context, MissionStatus.SUCCESS);

        assertTrue(data.plainLanguageSummary().contains("navigation/experience issue"));
    }

    @Test
    void tenArgFromOverloadUsesTheSuppliedSummarizer() {

        MissionContext context = new MissionContext(mission());

        MissionReportData data = MissionReportData.from(
                context, MissionStatus.SUCCESS,
                (cluster, fallback) -> fallback,
                (clusters, fallback) -> fallback,
                new com.aegis.core.mission.MissionPlan(List.of("step")),
                List.of(), List.of(), com.aegis.core.knowledge.KnowledgeConfig.empty(),
                com.aegis.core.knowledge.SignalLog.empty(),
                (missionName, missionGoal, status, coverage, bugClusters, findingsByCategory, recommendation, knowledgeBase, fallback) -> "custom plain-language summary");

        assertEquals("custom plain-language summary", data.plainLanguageSummary());
    }

    @Test
    void nineArgFromOverloadDefaultsToTheRuleBasedSummarizer() {

        MissionContext context = new MissionContext(mission());

        MissionReportData data = MissionReportData.from(
                context, MissionStatus.SUCCESS,
                (cluster, fallback) -> fallback,
                (clusters, fallback) -> fallback,
                new com.aegis.core.mission.MissionPlan(List.of("step")),
                List.of(), List.of(), com.aegis.core.knowledge.KnowledgeConfig.empty(),
                com.aegis.core.knowledge.SignalLog.empty());

        assertTrue(data.plainLanguageSummary().contains("successfully"));
    }

    private Mission mission() {
        return new Mission(UUID.randomUUID(), "Test", "Test", Map.of());
    }

    private Observation observation(String url, ElementInfo... elements) {
        return new Observation(url, "Title", List.of(elements), List.of(), List.of(elements), List.of(), List.of(), Instant.now());
    }

    private ElementInfo input(String locator) {
        return new ElementInfo("input", "", "", "", "text", "", true, true, locator);
    }

    private Action type(String target) {
        return new Action(
                UUID.randomUUID(), ActionType.TYPE, target, "value", "test",
                1.0, "test", Duration.ofSeconds(5), Instant.now(), "input"
        );
    }
}
