package com.aegis.core.reasoning.factory;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.observation.ElementInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class DefaultActionFactory implements ActionFactory {

    @Override
    public Action create(
            ActionType actionType,
            ElementInfo element,
            String value,
            String reasoning,
            double confidence) {

        return new Action(

                UUID.randomUUID(),

                actionType,

                element.locator(),

                value,

                reasoning,

                confidence,

                "Execute " + actionType,

                Duration.ofSeconds(5),

                Instant.now(),

                element.tag()

        );
    }
}