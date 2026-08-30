package com.aegis.core.knowledge;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.observation.Observation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.aegis.core.knowledge.KnowledgeTestFixtures.observation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationGraphCatalogProviderTest {

    @Test
    void linearNavigationWithNoRevisitsProducesEdgesButNoLoopsOrDeadEnds() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b"),
                observation("https://app/payment", "Payment", "c")
        );

        NavigationGraphCatalog catalog = buildCatalog(observations, List.of());

        assertEquals(2, catalog.edges().size());
        assertTrue(hasEdge(catalog, "login", "dashboard"));
        assertTrue(hasEdge(catalog, "dashboard", "payment"));
        assertTrue(catalog.loops().isEmpty());
        assertTrue(catalog.deadEndNodeKeys().isEmpty());
    }

    @Test
    void aRevisitedNodeProducesAWalkedLoop() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b"),
                observation("https://app/login", "Login", "a")
        );

        NavigationGraphCatalog catalog = buildCatalog(observations, List.of());

        assertEquals(1, catalog.loops().size());
        assertEquals(List.of("login", "dashboard", "login"), catalog.loops().get(0).nodeKeySequence());
    }

    @Test
    void onlyTheFirstWalkedLoopPerStartingNodeIsReported() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b"),
                observation("https://app/login", "Login", "a"),
                observation("https://app/payment", "Payment", "c"),
                observation("https://app/login", "Login", "a")
        );

        NavigationGraphCatalog catalog = buildCatalog(observations, List.of());

        assertEquals(1, catalog.loops().size());
    }

    @Test
    void aBackActionTakenFromANodeFlagsItAsADeadEnd() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b"),
                observation("https://app/help", "Help", "c"),
                observation("https://app/dashboard", "Dashboard", "b")
        );

        List<Action> actions = List.of(
                action(ActionType.CLICK, "a"),
                action(ActionType.CLICK, "b"),
                action(ActionType.BACK, "c")
        );

        NavigationGraphCatalog catalog = buildCatalog(observations, actions);

        assertEquals(List.of("help"), catalog.deadEndNodeKeys());
    }

    @Test
    void noBackActionsProduceNoDeadEnds() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b"),
                observation("https://app/help", "Help", "c")
        );

        List<Action> actions = List.of(
                action(ActionType.CLICK, "a"),
                action(ActionType.CLICK, "b")
        );

        NavigationGraphCatalog catalog = buildCatalog(observations, actions);

        assertTrue(catalog.deadEndNodeKeys().isEmpty());
    }

    @Test
    void consecutiveDuplicateObservationsOfTheSameNodeCollapseIntoOneStep() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b")
        );

        NavigationGraphCatalog catalog = buildCatalog(observations, List.of());

        assertEquals(1, catalog.edges().size());
        assertTrue(hasEdge(catalog, "login", "dashboard"));
    }

    @Test
    void repeatedTransitionsAreCountedOnTheSameEdge() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b"),
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b")
        );

        NavigationGraphCatalog catalog = buildCatalog(observations, List.of());

        NavigationGraphEdge edge = catalog.edges().stream()
                .filter(e -> e.fromNodeKey().equals("login") && e.toNodeKey().equals("dashboard"))
                .findFirst().orElseThrow();

        assertEquals(2, edge.traversalCount());
    }

    @Test
    void emptyObservationsProduceAnEmptyCatalog() {

        NavigationGraphCatalog catalog = buildCatalog(List.of(), List.of());

        assertTrue(catalog.edges().isEmpty());
        assertTrue(catalog.loops().isEmpty());
        assertTrue(catalog.deadEndNodeKeys().isEmpty());
    }

    private boolean hasEdge(NavigationGraphCatalog catalog, String from, String to) {
        return catalog.edges().stream().anyMatch(e -> e.fromNodeKey().equals(from) && e.toNodeKey().equals(to));
    }

    private Action action(ActionType type, String target) {
        return new Action(UUID.randomUUID(), type, target, null, "test", 0.5, "test", Duration.ofSeconds(5), Instant.now(), "button");
    }

    private NavigationGraphCatalog buildCatalog(List<Observation> observations, List<Action> actions) {

        StateCatalog stateCatalog = new StateCatalogProvider().provide(
                new KnowledgeBuildContext("Test", observations, actions, KnowledgeConfig.empty(), new KnowledgeBase(Map.of()), null));

        NodeCatalog nodeCatalog = new NodeCatalogProvider().provide(
                new KnowledgeBuildContext("Test", observations, actions, KnowledgeConfig.empty(),
                        new KnowledgeBase(Map.of(StateCatalog.class, stateCatalog)), null));

        KnowledgeBase partial = new KnowledgeBase(Map.of(StateCatalog.class, stateCatalog, NodeCatalog.class, nodeCatalog));

        return new NavigationGraphCatalogProvider().provide(
                new KnowledgeBuildContext("Test", observations, actions, KnowledgeConfig.empty(), partial, null));
    }
}
