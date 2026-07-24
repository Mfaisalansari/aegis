package com.aegis.core.action.handler;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;

public class WaitActionHandler implements ActionHandler {

    @Override
    public ActionType supports() {
        return ActionType.WAIT;
    }

    @Override
    public void execute(Action action, MissionContext context) {

        try {

            if (action.timeout() != null) {
                Thread.sleep(action.timeout().toMillis());
            }

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new RuntimeException("Wait interrupted", e);

        }
    }
}