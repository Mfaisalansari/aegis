package com.aegis.model.mission;

import java.util.Map;
import java.util.UUID;

public record Mission(
        UUID id,
        String name,
        String description,
        Map<String, String> parameters
) {

    // Stage 5 hardening: defensively copy so a caller mutating their own
    // map after construction can't change a Mission already handed to
    // Aegis.run(...) — matters most for ParallelMissionRunner, where two
    // concurrently running missions must never observe each other's state.
    public Mission {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    public String parameter(String key) {
        return parameters.get(key);
    }
}