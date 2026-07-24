package com.aegis.core.reasoning.value;

import com.aegis.model.context.MissionContext;
import com.aegis.model.observation.ElementInfo;

public class DefaultInputValueResolver implements InputValueResolver {

    private static final String DEFAULT_USERNAME = "aegis.tester";
    private static final String DEFAULT_PASSWORD = "Aegis!2345";

    @Override
    public String resolve(MissionContext context, ElementInfo element) {

        FieldPurpose.Purpose purpose = FieldPurpose.of(element);

        String username = context.getMission().parameter("username");
        String password = context.getMission().parameter("password");

        return switch (purpose) {
            case USERNAME -> username != null ? username : DEFAULT_USERNAME;
            case EMAIL -> resolveEmail(username);
            case PASSWORD -> password != null ? password : DEFAULT_PASSWORD;
            case GENERIC -> "sample";
        };
    }

    private String resolveEmail(String username) {

        if (username != null && username.contains("@")) {
            return username;
        }

        // Timestamped so repeated runs don't collide with "email already
        // registered" validation on sites that require a unique email.
        return "aegis.tester." + System.currentTimeMillis() + "@example.com";
    }
}
