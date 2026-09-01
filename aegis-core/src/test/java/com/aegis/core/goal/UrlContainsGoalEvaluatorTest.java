package com.aegis.core.goal;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionStatus;
import com.aegis.model.observation.Observation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlContainsGoalEvaluatorTest {

    private final UrlContainsGoalEvaluator evaluator = new UrlContainsGoalEvaluator();

    @Test
    void neverResolvesWhenTheMissionSetsNoUrlGoal() {

        MissionContext context = context(Map.of());
        context.getExecutionState().setCurrentObservation(observation("https://example.com/checkout-complete"));

        assertEquals(Optional.empty(), evaluator.evaluate(context));
    }

    @Test
    void succeedsOnUrlMatchAloneWhenNoRequiredActionsAreSet() {

        MissionContext context = context(Map.of("successUrlContains", "checkout-complete"));
        context.getExecutionState().setCurrentObservation(observation("https://example.com/checkout-complete"));

        assertEquals(Optional.of(MissionStatus.SUCCESS), evaluator.evaluate(context));
    }

    // Regression test for a real-world hollow-success case: on
    // saucedemo.com, the shortest path to "checkout-complete" is to
    // check out an EMPTY cart — no add-to-cart click required — so a
    // bare URL goal rewards that with SUCCESS even though nothing of
    // business value was actually tested. requiredActionsContain must
    // block that until a real add-to-cart action has happened.
    @Test
    void doesNotSucceedOnUrlMatchAloneWhenARequiredActionNeverHappened() {

        MissionContext context = context(Map.of(
                "successUrlContains", "checkout-complete",
                "requiredActionsContain", "add-to-cart"));
        context.getExecutionState().setCurrentObservation(observation("https://example.com/checkout-complete"));

        assertEquals(Optional.empty(), evaluator.evaluate(context));
    }

    @Test
    void succeedsOnceTheUrlIsReachedAndTheRequiredActionAlreadyHappened() {

        MissionContext context = context(Map.of(
                "successUrlContains", "checkout-complete",
                "requiredActionsContain", "add-to-cart"));
        context.getExecutionState().addAction(click("[id='add-to-cart-sauce-labs-backpack']"));
        context.getExecutionState().setCurrentObservation(observation("https://example.com/checkout-complete"));

        assertEquals(Optional.of(MissionStatus.SUCCESS), evaluator.evaluate(context));
    }

    @Test
    void allRequiredActionsMustHaveHappenedNotJustOne() {

        MissionContext context = context(Map.of(
                "successUrlContains", "checkout-complete",
                "requiredActionsContain", "add-to-cart, first-name"));
        context.getExecutionState().addAction(click("[id='add-to-cart-sauce-labs-backpack']"));
        context.getExecutionState().setCurrentObservation(observation("https://example.com/checkout-complete"));

        // "first-name" was never typed into, so this must not succeed yet.
        assertEquals(Optional.empty(), evaluator.evaluate(context));

        context.getExecutionState().addAction(click("[id='first-name']"));

        assertEquals(Optional.of(MissionStatus.SUCCESS), evaluator.evaluate(context));
    }

    @Test
    void requiredActionMatchIsCaseInsensitive() {

        MissionContext context = context(Map.of(
                "successUrlContains", "checkout-complete",
                "requiredActionsContain", "ADD-TO-CART"));
        context.getExecutionState().addAction(click("[id='add-to-cart-sauce-labs-backpack']"));
        context.getExecutionState().setCurrentObservation(observation("https://example.com/checkout-complete"));

        assertTrue(evaluator.evaluate(context).isPresent());
    }

    // Regression test for a real-world "add then undo" gap: a generic
    // exploration agent scores "remove from cart" exactly as attractive
    // as "add to cart" once both are equally novel, so an add is
    // routinely followed by removing the same item. Without
    // undoActionsContain, requiredActionsContain="add-to-cart" alone
    // would still credit that as SUCCESS even though the cart may be
    // empty again by the time checkout-complete is reached.
    @Test
    void doesNotSucceedWhenTheRequiredActionWasUndoneAfterwards() {

        MissionContext context = context(Map.of(
                "successUrlContains", "checkout-complete",
                "requiredActionsContain", "add-to-cart",
                "undoActionsContain", "remove"));
        context.getExecutionState().addAction(click("[id='add-to-cart-sauce-labs-backpack']"));
        context.getExecutionState().addAction(click("[id='remove-sauce-labs-backpack']"));
        context.getExecutionState().setCurrentObservation(observation("https://example.com/checkout-complete"));

        assertEquals(Optional.empty(), evaluator.evaluate(context));
    }

    @Test
    void succeedsWhenTheRequiredActionWasReDoneAfterBeingUndone() {

        MissionContext context = context(Map.of(
                "successUrlContains", "checkout-complete",
                "requiredActionsContain", "add-to-cart",
                "undoActionsContain", "remove"));
        context.getExecutionState().addAction(click("[id='add-to-cart-sauce-labs-backpack']"));
        context.getExecutionState().addAction(click("[id='remove-sauce-labs-backpack']"));
        context.getExecutionState().addAction(click("[id='add-to-cart-sauce-labs-backpack']"));
        context.getExecutionState().setCurrentObservation(observation("https://example.com/checkout-complete"));

        assertEquals(Optional.of(MissionStatus.SUCCESS), evaluator.evaluate(context));
    }

    @Test
    void undoActionsContainHasNoEffectWhenNotSet() {

        MissionContext context = context(Map.of(
                "successUrlContains", "checkout-complete",
                "requiredActionsContain", "add-to-cart"));
        context.getExecutionState().addAction(click("[id='add-to-cart-sauce-labs-backpack']"));
        context.getExecutionState().addAction(click("[id='remove-sauce-labs-backpack']"));
        context.getExecutionState().setCurrentObservation(observation("https://example.com/checkout-complete"));

        // No undoActionsContain configured — original "ever happened"
        // behavior is preserved exactly.
        assertEquals(Optional.of(MissionStatus.SUCCESS), evaluator.evaluate(context));
    }

    // Regression test for a real gap found live: on saucedemo.com, an
    // "Add to cart" button relabels itself to "Remove" the instant the
    // first sub-click of a DOUBLE_CLICK lands, so a stress-test
    // double-click nets out as add-then-immediately-remove — a real
    // no-op the cart stayed empty through — yet its target string still
    // contains "add-to-cart", so a naive check would count it as a
    // genuine, later add that "un-does" an earlier real remove. Neither
    // DOUBLE_CLICK nor RACE_CLICK may satisfy a required action or count
    // as undoing one; only a deliberate CLICK/TYPE/SELECT can.
    @Test
    void doubleClickAndRaceClickNeverCountTowardARequiredAction() {

        MissionContext context = context(Map.of(
                "successUrlContains", "checkout-complete",
                "requiredActionsContain", "add-to-cart"));
        context.getExecutionState().addAction(doubleClick("[id='add-to-cart-sauce-labs-backpack']"));
        context.getExecutionState().setCurrentObservation(observation("https://example.com/checkout-complete"));

        assertEquals(Optional.empty(), evaluator.evaluate(context));
    }

    @Test
    void aDoubleClickOnAddToCartCannotMaskAnEarlierRealRemove() {

        MissionContext context = context(Map.of(
                "successUrlContains", "checkout-complete",
                "requiredActionsContain", "add-to-cart",
                "undoActionsContain", "remove"));
        context.getExecutionState().addAction(click("[id='add-to-cart-sauce-labs-backpack']"));
        context.getExecutionState().addAction(click("[id='remove-sauce-labs-backpack']"));
        // A stress-test double-click on the (now-relabeled) button — its
        // target still says "add-to-cart", but it must not count as a
        // real re-add that clears the earlier remove.
        context.getExecutionState().addAction(doubleClick("[id='add-to-cart-sauce-labs-backpack']"));
        context.getExecutionState().setCurrentObservation(observation("https://example.com/checkout-complete"));

        assertEquals(Optional.empty(), evaluator.evaluate(context));
    }

    private MissionContext context(Map<String, String> parameters) {
        return new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", parameters));
    }

    private Observation observation(String url) {
        return new Observation(url, "Title", List.of(), List.of(), List.of(), List.of(), List.of(), Instant.now());
    }

    private Action click(String target) {
        return new Action(
                UUID.randomUUID(), ActionType.CLICK, target, "", "test",
                0.5, "test", Duration.ofSeconds(5), Instant.now(), "button"
        );
    }

    private Action doubleClick(String target) {
        return new Action(
                UUID.randomUUID(), ActionType.DOUBLE_CLICK, target, "", "test",
                0.2, "test", Duration.ofSeconds(5), Instant.now(), "button"
        );
    }
}
