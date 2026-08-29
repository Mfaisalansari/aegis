package com.aegis.core.stream;

import com.aegis.core.observer.Observer;
import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingObserverTest {

    @Test
    void emitsAnObserveEventAfterDelegatingToTheRealObserver() {

        List<MissionStreamEvent> recorded = new ArrayList<>();
        Observation observation = observation("https://example.com/login", "Login", 3);

        Observer fake = context -> context.getExecutionState().setCurrentObservation(observation);
        StreamingObserver observer = new StreamingObserver(fake, recorded::add);

        MissionContext context = new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", Map.of()));
        observer.observe(context);

        assertEquals(1, recorded.size());
        MissionStreamEvent event = recorded.get(0);
        assertEquals(MissionStreamEvent.Kind.OBSERVE, event.kind());
        assertEquals("Observed https://example.com/login", event.headline());
        assertTrue(event.detail().contains("3 interactive elements"));
        assertTrue(event.detail().contains("Login"));
    }

    @Test
    void delegatesToTheRealObserverEvenWithNoListener() {

        List<String> calls = new ArrayList<>();
        Observer fake = context -> calls.add("observed");
        StreamingObserver observer = new StreamingObserver(fake, event -> { });

        observer.observe(new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", Map.of())));

        assertEquals(List.of("observed"), calls);
    }

    private Observation observation(String url, String title, int elementCount) {

        List<ElementInfo> elements = new ArrayList<>();
        for (int i = 0; i < elementCount; i++) {
            elements.add(new ElementInfo("button", "el-" + i, null, null, "button", null, true, true, "el-" + i));
        }

        return new Observation(url, title, elements, elements, List.of(), List.of(), List.of(), Instant.now());
    }
}
