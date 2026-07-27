package com.aegis.core.knowledge;

import com.aegis.model.observation.Observation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static com.aegis.core.knowledge.KnowledgeTestFixtures.observation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StateCatalogProviderTest {

    private final StateCatalogProvider provider = new StateCatalogProvider();

    @Test
    void assignsIdsByFirstDiscoveryOrder() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b"),
                observation("https://app/login", "Login", "a") // revisit — same state, no new id
        );

        StateCatalog catalog = provider.provide(context(observations));

        assertEquals(2, catalog.states().size());
        assertEquals("S1", catalog.states().get(0).id());
        assertEquals("https://app/login", catalog.states().get(0).url());
        assertEquals("S2", catalog.states().get(1).id());
        assertEquals("https://app/dashboard", catalog.states().get(1).url());
    }

    @Test
    void distinctComponentCountDeduplicatesSharedElementsAcrossStates() {

        List<Observation> observations = List.of(
                observation("https://app/a", "A", "nav-home", "nav-about", "button-a"),
                observation("https://app/b", "B", "nav-home", "nav-about", "button-b")
        );

        StateCatalog catalog = provider.provide(context(observations));

        // nav-home/nav-about appear on both pages — counted once each, not twice.
        assertEquals(4, catalog.distinctComponentCount());
    }

    @Test
    void emptyObservationsProduceAnEmptyCatalog() {

        StateCatalog catalog = provider.provide(context(List.of()));

        assertEquals(0, catalog.discoveredScreenCount());
        assertEquals(0, catalog.distinctComponentCount());
    }

    @Test
    void byIdReturnsEmptyForAnUnknownId() {

        StateCatalog catalog = provider.provide(context(List.of(observation("https://app/a", "A", "x"))));

        assertThrows(NoSuchElementException.class, () -> catalog.byId("S99").get());
    }

    private KnowledgeBuildContext context(List<Observation> observations) {
        return new KnowledgeBuildContext("Test App", observations, List.of(), KnowledgeConfig.empty(), new KnowledgeBase(Map.of()), null);
    }
}
