package com.aegis.core.world;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldModelTest {

    @Test
    void unknownTransitionHasNoDestination() {

        WorldModel worldModel = new WorldModel();

        Observation login = observation("https://example.com/login", link("a:nth-of-type(1)"));

        assertTrue(worldModel.knownDestinationOf(login, click("a:nth-of-type(1)")).isEmpty());
    }

    @Test
    void recordedTransitionIsRecalled() {

        WorldModel worldModel = new WorldModel();

        Observation login = observation("https://example.com/login", link("a:nth-of-type(1)"));
        Observation secure = observation("https://example.com/secure", link("a:nth-of-type(1)"));
        Action clickLink = click("a:nth-of-type(1)");

        worldModel.recordTransition(login, clickLink, secure);

        Optional<String> destination = worldModel.knownDestinationOf(login, clickLink);

        assertTrue(destination.isPresent());
        assertEquals(destination.get(), com.aegis.core.reasoning.memory.StateSignature.of(secure));
    }

    @Test
    void differentActionFromSameStateHasNoKnownDestination() {

        WorldModel worldModel = new WorldModel();

        Observation login = observation("https://example.com/login", link("a:nth-of-type(1)"));
        Observation secure = observation("https://example.com/secure", link("a:nth-of-type(1)"));

        worldModel.recordTransition(login, click("a:nth-of-type(1)"), secure);

        assertTrue(worldModel.knownDestinationOf(login, click("a:nth-of-type(2)")).isEmpty());
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
