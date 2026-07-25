package com.aegis.core.anomaly;

import com.aegis.core.plugin.FindingRule;
import com.aegis.model.context.MissionContext;
import com.aegis.model.finding.Finding;
import com.aegis.model.finding.FindingSeverity;
import com.aegis.model.mission.Mission;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositeAnomalyDetectorTest {

    private final MissionContext context = new MissionContext(
            new Mission(UUID.randomUUID(), "Test", "Test", Map.of()));

    @Test
    void mergesBuiltInFindingsWithEveryDiscoveredRulesFindings() {

        Finding builtInFinding = finding("built-in problem");
        Finding ruleFinding = finding("plugin problem");

        AnomalyDetector builtIn = ctx -> List.of(builtInFinding);
        FindingRule rule = ctx -> List.of(ruleFinding);

        CompositeAnomalyDetector composite = new CompositeAnomalyDetector(builtIn, List.of(rule));

        List<Finding> findings = composite.detect(context);

        assertEquals(2, findings.size());
        assertTrue(findings.contains(builtInFinding));
        assertTrue(findings.contains(ruleFinding));
    }

    @Test
    void behavesIdenticallyToTheBuiltInDetectorWhenNoRulesAreRegistered() {

        Finding builtInFinding = finding("built-in problem");
        AnomalyDetector builtIn = ctx -> List.of(builtInFinding);

        CompositeAnomalyDetector composite = new CompositeAnomalyDetector(builtIn, List.of());

        assertEquals(List.of(builtInFinding), composite.detect(context));
    }

    @Test
    void mergesFindingsFromMultipleRules() {

        AnomalyDetector builtIn = ctx -> List.of();
        FindingRule ruleA = ctx -> List.of(finding("from rule A"));
        FindingRule ruleB = ctx -> List.of(finding("from rule B"));

        CompositeAnomalyDetector composite = new CompositeAnomalyDetector(builtIn, List.of(ruleA, ruleB));

        assertEquals(2, composite.detect(context).size());
    }

    private Finding finding(String summary) {
        return new Finding(FindingSeverity.LOW, summary, "https://example.com", Instant.now());
    }
}
