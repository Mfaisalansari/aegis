package com.aegis.core.knowledge;

import com.aegis.model.observation.Observation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.aegis.core.knowledge.KnowledgeTestFixtures.observation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeCatalogProviderTest {

    private final StateCatalogProvider stateProvider = new StateCatalogProvider();
    private final NodeCatalogProvider nodeProvider = new NodeCatalogProvider();

    @Test
    void usesPageTitleWhenPresent() {

        Node node = firstNode(List.of(observation("https://app/customers/search", "Customer Search", "x")), KnowledgeConfig.empty());

        assertEquals("Customer Search", node.displayName());
        assertEquals(NameSource.AUTO_TITLE, node.nameSource());
    }

    @Test
    void fallsBackToUrlWhenTitleIsBlank() {

        Node node = firstNode(List.of(observation("https://app/customers/search", "", "x")), KnowledgeConfig.empty());

        assertEquals("Customers Search", node.displayName());
        assertEquals(NameSource.AUTO_URL, node.nameSource());
    }

    @Test
    void keyAndTechnicalNameDefaultToAUrlSlugWhenUnconfigured() {

        Node node = firstNode(List.of(observation("https://app/Customers/Search", "Customer Search", "x")), KnowledgeConfig.empty());

        assertEquals("customers-search", node.key());
        assertEquals("customers-search", node.technicalName());
    }

    @Test
    void configuredDisplayNameWinsOverAutoNaming() {

        KnowledgeConfig config = new KnowledgeConfig(1, List.of(
                new KnowledgeConfig.NodeConfig("*/customers/search*", "cust-search", "Find a Customer", null, List.of("Search"), Map.of())
        ), List.of(), List.of(), null);

        Node node = firstNode(List.of(observation("https://app/customers/search", "Customer Search", "x")), config);

        assertEquals("Find a Customer", node.displayName());
        assertEquals(NameSource.CONFIGURED, node.nameSource());
        assertEquals("cust-search", node.key());
        // technicalName wasn't set in the config entry — falls back to the auto slug independently.
        assertEquals("customers-search", node.technicalName());
        assertEquals(List.of("Search"), node.aliases());
    }

    @Test
    void firstMatchingPatternWinsInDeclarationOrder() {

        KnowledgeConfig config = new KnowledgeConfig(1, List.of(
                new KnowledgeConfig.NodeConfig("*/customers/*", "generic", "Generic Customers Page", null, List.of(), Map.of()),
                new KnowledgeConfig.NodeConfig("*/customers/search*", "specific", "Should Not Win", null, List.of(), Map.of())
        ), List.of(), List.of(), null);

        Node node = firstNode(List.of(observation("https://app/customers/search", "Customer Search", "x")), config);

        assertEquals("generic", node.key());
        assertEquals("Generic Customers Page", node.displayName());
    }

    @Test
    void nonMatchingPatternIsIgnored() {

        KnowledgeConfig config = new KnowledgeConfig(1, List.of(
                new KnowledgeConfig.NodeConfig("*/orders/*", "orders", "Orders", null, List.of(), Map.of())
        ), List.of(), List.of(), null);

        Node node = firstNode(List.of(observation("https://app/customers/search", "Customer Search", "x")), config);

        assertTrue(node.key().contains("customers"));
        assertEquals(NameSource.AUTO_TITLE, node.nameSource());
    }

    private Node firstNode(List<Observation> observations, KnowledgeConfig config) {

        KnowledgeBuildContext stateContext = new KnowledgeBuildContext("Test", observations, List.of(), config, new KnowledgeBase(Map.of()), null);
        StateCatalog stateCatalog = stateProvider.provide(stateContext);

        KnowledgeBase partial = new KnowledgeBase(Map.of(StateCatalog.class, stateCatalog));
        KnowledgeBuildContext nodeContext = new KnowledgeBuildContext("Test", observations, List.of(), config, partial, null);

        return nodeProvider.provide(nodeContext).nodes().get(0);
    }
}
