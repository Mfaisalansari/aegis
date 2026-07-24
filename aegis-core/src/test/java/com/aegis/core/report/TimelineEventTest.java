package com.aegis.core.report;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Reporting v2 Stage 3 "screenshot hooks": always null today, but a real path can be carried once populated. */
class TimelineEventTest {

    @Test
    void fourArgConstructorDefaultsScreenshotPathToNull() {

        TimelineEvent event = new TimelineEvent(Instant.now(), TimelineEventKind.OBSERVATION, "headline", "detail");

        assertNull(event.screenshotPath());
    }

    @Test
    void fiveArgConstructorCarriesAnExplicitScreenshotPath() {

        TimelineEvent event = new TimelineEvent(
                Instant.now(), TimelineEventKind.EXECUTION, "headline", "detail", "/tmp/screenshot.png");

        assertEquals("/tmp/screenshot.png", event.screenshotPath());
    }
}
