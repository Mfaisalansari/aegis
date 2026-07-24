package com.aegis.core.reasoning.filter;

import com.aegis.core.reasoning.memory.VisitedStateMemory;
import com.aegis.core.world.WorldModel;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnownDeadEndCandidateFilterTest {

    @Test
    void leavesCandidatesAloneWhenDestinationIsUnknown() {

        WorldModel worldModel = new WorldModel();
        VisitedStateMemory visitedStateMemory = new VisitedStateMemory();

        Observation login = observation("https://example.com/login", link("a:nth-of-type(1)"));

        MissionContext context = context(login);

        CandidateAction candidate = candidate(click("a:nth-of-type(1)"));

        List<CandidateAction> result = new KnownDeadEndCandidateFilter(worldModel, visitedStateMemory)
                .filter(context, List.of(candidate));

        assertEquals(1, result.size());
    }

    @Test
    void removesCandidateWhoseKnownDestinationIsAlreadyVisited() {

        WorldModel worldModel = new WorldModel();
        VisitedStateMemory visitedStateMemory = new VisitedStateMemory();

        Observation login = observation("https://example.com/login", link("a:nth-of-type(1)"));
        Observation secure = observation("https://example.com/secure", link("a:nth-of-type(1)"));

        Action clickLink = click("a:nth-of-type(1)");

        worldModel.recordTransition(login, clickLink, secure);
        visitedStateMemory.remember(login);
        visitedStateMemory.remember(secure);

        MissionContext context = context(login);

        List<CandidateAction> result = new KnownDeadEndCandidateFilter(worldModel, visitedStateMemory)
                .filter(context, List.of(candidate(clickLink)));

        assertTrue(result.isEmpty());
    }

    @Test
    void keepsCandidateWhoseKnownDestinationIsNotYetVisited() {

        WorldModel worldModel = new WorldModel();
        VisitedStateMemory visitedStateMemory = new VisitedStateMemory();

        Observation login = observation("https://example.com/login", link("a:nth-of-type(1)"));
        Observation newPage = observation("https://example.com/new-page", link("a:nth-of-type(1)"));

        Action clickLink = click("a:nth-of-type(1)");

        worldModel.recordTransition(login, clickLink, newPage);
        visitedStateMemory.remember(login);
        // newPage is deliberately NOT remembered — it's known but not yet visited by this memory

        MissionContext context = context(login);

        List<CandidateAction> result = new KnownDeadEndCandidateFilter(worldModel, visitedStateMemory)
                .filter(context, List.of(candidate(clickLink)));

        assertEquals(1, result.size());
    }

    private MissionContext context(Observation current) {

        MissionContext context = new MissionContext(
                new Mission(UUID.randomUUID(), "Test", "Test", Map.of()));

        context.getExecutionState().setCurrentObservation(current);

        return context;
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

    private CandidateAction candidate(Action action) {
        return new CandidateAction(action, action.confidence(), "test");
    }
}
