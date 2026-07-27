package com.aegis.core.knowledge;

import com.aegis.model.observation.Observation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.aegis.core.knowledge.KnowledgeTestFixtures.observation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowCatalogProviderTest {

    @Test
    void resolvesDeclaredFlowAgainstDiscoveredNodes() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b")
        );

        KnowledgeConfig config = new KnowledgeConfig(1, List.of(), List.of(
                new KnowledgeConfig.FlowConfig("core", "Core Flow", List.of("login", "dashboard"), "desc", Map.of())
        ), List.of(), null);

        FlowCatalog catalog = buildFlowCatalog(observations, config);

        assertEquals(1, catalog.flows().size());
        Flow flow = catalog.flows().get(0);
        assertEquals("Core Flow", flow.name());
        assertTrue(flow.unmatchedNodeKeys().isEmpty());
    }

    @Test
    void aDeclaredKeyWithNoDiscoveredNodeIsReportedNotDropped() {

        List<Observation> observations = List.of(observation("https://app/login", "Login", "a"));

        KnowledgeConfig config = new KnowledgeConfig(1, List.of(), List.of(
                new KnowledgeConfig.FlowConfig("core", "Core Flow", List.of("login", "not-discovered-yet"), "desc", Map.of())
        ), List.of(), null);

        FlowCatalog catalog = buildFlowCatalog(observations, config);

        Flow flow = catalog.flows().get(0);
        assertEquals(List.of("not-discovered-yet"), flow.unmatchedNodeKeys());
        // Still reported in full, not silently dropped from nodeKeys.
        assertEquals(List.of("login", "not-discovered-yet"), flow.nodeKeys());
    }

    @Test
    void noFlowsDeclaredProducesAnEmptyCatalog() {

        FlowCatalog catalog = buildFlowCatalog(List.of(observation("https://app/login", "Login", "a")), KnowledgeConfig.empty());

        assertTrue(catalog.flows().isEmpty());
    }

    private FlowCatalog buildFlowCatalog(List<Observation> observations, KnowledgeConfig config) {

        StateCatalog stateCatalog = new StateCatalogProvider().provide(
                new KnowledgeBuildContext("Test", observations, List.of(), config, new KnowledgeBase(Map.of()), null));

        NodeCatalog nodeCatalog = new NodeCatalogProvider().provide(
                new KnowledgeBuildContext("Test", observations, List.of(), config, new KnowledgeBase(Map.of(StateCatalog.class, stateCatalog)), null));

        KnowledgeBase partial = new KnowledgeBase(Map.of(StateCatalog.class, stateCatalog, NodeCatalog.class, nodeCatalog));

        return new FlowCatalogProvider().provide(new KnowledgeBuildContext("Test", observations, List.of(), config, partial, null));
    }
}
