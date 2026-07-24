package com.aegis.model.mission;

import java.util.Map;
import java.util.UUID;

public record Mission(
        UUID id,
        String name,
        String description,
        Map<String, String> parameters
) {

    public String parameter(String key) {
        return parameters == null ? null : parameters.get(key);
    }
}