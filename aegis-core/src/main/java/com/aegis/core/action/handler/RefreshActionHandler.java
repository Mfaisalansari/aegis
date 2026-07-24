package com.aegis.core.action.handler;

import com.aegis.core.browser.Browser;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;

import java.util.Objects;

/**
 * Executes REFRESH actions using the configured Browser — reloads the
 * current page mid-workflow to probe whether the app recovers into a
 * consistent state (goal: interrupted-workflow / inconsistent-UI-state
 * detection). Not tied to any element, so unlike ClickActionHandler etc.
 * there's no target to validate.
 */
public class RefreshActionHandler implements ActionHandler {

    private final Browser browser;

    public RefreshActionHandler(Browser browser) {
        this.browser = Objects.requireNonNull(browser, "browser must not be null");
    }

    @Override
    public ActionType supports() {
        return ActionType.REFRESH;
    }

    @Override
    public void execute(Action action, MissionContext context) {

        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }

        browser.refresh();
    }
}
