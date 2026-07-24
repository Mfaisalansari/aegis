package com.aegis.model.observation;

import java.time.Instant;
import java.util.List;

public record Observation(

        String url,

        String pageTitle,

        List<ElementInfo> elements,

        List<ElementInfo> buttons,

        List<ElementInfo> inputs,

        List<ElementInfo> links,

        List<ElementInfo> selects,

        Instant capturedAt

) {
}