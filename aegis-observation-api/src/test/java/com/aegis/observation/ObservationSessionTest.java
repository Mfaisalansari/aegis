package com.aegis.observation;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.MissionStatus;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservationSessionTest {

    @Test
    void namesTheMissionAndDescriptionFromTheConstructor() {

        ObservationSession session = new ObservationSession("Selenium", "Login Flow", "Ported from an existing Selenium suite");

        ObservationResult result = session.finish(MissionStatus.SUCCESS);

        assertEquals("Login Flow", result.context().getMission().name());
        assertEquals("Ported from an existing Selenium suite", result.context().getMission().description());
    }

    @Test
    void recordObservationBuildsTheCombinedElementsListAsTheUnionOfTheFourCategories() {

        ObservationSession session = new ObservationSession("Selenium", "Test", "Test");

        ElementInfo button = element("button", "login-button");
        ElementInfo input = element("input", "user-name");
        ElementInfo link = element("a", "help-link");
        ElementInfo select = element("select", "language");

        session.recordObservation("https://example.com/login", "Login",
                List.of(button), List.of(input), List.of(link), List.of(select));

        Observation observation = session.finish(MissionStatus.SUCCESS).context().getExecutionState().getCurrentObservation();

        assertEquals("https://example.com/login", observation.url());
        assertEquals("Login", observation.pageTitle());
        assertEquals(List.of(button), observation.buttons());
        assertEquals(List.of(input), observation.inputs());
        assertEquals(List.of(link), observation.links());
        assertEquals(List.of(select), observation.selects());
        assertEquals(4, observation.elements().size());
        assertTrue(observation.elements().containsAll(List.of(button, input, link, select)));
    }

    @Test
    void recordActionNeverFabricatesAgenuineAegisConfidenceOrReasoning() {

        ObservationSession session = new ObservationSession("Playwright", "Test", "Test");

        session.recordAction(ActionType.CLICK, "[id='submit']", null, "button");

        Action recorded = session.finish(MissionStatus.SUCCESS).context().getExecutionState().getActions().get(0);

        assertEquals(1.0, recorded.confidence());
        assertEquals("Externally observed via Playwright — not scored by AEGIS", recorded.reasoning());
        assertNull(recorded.expectedOutcome());
        assertEquals(ActionType.CLICK, recorded.type());
        assertEquals("[id='submit']", recorded.target());
        assertEquals("button", recorded.elementTag());
    }

    @Test
    void finishReturnsTheCallerSuppliedStatusUnchanged() {

        ObservationSession session = new ObservationSession("Manual", "Test", "Test");

        assertEquals(MissionStatus.FAILED, session.finish(MissionStatus.FAILED).status());
    }

    @Test
    void multipleActionsAccumulateInOrder() {

        ObservationSession session = new ObservationSession("Selenium", "Test", "Test");

        session.recordAction(ActionType.TYPE, "[id='user-name']", "aegis.tester", "input");
        session.recordAction(ActionType.CLICK, "[id='login-button']", null, "button");

        MissionContext context = session.finish(MissionStatus.SUCCESS).context();

        assertEquals(2, context.getExecutionState().getActions().size());
        assertEquals(ActionType.TYPE, context.getExecutionState().getActions().get(0).type());
        assertEquals(ActionType.CLICK, context.getExecutionState().getActions().get(1).type());
    }

    @Test
    void recordHealedLocatorFixesConfidenceAndNamesBothLocatorsInReasoning() {

        ObservationSession session = new ObservationSession("Selenium", "Test", "Test");

        session.recordHealedLocator(ActionType.CLICK, "[id='submit']", "[id*='submit']", null, "button");

        Action recorded = session.finish(MissionStatus.SUCCESS).context().getExecutionState().getActions().get(0);

        assertEquals(1.0, recorded.confidence());
        assertNull(recorded.expectedOutcome());
        assertEquals(ActionType.CLICK, recorded.type());
        assertEquals("[id*='submit']", recorded.target());
        assertTrue(recorded.reasoning().startsWith("Self-healed by AEGIS via Selenium: "));
        assertTrue(recorded.reasoning().contains("[id='submit']"));
        assertTrue(recorded.reasoning().contains("[id*='submit']"));
    }

    @Test
    void recordSelfInputFixesConfidenceAndNamesTheStrategyInReasoning() {

        ObservationSession session = new ObservationSession("Selenium", "Test", "Test");

        session.recordSelfInput("[id='email']", "aegis-tester@example.com", "input", "realistic");

        Action recorded = session.finish(MissionStatus.SUCCESS).context().getExecutionState().getActions().get(0);

        assertEquals(1.0, recorded.confidence());
        assertNull(recorded.expectedOutcome());
        assertEquals(ActionType.TYPE, recorded.type());
        assertEquals("[id='email']", recorded.target());
        assertEquals("aegis-tester@example.com", recorded.value());
        assertTrue(recorded.reasoning().startsWith("Self-input by AEGIS via Selenium (realistic strategy)"));
    }

    private ElementInfo element(String tag, String id) {
        return new ElementInfo(tag, id, "", "", tag, "", true, true, "[id='" + id + "']");
    }
}
