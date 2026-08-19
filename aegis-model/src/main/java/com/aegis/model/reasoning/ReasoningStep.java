package com.aegis.model.reasoning;

import java.time.Instant;
import java.util.List;

public record ReasoningStep(
        int step,
        List<CandidateAction> candidates,
        CandidateAction selected,
        Instant timestamp,
        /** The exploration strategy key (e.g. "greedy", "coverage-aware") active when this step's candidate was chosen — "adaptive" can change this mid-mission. */
        String strategy
) {
}
