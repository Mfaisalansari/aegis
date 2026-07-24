package com.aegis.model.observation;

public record ElementInfo(
        String tag,
        String id,
        String name,
        String text,
        String type,
        String value,
        boolean visible,
        boolean enabled,
        String locator
) {
}