package com.aegis.core.report;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Screenshot hook: null unless MissionReportData.buildTimeline matched a captured screenshot. */
class TimelineEventTest {

    @Test
    void fourArgConstructorDefaultsScreenshotDataUriToNull() {

        TimelineEvent event = new TimelineEvent(Instant.now(), TimelineEventKind.OBSERVATION, "headline", "detail");

        assertNull(event.screenshotDataUri());
    }

    @Test
    void fiveArgConstructorCarriesAnExplicitScreenshotDataUri() {

        TimelineEvent event = new TimelineEvent(
                Instant.now(), TimelineEventKind.EXECUTION, "headline", "detail", "data:image/png;base64,AAAA");

        assertEquals("data:image/png;base64,AAAA", event.screenshotDataUri());
    }
}
