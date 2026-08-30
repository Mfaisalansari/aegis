package com.aegis.core.knowledge;

import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.aegis.core.knowledge.KnowledgeTestFixtures.observation;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeBaseTextRendererTest {

    private final KnowledgeBaseTextRenderer renderer = new KnowledgeBaseTextRenderer();

    @Test
    void rendersTheWorldModelSummaryWithNodesFlowsAndJourneys() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b"),
                observation("https://app/payment", "Payment", "c")
        );

        KnowledgeConfig config = new KnowledgeConfig(1,
                List.of(),
                List.of(new KnowledgeConfig.FlowConfig("core", "Core Flow", List.of("login", "dashboard"), "desc", Map.of())),
                List.of(new KnowledgeConfig.JourneyDefinitionConfig("onboarding", "Onboarding", List.of("login", "payment"), "desc", Map.of(), null)),
                null
        );

        KnowledgeBase base = KnowledgeBaseBuilder.standard().build("Insurance Portal", observations, List.of(), config);

        String rendered = renderer.render("Insurance Portal", base);

        assertTrue(rendered.contains("World Model Summary"));
        assertTrue(rendered.contains("Application: Insurance Portal"));
        assertTrue(rendered.contains("Discovered Screens: 3"));
        assertTrue(rendered.contains("Business Flows: 1"));
        assertTrue(rendered.contains("S1  → Login"));
        assertTrue(rendered.contains("Core Flow: S1 → S2"));
        assertTrue(rendered.contains("Onboarding: S1 → S3"));
        assertTrue(rendered.contains("[followed this run]"));
    }

    @Test
    void reportsUndeclaredCatalogsAsNoneRatherThanOmittingTheSection() {

        KnowledgeBase base = KnowledgeBaseBuilder.standard()
                .build("Empty App", List.of(observation("https://app/login", "Login", "a")), List.of(), KnowledgeConfig.empty());

        String rendered = renderer.render("Empty App", base);

        assertTrue(rendered.contains("Flows"));
        assertTrue(rendered.contains("(none declared)"));
        assertTrue(rendered.contains("UX Quality"));
        assertTrue(rendered.contains("Page Inspection"));
        assertTrue(rendered.contains("(no findings)"));
    }

    @Test
    void reportsNoUxFindingsWhenTheElementHasARealAccessibleName() {

        List<ElementInfo> elements = List.of(new ElementInfo("button", null, null, "Log In", "button", null, true, true, "a"));
        List<Observation> observations = List.of(
                new Observation("https://app/login", "Login", elements, elements, List.of(), List.of(), List.of(), Instant.now()));

        KnowledgeBase base = KnowledgeBaseBuilder.standard().build("Empty App", observations, List.of(), KnowledgeConfig.empty());

        String rendered = renderer.render("Empty App", base);

        assertTrue(rendered.contains("UX Quality"));
        assertTrue(rendered.contains("(no findings)"));
    }

    @Test
    void reportsARealUxFindingWhenTheRunBacktracks() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b"),
                observation("https://app/login", "Login", "a")
        );

        KnowledgeBase base = KnowledgeBaseBuilder.standard().build("App", observations, List.of(), KnowledgeConfig.empty());

        String rendered = renderer.render("App", base);

        assertTrue(rendered.contains("UX Quality"));
        assertTrue(rendered.contains("BACKTRACKING"));
        assertTrue(rendered.contains("Navigation Graph"));
        assertTrue(rendered.contains("login → dashboard → login"));
        assertTrue(rendered.contains("Experience Score"));
        assertTrue(rendered.contains("/ 100"));
    }

    @Test
    void omitsTheTestIntelligenceSectionWhenItWasNeverAddedToTheChain() {

        KnowledgeBase base = KnowledgeBaseBuilder.standard()
                .build("App", List.of(observation("https://app/login", "Login", "a")), List.of(), KnowledgeConfig.empty());

        String rendered = renderer.render("App", base);

        assertTrue(!rendered.contains("Test Intelligence"));
    }

    @Test
    void rendersTestIntelligenceRecommendationsWhenTheProviderWasExplicitlyAdded(@TempDir Path tempDir) {

        CoverageStore store = new CoverageStore(tempDir);
        store.merge("App", java.util.Set.of("checkout"));

        KnowledgeBase base = KnowledgeBaseBuilder.standard()
                .withProvider(new TestIntelligenceCatalogProvider(store))
                .build("App", List.of(observation("https://app/login", "Login", "a")), List.of(), KnowledgeConfig.empty());

        String rendered = renderer.render("App", base);

        assertTrue(rendered.contains("Test Intelligence"));
        assertTrue(rendered.contains("checkout"));
    }
}
