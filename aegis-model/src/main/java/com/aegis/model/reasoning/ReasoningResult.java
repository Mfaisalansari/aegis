package com.aegis.model.reasoning;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ReasoningResult(

        CandidateAction selected,

        List<CandidateAction> candidates,

        Instant decidedAt

) {

    public ReasoningResult {

        Objects.requireNonNull(selected);
        Objects.requireNonNull(candidates);
        Objects.requireNonNull(decidedAt);

        candidates = List.copyOf(candidates);
    }

}