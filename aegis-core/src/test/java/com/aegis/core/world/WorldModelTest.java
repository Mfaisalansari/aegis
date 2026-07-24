package com.aegis.core.world;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldModelTest {

    @Test
    void unknownActionHasNoKnownDestinations() {

        WorldModel worldModel = new WorldModel();

        assertTrue(worldModel.knownDestinationsOf(ActionType.CLICK, "a:nth-of-type(1)").isEmpty());
    }

    @Test
    void recordedTransitionIsRecalled() {

        WorldModel worldModel = new WorldModel();

        Observation login = observation("https://example.com/login", link("a:nth-of-type(1)"));
        Observation secure = observation("https://example.com/secure", link("a:nth-of-type(1)"));
        Action clickLink = click("a:nth-of-type(1)");

        worldModel.recordTransition(login, clickLink, secure);

        Set<String> destinations = worldModel.knownDestinationsOf(ActionType.CLICK, "a:nth-of-type(1)");

        assertEquals(Set.of(com.aegis.core.reasoning.memory.StateSignature.of(secure)), destinations);
    }

    @Test
    void differentActionHasNoKnownDestinations() {

        WorldModel worldModel = new WorldModel();

        Observation login = observation("https://example.com/login", link("a:nth-of-type(1)"));
        Observation secure = observation("https://example.com/secure", link("a:nth-of-type(1)"));

        worldModel.recordTransition(login, click("a:nth-of-type(1)"), secure);

        assertTrue(worldModel.knownDestinationsOf(ActionType.CLICK, "a:nth-of-type(2)").isEmpty());
    }

    @Test
    void knownDestinationsAreAggregatedAcrossDifferentSourceStates() {

        WorldModel worldModel = new WorldModel();

        Observation pageA = observation("https://example.com/a", link("#nav-home"));
        Observation pageB = observation("https://example.com/b", link("#nav-home"));
        Observation home = observation("https://example.com/", link("#nav-home"));
        Observation otherHome = observation("https://example.com/home2", link("#nav-home"));

        // The same locator, tried from two different source states, recorded
        // two different destinations — this is exactly the cross-state
        // history a candidate reached via a still-untried-from state should
        // be judged against.
        worldModel.recordTransition(pageA, click("#nav-home"), home);
        worldModel.recordTransition(pageB, click("#nav-home"), otherHome);

        Set<String> destinations = worldModel.knownDestinationsOf(ActionType.CLICK, "#nav-home");

        assertEquals(2, destinations.size());
    }

    @Test
    void stateAndEdgeCountsReflectDistinctTransitions() {

        WorldModel worldModel = new WorldModel();

        Observation login = observation("https://example.com/login", link("a:nth-of-type(1)"));
        Observation secure = observation("https://example.com/secure", link("a:nth-of-type(1)"));

        worldModel.recordTransition(login, click("a:nth-of-type(1)"), secure);
        worldModel.recordTransition(login, click("a:nth-of-type(1)"), secure);

        assertEquals(1, worldModel.stateCount());
        assertEquals(1, worldModel.edgeCount());
    }

    private Observation observation(String url, ElementInfo... elements) {
        return new Observation(url, "Title", List.of(elements), List.of(), List.of(), List.of(elements), List.of(), Instant.now());
    }

    private ElementInfo link(String locator) {
        return new ElementInfo("a", "", "", "", "link", "", true, true, locator);
    }

    private Action click(String target) {
        return new Action(
                UUID.randomUUID(),
                ActionType.CLICK,
                target,
                "",
                "test",
                0.5,
                "test",
                Duration.ofSeconds(5),
                Instant.now(),
                "a"
        );
    }
}
