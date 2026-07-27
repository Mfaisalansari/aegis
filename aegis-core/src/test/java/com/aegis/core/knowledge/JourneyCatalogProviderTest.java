package com.aegis.core.knowledge;

import com.aegis.model.observation.Observation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.aegis.core.knowledge.KnowledgeTestFixtures.observation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JourneyCatalogProviderTest {

    @Test
    void observedSequenceIsChronologicalDistinctVisitOrderWithRevisitsCollapsed() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b"),
                observation("https://app/login", "Login", "a"), // revisit, not a new entry
                observation("https://app/payment", "Payment", "c")
        );

        JourneyCatalog catalog = buildJourneyCatalog(observations, KnowledgeConfig.empty());

        assertEquals(1, catalog.observed().size());
        assertEquals(List.of("login", "dashboard", "payment"), catalog.observed().get(0).actualNodeKeySequence());
    }

    @Test
    void matchesADeclaredDefinitionThatIsASubsequenceOfWhatHappened() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b"),
                observation("https://app/create-policy", "Create Policy", "c"),
                observation("https://app/payment", "Payment", "d")
        );

        KnowledgeConfig config = new KnowledgeConfig(1, List.of(), List.of(), List.of(
                new KnowledgeConfig.JourneyDefinitionConfig("onboarding", "Onboarding", List.of("login", "payment"), "desc", Map.of(), null)
        ), null);

        JourneyCatalog catalog = buildJourneyCatalog(observations, config);

        assertTrue(catalog.observed().get(0).matchesAnyDefinition());
        assertEquals(List.of("onboarding"), catalog.observed().get(0).matchedDefinitionKeys());
    }

    @Test
    void doesNotMatchADefinitionWhoseOrderWasNotFollowed() {

        List<Observation> observations = List.of(
                observation("https://app/payment", "Payment", "a"),
                observation("https://app/login", "Login", "b")
        );

        KnowledgeConfig config = new KnowledgeConfig(1, List.of(), List.of(), List.of(
                new KnowledgeConfig.JourneyDefinitionConfig("onboarding", "Onboarding", List.of("login", "payment"), "desc", Map.of(), null)
        ), null);

        JourneyCatalog catalog = buildJourneyCatalog(observations, config);

        assertFalse(catalog.observed().get(0).matchesAnyDefinition());
    }

    @Test
    void unmatchedDefinitionKeysAreReported() {

        KnowledgeConfig config = new KnowledgeConfig(1, List.of(), List.of(), List.of(
                new KnowledgeConfig.JourneyDefinitionConfig("onboarding", "Onboarding", List.of("login", "never-discovered"), "desc", Map.of(), null)
        ), null);

        JourneyCatalog catalog = buildJourneyCatalog(List.of(observation("https://app/login", "Login", "a")), config);

        assertEquals(List.of("never-discovered"), catalog.definitions().get(0).unmatchedNodeKeys());
    }

    @Test
    void noObservationsProducesNoObservedJourney() {

        JourneyCatalog catalog = buildJourneyCatalog(List.of(), KnowledgeConfig.empty());

        assertTrue(catalog.observed().isEmpty());
    }

    private JourneyCatalog buildJourneyCatalog(List<Observation> observations, KnowledgeConfig config) {

        StateCatalog stateCatalog = new StateCatalogProvider().provide(
                new KnowledgeBuildContext("Test", observations, List.of(), config, new KnowledgeBase(Map.of()), null));

        NodeCatalog nodeCatalog = new NodeCatalogProvider().provide(
                new KnowledgeBuildContext("Test", observations, List.of(), config, new KnowledgeBase(Map.of(StateCatalog.class, stateCatalog)), null));

        KnowledgeBase partial = new KnowledgeBase(Map.of(StateCatalog.class, stateCatalog, NodeCatalog.class, nodeCatalog));

        return new JourneyCatalogProvider().provide(new KnowledgeBuildContext("Test", observations, List.of(), config, partial, null));
    }
}
