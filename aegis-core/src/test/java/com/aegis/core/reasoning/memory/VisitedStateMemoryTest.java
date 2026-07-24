package com.aegis.core.reasoning.memory;

import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisitedStateMemoryTest {

    @Test
    void unseenPageIsNotVisited() {

        VisitedStateMemory memory = new VisitedStateMemory();

        assertFalse(memory.hasVisitedPage("https://example.com/login"));
    }

    @Test
    void rememberedPageIsVisited() {

        VisitedStateMemory memory = new VisitedStateMemory();

        memory.remember(observation("https://example.com/login", input("#username", "")));

        assertTrue(memory.hasVisitedPage("https://example.com/login"));
        assertFalse(memory.hasVisitedPage("https://example.com/secure"));
    }

    @Test
    void unseenStateIsNotVisited() {

        VisitedStateMemory memory = new VisitedStateMemory();

        Observation observation = observation("https://example.com/login", input("#username", ""));

        assertFalse(memory.hasVisitedState(observation));
    }

    @Test
    void rememberedStateIsVisited() {

        VisitedStateMemory memory = new VisitedStateMemory();

        Observation observation = observation("https://example.com/login", input("#username", ""));

        memory.remember(observation);

        assertTrue(memory.hasVisitedState(observation));
    }

    @Test
    void sameUrlAndElementsButDifferentValuesIsStillTheSameState() {

        VisitedStateMemory memory = new VisitedStateMemory();

        memory.remember(observation("https://example.com/login", input("#username", "")));

        Observation afterTyping = observation("https://example.com/login", input("#username", "tomsmith"));

        assertTrue(memory.hasVisitedState(afterTyping),
                "Typing into a field shouldn't count as a new state — only new elements/pages should");
    }

    @Test
    void differentElementsOnSameUrlIsANewState() {

        VisitedStateMemory memory = new VisitedStateMemory();

        memory.remember(observation("https://example.com/login", input("#username", "")));

        Observation withExtraField = observation(
                "https://example.com/login",
                input("#username", ""),
                input("#password", "")
        );

        assertFalse(memory.hasVisitedState(withExtraField));
    }

    @Test
    void reRememberingTheSameStateDoesNotInflateTheCount() {

        VisitedStateMemory memory = new VisitedStateMemory();

        Observation observation = observation("https://example.com/login", input("#username", ""));

        memory.remember(observation);
        memory.remember(observation);

        assertEquals(1, memory.visitedStateCount());
    }

    private Observation observation(String url, ElementInfo... inputs) {

        return new Observation(
                url,
                "Title",
                List.of(inputs),
                List.of(),
                List.of(inputs),
                List.of(),
                List.of(),
                Instant.now()
        );
    }

    private ElementInfo input(String locator, String value) {

        return new ElementInfo(
                "input",
                locator.replace("#", ""),
                locator.replace("#", ""),
                "",
                "text",
                value,
                true,
                true,
                locator
        );
    }
}
