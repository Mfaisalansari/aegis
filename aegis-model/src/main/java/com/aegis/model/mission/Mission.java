package com.aegis.model.mission;

import java.util.UUID;

public record Mission(
        UUID id,
        String name,
        String description
) {
}