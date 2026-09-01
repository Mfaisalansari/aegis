package com.aegis.core.goal;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.MissionStatus;
import com.aegis.model.observation.Observation;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Declares SUCCESS once the current URL contains the mission's
 * "successUrlContains" parameter — and, if the mission also sets
 * "requiredActionsContain" (a comma-separated list of substrings), only
 * once every one of those substrings has appeared in some
 * already-executed action's target, and its effect hasn't since been
 * undone.
 *
 * The required-actions check exists because a URL alone doesn't prove
 * anything business-meaningful happened on the way there: on
 * saucedemo.com, the shortest path to "checkout-complete" is to check
 * out an EMPTY cart — no add-to-cart click required — so an unqualified
 * URL goal rewards that hollow shortcut with SUCCESS. Naming required
 * actions (e.g. "add-to-cart") instead of a fixed step sequence keeps
 * exploration autonomous: the agent still discovers its own path and can
 * act in any order, it just can't call the mission done until the
 * actions that make the outcome real have actually happened.
 *
 * A required action having happened at some point still isn't the full
 * story: a real exploration agent legitimately tries "remove from cart"
 * too (that's a real feature worth testing), and it scores exactly as
 * attractive as "add to cart" once both are equally novel — so it's
 * common for an add to be immediately followed by removing the same
 * item. "requiredActionsContain=add-to-cart" alone would still credit
 * that as SUCCESS even though the cart may be empty again by the time
 * checkout-complete is reached a second time. The optional
 * "undoActionsContain" parameter (same comma-separated format) closes
 * that gap: a required action only counts if the most recent action
 * matching either it or any undo substring was the required one, not an
 * undo of it. Missions that don't set undoActionsContain keep the
 * original "ever happened" behavior.
 *
 * DOUBLE_CLICK and RACE_CLICK actions never count toward either check —
 * both are documented elsewhere (DoubleClickCandidateActionGenerator,
 * RaceClickCandidateActionGenerator) as deliberate stress probes with an
 * uncertain net effect, not reliable evidence of real user intent. This
 * isn't theoretical: live-testing this exact evaluator against
 * saucedemo.com showed a DOUBLE_CLICK on an "Add to cart" button (whose
 * label/handler swaps to "Remove" the instant the first sub-click lands)
 * add the item and then immediately remove it again within the same
 * double-click — a net no-op that would still satisfy a naive
 * "add-to-cart happened after the last remove" check even though the
 * cart was empty at checkout.
 *
 * Missions that don't set successUrlContains never resolve via this
 * evaluator, matching the original behavior exactly; missions that
 * don't set requiredActionsContain behave exactly as before too.
 */
public class UrlContainsGoalEvaluator implements GoalEvaluator {

    private static final String URL_PARAMETER_KEY = "successUrlContains";
    private static final String REQUIRED_ACTIONS_PARAMETER_KEY = "requiredActionsContain";
    private static final String UNDO_ACTIONS_PARAMETER_KEY = "undoActionsContain";
    private static final Set<ActionType> UNRELIABLE_ACTION_TYPES = Set.of(ActionType.DOUBLE_CLICK, ActionType.RACE_CLICK);

    @Override
    public Optional<MissionStatus> evaluate(MissionContext context) {

        String expectedUrl = context.getMission().parameter(URL_PARAMETER_KEY);

        if (expectedUrl == null || expectedUrl.isBlank()) {
            return Optional.empty();
        }

        Observation observation = context.getExecutionState().getCurrentObservation();

        if (observation == null || observation.url() == null) {
            return Optional.empty();
        }

        if (!observation.url().contains(expectedUrl)) {
            return Optional.empty();
        }

        if (!requiredActionsHaveHappenedAndStillHold(context)) {
            return Optional.empty();
        }

        return Optional.of(MissionStatus.SUCCESS);
    }

    private boolean requiredActionsHaveHappenedAndStillHold(MissionContext context) {

        String required = context.getMission().parameter(REQUIRED_ACTIONS_PARAMETER_KEY);

        if (required == null || required.isBlank()) {
            return true;
        }

        List<Action> executed = context.getExecutionState().getActions();
        List<String> undoSubstrings = splitAndNormalize(context.getMission().parameter(UNDO_ACTIONS_PARAMETER_KEY));

        for (String requirement : splitAndNormalize(required)) {

            int lastRequiredIndex = lastIndexMatching(executed, requirement);

            if (lastRequiredIndex < 0) {
                return false;
            }

            if (!undoSubstrings.isEmpty()) {

                int lastUndoIndex = undoSubstrings.stream()
                        .mapToInt(undo -> lastIndexMatching(executed, undo))
                        .max()
                        .orElse(-1);

                if (lastUndoIndex > lastRequiredIndex) {
                    return false;
                }
            }
        }

        return true;
    }

    private int lastIndexMatching(List<Action> executed, String substring) {

        for (int i = executed.size() - 1; i >= 0; i--) {

            Action action = executed.get(i);

            if (UNRELIABLE_ACTION_TYPES.contains(action.type())) {
                continue;
            }

            if (action.target() != null && action.target().toLowerCase().contains(substring)) {
                return i;
            }
        }

        return -1;
    }

    private List<String> splitAndNormalize(String commaSeparated) {

        if (commaSeparated == null || commaSeparated.isBlank()) {
            return List.of();
        }

        return Arrays.stream(commaSeparated.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase())
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
