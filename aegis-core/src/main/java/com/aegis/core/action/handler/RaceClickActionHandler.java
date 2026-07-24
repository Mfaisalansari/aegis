package com.aegis.core.action.handler;

import com.aegis.core.browser.Browser;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;

import java.util.Objects;

/**
 * Executes RACE_CLICK actions using the configured Browser — fires two
 * click events on the same element back-to-back to probe for race
 * conditions (e.g. two overlapping "place order" requests). Detection is
 * indirect: this doesn't verify application-level side effects like "was
 * exactly one order created" (that needs domain-specific assertions this
 * project doesn't have — see WorldModel's documented state-extraction
 * gap), it just exercises the concurrent code path and relies on the
 * existing anomaly pipeline to catch whatever breaks (console/page
 * errors, failed requests) as a result.
 */
public class RaceClickActionHandler implements ActionHandler {

    private final Browser browser;

    public RaceClickActionHandler(Browser browser) {
        this.browser = Objects.requireNonNull(browser, "browser must not be null");
    }

    @Override
    public ActionType supports() {
        return ActionType.RACE_CLICK;
    }

    @Override
    public void execute(Action action, MissionContext context) {

        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }

        if (action.target() == null || action.target().isBlank()) {
            throw new IllegalArgumentException("RACE_CLICK action requires a target.");
        }

        browser.raceClick(action.target());
    }
}
