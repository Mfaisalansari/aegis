package com.aegis.core.knowledge;

import com.aegis.model.finding.FindingSeverity;
import com.aegis.model.observation.Observation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.aegis.core.knowledge.KnowledgeTestFixtures.observation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperienceScoreCatalogProviderTest {

    @Test
    void aRunWithNoFindingsScoresAPerfect100() {

        // No element locators — an empty-elements observation can never trip
        // detectMissingAccessibleNames, unlike KnowledgeTestFixtures.observation's
        // usual blank-text/name/id synthetic buttons.
        List<Observation> observations = List.of(
                observation("https://app/login", "Login"),
                observation("https://app/dashboard", "Dashboard")
        );

        ExperienceScoreCatalog catalog = buildCatalog(observations);

        assertEquals(100, catalog.score());
        assertEquals(0, catalog.frictionPoints());
        assertEquals(0, catalog.totalFindings());
    }

    @Test
    void backtrackingFindingsLowerTheScore() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b"),
                observation("https://app/login", "Login", "a")
        );

        ExperienceScoreCatalog catalog = buildCatalog(observations);

        assertTrue(catalog.score() < 100);
        assertTrue(catalog.frictionPoints() > 0);
        assertTrue(catalog.totalFindings() > 0);
    }

    @Test
    void findingCountsBySeverityAreTrackedAcrossAllSeverityLevels() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b"),
                observation("https://app/login", "Login", "a")
        );

        ExperienceScoreCatalog catalog = buildCatalog(observations);

        assertEquals(4, catalog.findingCountsBySeverity().size());
        int totalCounted = catalog.findingCountsBySeverity().values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(catalog.totalFindings(), totalCounted);
    }

    @Test
    void theScoreNeverGoesBelowZero() {

        // 15 independent immediate-backtrack pairs (nodeI -> hubI -> nodeI)
        // — each produces its own escalated BACKTRACKING finding plus its
        // own walked loop, comfortably pushing frictionPoints past 100 so
        // the Math.max(0, ...) floor in ExperienceScoreCatalogProvider is
        // actually exercised, not just asserted in the abstract.
        List<Observation> observations = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            observations.add(observation("https://app/node" + i, "Node" + i, "n" + i));
            observations.add(observation("https://app/hub" + i, "Hub" + i, "h" + i));
            observations.add(observation("https://app/node" + i, "Node" + i, "n" + i));
        }

        ExperienceScoreCatalog catalog = buildCatalog(observations);

        assertTrue(catalog.frictionPoints() > 100);
        assertEquals(0, catalog.score());
    }

    private ExperienceScoreCatalog buildCatalog(List<Observation> observations) {

        KnowledgeConfig config = KnowledgeConfig.empty();

        StateCatalog stateCatalog = new StateCatalogProvider().provide(
                new KnowledgeBuildContext("Test", observations, List.of(), config, new KnowledgeBase(java.util.Map.of()), null));

        NodeCatalog nodeCatalog = new NodeCatalogProvider().provide(
                new KnowledgeBuildContext("Test", observations, List.of(), config,
                        new KnowledgeBase(java.util.Map.of(StateCatalog.class, stateCatalog)), null));

        JourneyCatalog journeyCatalog = new JourneyCatalogProvider().provide(
                new KnowledgeBuildContext("Test", observations, List.of(), config,
                        new KnowledgeBase(java.util.Map.of(StateCatalog.class, stateCatalog, NodeCatalog.class, nodeCatalog)), null));

        UxFindingCatalog uxFindingCatalog = new UxAnalysisCatalogProvider().provide(
                new KnowledgeBuildContext("Test", observations, List.of(), config, new KnowledgeBase(java.util.Map.of(
                        StateCatalog.class, stateCatalog, NodeCatalog.class, nodeCatalog, JourneyCatalog.class, journeyCatalog)), null));

        InspectionCatalog inspectionCatalog = new InspectionCatalog(List.of());

        NavigationGraphCatalog navigationGraphCatalog = new NavigationGraphCatalogProvider().provide(
                new KnowledgeBuildContext("Test", observations, List.of(), config, new KnowledgeBase(java.util.Map.of(
                        StateCatalog.class, stateCatalog, NodeCatalog.class, nodeCatalog)), null));

        KnowledgeBase partial = new KnowledgeBase(java.util.Map.of(
                StateCatalog.class, stateCatalog,
                NodeCatalog.class, nodeCatalog,
                JourneyCatalog.class, journeyCatalog,
                UxFindingCatalog.class, uxFindingCatalog,
                InspectionCatalog.class, inspectionCatalog,
                NavigationGraphCatalog.class, navigationGraphCatalog
        ));

        return new ExperienceScoreCatalogProvider().provide(
                new KnowledgeBuildContext("Test", observations, List.of(), config, partial, null));
    }
}
