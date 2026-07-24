package com.aegis.core.anomaly;

import com.aegis.core.browser.Browser;
import com.aegis.model.action.Action;
import com.aegis.model.context.MissionContext;
import com.aegis.model.finding.Finding;
import com.aegis.model.finding.FindingSeverity;
import com.aegis.model.observation.AnomalySignal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns raw browser signals (console errors, uncaught exceptions, failed
 * requests, crashes) into Findings. Only classifies what the browser
 * already reported — it doesn't infer anomalies from page content, and it
 * doesn't attempt root-cause analysis (that's a job for an AI reasoner
 * later, not for hand-rolled heuristics now).
 *
 * Two things it does to keep findings useful instead of noisy:
 * - Deduplicates identical signals so a repeating error is reported once
 * - Notes the last action taken, so a finding says what it followed
 */
public class BrowserSignalAnomalyDetector implements AnomalyDetector {

    private final Browser browser;
    private final Set<String> seenSignatures = new HashSet<>();

    public BrowserSignalAnomalyDetector(Browser browser) {
        this.browser = browser;
    }

    @Override
    public List<Finding> detect(MissionContext context) {

        List<Finding> findings = new ArrayList<>();

        String lastAction = lastActionDescription(context);

        for (AnomalySignal signal : browser.drainAnomalies()) {

            String signature = signal.type() + "|" + signal.detail();

            if (!seenSignatures.add(signature)) {
                continue;
            }

            String summary = lastAction.isEmpty()
                    ? signal.type() + ": " + signal.detail()
                    : signal.type() + ": " + signal.detail() + " (after " + lastAction + ")";

            findings.add(new Finding(
                    severityOf(signal),
                    summary,
                    signal.url(),
                    signal.occurredAt()
            ));
        }

        return findings;
    }

    private String lastActionDescription(MissionContext context) {

        List<Action> actions = context.getExecutionState().getActions();

        if (actions.isEmpty()) {
            return "";
        }

        Action last = actions.get(actions.size() - 1);

        return last.type() + " " + last.target();
    }

    private FindingSeverity severityOf(AnomalySignal signal) {

        return switch (signal.type()) {
            case "CRASH" -> FindingSeverity.CRITICAL;
            case "PAGE_ERROR" -> FindingSeverity.HIGH;
            case "REQUEST_FAILED", "CONSOLE_ERROR" -> FindingSeverity.MEDIUM;
            case "DIALOG" -> FindingSeverity.LOW;
            default -> FindingSeverity.LOW;
        };
    }
}
