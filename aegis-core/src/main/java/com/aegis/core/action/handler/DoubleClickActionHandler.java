package com.aegis.core.action.handler;

import com.aegis.core.browser.Browser;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;

import java.util.Objects;

/**
 * Executes DOUBLE_CLICK actions using the configured Browser — a real
 * double-click event, not two separate clicks, to probe for
 * duplicate-submission bugs (a form/button that doesn't disable itself
 * after the first click can end up processing the same request twice).
 */
public class DoubleClickActionHandler implements ActionHandler {

    private final Browser browser;

    public DoubleClickActionHandler(Browser browser) {
        this.browser = Objects.requireNonNull(browser, "browser must not be null");
    }

    @Override
    public ActionType supports() {
        return ActionType.DOUBLE_CLICK;
    }

    @Override
    public void execute(Action action, MissionContext context) {

        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }

        if (action.target() == null || action.target().isBlank()) {
            throw new IllegalArgumentException("DOUBLE_CLICK action requires a target.");
        }

        browser.doubleClick(action.target());
    }
}
