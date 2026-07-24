package com.aegis.core.bug;

import com.aegis.model.finding.FindingSeverity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleBasedRecommendationEngineTest {

    @Test
    void alwaysReturnsTheGivenFallbackUnchanged() {

        BugCluster cluster = new BugCluster(
                "CRASH|", "CRASH: tab crashed", FindingSeverity.CRITICAL,
                1, Set.of("https://example.com"), Instant.now(), Instant.now());

        String result = new RuleBasedRecommendationEngine().recommend(List.of(cluster), "the fallback text");

        assertEquals("the fallback text", result);
    }
}
