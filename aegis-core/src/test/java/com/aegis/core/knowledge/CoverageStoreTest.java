package com.aegis.core.knowledge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverageStoreTest {

    @Test
    void loadingAnUnknownApplicationReturnsAnEmptyRecord() throws IOException {

        CoverageStore store = new CoverageStore(Files.createTempDirectory("aegis-coverage-test"));

        CoverageRecord record = store.load("Never Seen Before");

        assertTrue(record.visitedNodeKeys().isEmpty());
    }

    @Test
    void mergingPersistsAndIsReadableBackByTheSameApplicationName() throws IOException {

        CoverageStore store = new CoverageStore(Files.createTempDirectory("aegis-coverage-test"));

        store.merge("SauceDemo", Set.of("login", "inventory"));
        CoverageRecord record = store.load("SauceDemo");

        assertEquals(Set.of("login", "inventory"), record.visitedNodeKeys());
    }

    @Test
    void repeatedMergesUnionRatherThanReplace() throws IOException {

        CoverageStore store = new CoverageStore(Files.createTempDirectory("aegis-coverage-test"));

        store.merge("SauceDemo", Set.of("login"));
        store.merge("SauceDemo", Set.of("inventory"));
        CoverageRecord record = store.load("SauceDemo");

        assertEquals(Set.of("login", "inventory"), record.visitedNodeKeys());
    }

    @Test
    void differentApplicationNamesArePersistedIndependently() throws IOException {

        CoverageStore store = new CoverageStore(Files.createTempDirectory("aegis-coverage-test"));

        store.merge("App One", Set.of("home"));
        store.merge("App Two", Set.of("dashboard"));

        assertEquals(Set.of("home"), store.load("App One").visitedNodeKeys());
        assertEquals(Set.of("dashboard"), store.load("App Two").visitedNodeKeys());
    }

    @Test
    void mergingFromAKnowledgeBaseUsesItsNodeCatalogKeys() throws IOException {

        StateCatalog stateCatalog = new StateCatalog(
                List.of(new State("S1", "sig1", "https://app/login", "Login", 3, java.util.Map.of())), 3);

        NodeCatalog nodeCatalog = new NodeCatalog(
                List.of(new Node("S1", "login", "Login", "login", List.of(), NameSource.AUTO_TITLE, java.util.Map.of())));

        KnowledgeBase kb = new KnowledgeBase(java.util.Map.of(StateCatalog.class, stateCatalog, NodeCatalog.class, nodeCatalog));

        CoverageStore store = new CoverageStore(Files.createTempDirectory("aegis-coverage-test"));
        store.merge("SauceDemo", kb);

        assertEquals(Set.of("login"), store.load("SauceDemo").visitedNodeKeys());
    }

    @Test
    void applicationNamesWithSpacesAndSpecialCharactersAreSlugifiedToAValidFileName() throws IOException {

        Path directory = Files.createTempDirectory("aegis-coverage-test");
        CoverageStore store = new CoverageStore(directory);

        store.merge("My App! (v2.0)", Set.of("home"));

        assertEquals(Set.of("home"), store.load("My App! (v2.0)").visitedNodeKeys());
    }
}
