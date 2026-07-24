package com.aegis.core.reasoning.generator;

import com.aegis.core.reasoning.mapper.DefaultElementActionMapper;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceClickCandidateActionGeneratorTest {

    private final RaceClickCandidateActionGenerator generator =
            new RaceClickCandidateActionGenerator(new DefaultElementActionMapper());

    @Test
    void producesNothingByDefault() {

        MissionContext context = context(Map.of());
        setObservation(context, List.of(submitButton()), List.of(), List.of());

        assertTrue(generator.generate(context).isEmpty(), "must be opt-in");
    }

    @Test
    void producesNothingWhenNoObservationYet() {

        MissionContext context = context(Map.of("raceConditions", "enabled"));

        assertTrue(generator.generate(context).isEmpty());
    }

    @Test
    void producesARaceClickCandidatePerClickableElementWhenEnabled() {

        MissionContext context = context(Map.of("raceConditions", "enabled"));
        setObservation(context, List.of(submitButton()), List.of(textInput()), List.of(link()));

        List<CandidateAction> candidates = generator.generate(context);

        List<ActionType> types = candidates.stream().map(c -> c.action().type()).collect(Collectors.toList());

        // submit button + link are CLICK-capable; the text input isn't.
        assertEquals(2, candidates.size());
        assertTrue(types.stream().allMatch(t -> t == ActionType.RACE_CLICK));
    }

    @Test
    void skipsInvisibleElements() {

        MissionContext context = context(Map.of("raceConditions", "enabled"));
        ElementInfo hiddenButton = new ElementInfo("button", "hidden", "hidden", "", "submit", "",
                false, true, "#hidden");
        setObservation(context, List.of(hiddenButton), List.of(), List.of());

        assertTrue(generator.generate(context).isEmpty());
    }

    private ElementInfo submitButton() {
        return new ElementInfo("button", "place-order", "place-order", "Place Order", "submit", "",
                true, true, "#place-order");
    }

    private ElementInfo textInput() {
        return new ElementInfo("input", "FirstName", "FirstName", "", "text", "",
                true, true, "#FirstName");
    }

    private ElementInfo link() {
        return new ElementInfo("a", "", "", "Home", "link", "",
                true, true, ":nth-match(a, 1)");
    }

    private void setObservation(
            MissionContext context, List<ElementInfo> buttons, List<ElementInfo> inputs, List<ElementInfo> links) {

        context.getExecutionState().setCurrentObservation(new Observation(
                "https://example.com", "t", List.of(), buttons, inputs, links, List.of(), Instant.now()));
    }

    private MissionContext context(Map<String, String> parameters) {
        return new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", parameters));
    }
}
