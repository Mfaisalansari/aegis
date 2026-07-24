package com.aegis.model.reasoning;

import java.time.Instant;
import java.util.List;

public record ReasoningStep(
        int step,
        List<CandidateAction> candidates,
        CandidateAction selected,
        Instant timestamp
) {
}
