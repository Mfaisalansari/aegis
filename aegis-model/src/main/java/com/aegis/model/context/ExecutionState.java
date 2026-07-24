package com.aegis.model.context;

import com.aegis.model.action.Action;
import com.aegis.model.finding.Finding;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.ReasoningStep;

import java.time.Instant;
import java.util.ArrayList;
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

    public List<Observation> getObservations() {
        return observations;
    }

    public List<Action> getActions() {
        return actions;
    }

    public List<Finding> getFindings() {
        return findings;
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
        return reasoningSteps;
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