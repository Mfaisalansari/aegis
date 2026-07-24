package com.aegis.core.action.handler;

import com.aegis.core.browser.Browser;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;

import java.util.Objects;

/**
 * Executes BACK actions using the configured Browser — navigates back in
 * history mid-workflow to probe whether the app recovers into a
 * consistent state (same goal as RefreshActionHandler: interrupted
 * workflows / inconsistent UI states). Not tied to any element.
 */
public class BackActionHandler implements ActionHandler {

    private final Browser browser;

    public BackActionHandler(Browser browser) {
        this.browser = Objects.requireNonNull(browser, "browser must not be null");
    }

    @Override
    public ActionType supports() {
        return ActionType.BACK;
    }

    @Override
    public void execute(Action action, MissionContext context) {

        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }

        browser.goBack();
    }
}
