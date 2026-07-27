package com.aegis.core.knowledge;

import java.util.List;

/**
 * Computed-style/geometry data for one element, captured once per
 * settled state by {@code SignalCapturingObserver} — the data {@link
 * com.aegis.model.observation.ElementInfo} was never designed to carry.
 * {@code box} is {@code null} when Playwright couldn't resolve one (the
 * element became detached/hidden between observation and capture).
 * {@code hrefs} is non-empty only for {@code <a>} elements.
 */
public record ElementSnapshot(
        String locator,
        String accessibleName,
        List<String> hrefs,
        BoundingBox box,
        String color,
        String background,
        double fontSize,
        boolean textTruncated
) {

    public ElementSnapshot {
        hrefs = hrefs == null ? List.of() : List.copyOf(hrefs);
    }
}
