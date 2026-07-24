package com.aegis.model.mission;

import com.aegis.model.context.MissionContext;

public record MissionResult(
        MissionContext context,
        MissionStatus status
) {
}