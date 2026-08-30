package com.aegis.core.knowledge;

import com.aegis.model.observation.Observation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import static com.aegis.core.knowledge.KnowledgeTestFixtures.observation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestIntelligenceCatalogProviderTest {

    @Test
    void aNodeCoveredBeforeButNotVisitedThisRunIsReportedAsUntested() throws IOException {

        CoverageStore store = new CoverageStore(Files.createTempDirectory("aegis-ti-test"));
        store.merge("TestApp", Set.of("login", "checkout"));

        List<Observation> observations = List.of(observation("https://app/login", "Login"));

        TestIntelligenceCatalog catalog = buildCatalog("TestApp", observations, store);

        assertEquals(List.of("checkout"), catalog.untestedNodeKeys());
    }

    @Test
    void aNodeVisitedThisRunButNeverPersistedBeforeIsReportedAsNewlyDiscovered() throws IOException {

        CoverageStore store = new CoverageStore(Files.createTempDirectory("aegis-ti-test"));

        List<Observation> observations = List.of(observation("https://app/login", "Login"));

        TestIntelligenceCatalog catalog = buildCatalog("TestApp", observations, store);

        assertEquals(List.of("login"), catalog.newlyDiscoveredNodeKeys());
    }

    @Test
    void withNoPriorHistoryEveryVisitedNodeIsNewlyDiscoveredAndNothingIsUntested() throws IOException {

        CoverageStore store = new CoverageStore(Files.createTempDirectory("aegis-ti-test"));

        List<Observation> observations = List.of(
                observation("https://app/login", "Login"),
                observation("https://app/dashboard", "Dashboard")
        );

        TestIntelligenceCatalog catalog = buildCatalog("TestApp", observations, store);

        assertTrue(catalog.untestedNodeKeys().isEmpty());
        assertEquals(2, catalog.newlyDiscoveredNodeKeys().size());
    }

    @Test
    void recommendationsMentionUntestedScreensWhenSomeExist() throws IOException {

        CoverageStore store = new CoverageStore(Files.createTempDirectory("aegis-ti-test"));
        store.merge("TestApp", Set.of("login", "checkout"));

        List<Observation> observations = List.of(observation("https://app/login", "Login"));

        TestIntelligenceCatalog catalog = buildCatalog("TestApp", observations, store);

        assertTrue(catalog.recommendations().stream().anyMatch(line -> line.contains("checkout")));
    }

    @Test
    void aFullyMatchingRerunProducesAReassuringRecommendationAndNoGaps() throws IOException {

        CoverageStore store = new CoverageStore(Files.createTempDirectory("aegis-ti-test"));
        store.merge("TestApp", Set.of("login"));

        List<Observation> observations = List.of(observation("https://app/login", "Login"));

        TestIntelligenceCatalog catalog = buildCatalog("TestApp", observations, store);

        assertTrue(catalog.untestedNodeKeys().isEmpty());
        assertTrue(catalog.newlyDiscoveredNodeKeys().isEmpty());
        assertTrue(catalog.deadEndNodeKeys().isEmpty());
        assertEquals(1, catalog.recommendations().size());
        assertTrue(catalog.recommendations().get(0).contains("No coverage gaps"));
    }

    @Test
    void isNotPresentInTheStandardChainUnlessExplicitlyAdded() {

        KnowledgeBase base = KnowledgeBaseBuilder.standard()
                .build("TestApp", List.of(observation("https://app/login", "Login")), List.of(), KnowledgeConfig.empty());

        assertTrue(base.get(TestIntelligenceCatalog.class).isEmpty());
    }

    private TestIntelligenceCatalog buildCatalog(String applicationName, List<Observation> observations, CoverageStore store) {

        KnowledgeBase base = KnowledgeBaseBuilder.standard()
                .withProvider(new TestIntelligenceCatalogProvider(store))
                .build(applicationName, observations, List.of(), KnowledgeConfig.empty());

        return base.require(TestIntelligenceCatalog.class);
    }
}
