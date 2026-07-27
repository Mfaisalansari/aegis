package com.aegis.core.knowledge;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Shared test-fixture builders for the knowledge package's tests. */
final class KnowledgeTestFixtures {

    private KnowledgeTestFixtures() {
    }

    static Observation observation(String url, String pageTitle, String... elementLocators) {

        List<ElementInfo> elements = List.of(elementLocators).stream()
                .map(locator -> new ElementInfo("button", null, null, null, "button", null, true, true, locator))
                .toList();

        return new Observation(url, pageTitle, elements, elements, List.of(), List.of(), List.of(), Instant.now());
    }

    static Action click(String target) {
        return new Action(UUID.randomUUID(), ActionType.CLICK, target, null, "test", 0.5, "test", Duration.ofSeconds(5), Instant.now(), "button");
    }
}
