package com.aegis.core.action.handler;

import com.aegis.core.browser.Browser;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;

import java.util.Objects;

public class TypeActionHandler implements ActionHandler {

    private final Browser browser;

    public TypeActionHandler(Browser browser) {
        this.browser = Objects.requireNonNull(browser);
    }

    @Override
    public ActionType supports() {
        return ActionType.TYPE;
    }

    @Override
    public void execute(Action action, MissionContext context) {

        if (action.target() == null || action.target().isBlank()) {
            throw new IllegalArgumentException("TYPE action requires target.");
        }

        browser.type(action.target(), action.value());
    }
}