package com.aegis.core.action.handler;

import com.aegis.core.browser.Browser;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;

import java.util.Objects;

/**
 * Executes CLICK actions using the configured Browser.
 */
public class ClickActionHandler implements ActionHandler {

    private final Browser browser;

    public ClickActionHandler(Browser browser) {
        this.browser = Objects.requireNonNull(browser, "browser must not be null");
    }

    @Override
    public ActionType supports() {
        return ActionType.CLICK;
    }

    @Override
    public void execute(Action action, MissionContext context) {

        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }

        if (action.target() == null || action.target().isBlank()) {
            throw new IllegalArgumentException("CLICK action requires a target.");
        }

        browser.click(action.target());
    }
}