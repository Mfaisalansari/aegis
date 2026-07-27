package com.aegis.core.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeBaseTest {

    @Test
    void getReturnsEmptyWhenACatalogIsNotPresent() {

        KnowledgeBase base = new KnowledgeBase(Map.of());

        assertTrue(base.get(StateCatalog.class).isEmpty());
    }

    @Test
    void getReturnsThePresentCatalogByExactType() {

        StateCatalog stateCatalog = new StateCatalog(List.of(), 0);
        KnowledgeBase base = new KnowledgeBase(Map.of(StateCatalog.class, stateCatalog));

        assertEquals(stateCatalog, base.get(StateCatalog.class).orElseThrow());
    }

    @Test
    void requireThrowsClearlyWhenACatalogIsMissing() {

        KnowledgeBase base = new KnowledgeBase(Map.of());

        assertThrows(IllegalStateException.class, () -> base.require(StateCatalog.class));
    }

    @Test
    void allReturnsEveryRegisteredCatalog() {

        StateCatalog stateCatalog = new StateCatalog(List.of(), 0);
        NodeCatalog nodeCatalog = new NodeCatalog(List.of());
        KnowledgeBase base = new KnowledgeBase(Map.of(StateCatalog.class, stateCatalog, NodeCatalog.class, nodeCatalog));

        assertEquals(2, base.all().size());
    }
}
