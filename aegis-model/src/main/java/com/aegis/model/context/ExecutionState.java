package com.aegis.model.context;

import com.aegis.model.action.Action;
import com.aegis.model.finding.Finding;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.ReasoningStep;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExecutionState {

    private final Instant startedAt = Instant.now();

    private Observation currentObservation;

    private final List<Observation> observations = new ArrayList<>();

    private final List<Action> actions = new ArrayList<>();

    private final List<Finding> findings = new ArrayList<>();

    private final List<ReasoningStep> reasoningSteps = new ArrayList<>();

    private int currentStep;

    private int iteration;

    public Instant getStartedAt() {
        return startedAt;
    }

    public Observation getCurrentObservation() {
        return currentObservation;
    }

    public void setCurrentObservation(Observation observation) {
        this.currentObservation = observation;
        this.observations.add(observation);
    }

    // Stage 5 hardening: unmodifiable views, not copies — still reflect
    // live growth as the mission runs (internal callers like
    // MissionEngine are unaffected), but a caller reaching this through
    // the public AegisReport.missionResult().context() chain can no
    // longer mutate AEGIS's own internal state through the getter.
    public List<Observation> getObservations() {
        return Collections.unmodifiableList(observations);
    }

    public List<Action> getActions() {
        return Collections.unmodifiableList(actions);
    }

    public List<Finding> getFindings() {
        return Collections.unmodifiableList(findings);
    }

    public void addAction(Action action) {
        actions.add(action);
        currentStep++;
    }

    public void addFinding(Finding finding) {
        findings.add(finding);
    }

    public void addReasoningStep(ReasoningStep step) {
        reasoningSteps.add(step);
    }

    public List<ReasoningStep> getReasoningSteps() {
        return Collections.unmodifiableList(reasoningSteps);
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public int getIteration() {
        return iteration;
    }

    public void incrementIteration() {
        iteration++;
    }
}