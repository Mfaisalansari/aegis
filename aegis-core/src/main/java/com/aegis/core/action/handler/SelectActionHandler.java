package com.aegis.core.action.handler;

import com.aegis.core.browser.Browser;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;

import java.util.Objects;

/**
 * Executes SELECT actions using the configured Browser.
 */
public class SelectActionHandler implements ActionHandler {

    private final Browser browser;

    public SelectActionHandler(Browser browser) {
        this.browser = Objects.requireNonNull(browser, "browser must not be null");
    }

    @Override
    public ActionType supports() {
        return ActionType.SELECT;
    }

    @Override
    public void execute(Action action, MissionContext context) {

        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }

        if (action.target() == null || action.target().isBlank()) {
            throw new IllegalArgumentException("SELECT action requires a target.");
        }

        browser.select(action.target());
    }
}
