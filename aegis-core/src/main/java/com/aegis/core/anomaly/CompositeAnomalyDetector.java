package com.aegis.core.anomaly;

import com.aegis.core.plugin.FindingRule;
import com.aegis.model.context.MissionContext;
import com.aegis.model.finding.Finding;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a built-in {@link AnomalyDetector} (in practice, always
 * {@link BrowserSignalAnomalyDetector}) and runs every discovered
 * {@link FindingRule} plugin alongside it, merging both sets of findings
 * — the same "built-ins plus plugins" composition already used for
 * candidate generation ({@code CompositeCandidateActionGenerator}). The
 * wrapped detector's own behavior is completely untouched; this is a new
 * class implementing the (frozen) {@code AnomalyDetector} interface, not
 * a change to that interface or to {@code BrowserSignalAnomalyDetector}.
 */
public class CompositeAnomalyDetector implements AnomalyDetector {

    private final AnomalyDetector builtIn;
    private final List<FindingRule> rules;

    public CompositeAnomalyDetector(AnomalyDetector builtIn, List<FindingRule> rules) {
        this.builtIn = builtIn;
        this.rules = rules;
    }

    @Override
    public List<Finding> detect(MissionContext context) {

        List<Finding> findings = new ArrayList<>(builtIn.detect(context));

        for (FindingRule rule : rules) {
            findings.addAll(rule.evaluate(context));
        }

        return findings;
    }
}
