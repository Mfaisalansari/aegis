package com.aegis.web.mission;

import com.aegis.core.AegisReport;
import com.aegis.model.mission.Mission;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One submitted mission run's mutable lifecycle: created RUNNING, then
 * transitions exactly once to either DONE (with a real {@link
 * AegisReport}) or ERROR (with the failure message) when the background
 * execution finishes. {@link #state} is an {@link AtomicReference} rather
 * than a plain field because the HTTP thread polling {@code
 * /missions/{id}} and the mission executor thread completing the run
 * both touch it concurrently.
 */
public final class MissionJob {

    public enum State { RUNNING, DONE, ERROR }

    private final String id;
    private final Mission mission;
    private final String browserType;
    private final Instant submittedAt;

    private final AtomicReference<State> state = new AtomicReference<>(State.RUNNING);
    private volatile Instant finishedAt;
    private volatile AegisReport report;
    private volatile String errorMessage;

    public MissionJob(String id, Mission mission, String browserType) {
        this.id = id;
        this.mission = mission;
        this.browserType = browserType;
        this.submittedAt = Instant.now();
    }

    public String id() {
        return id;
    }

    public Mission mission() {
        return mission;
    }

    public String browserType() {
        return browserType;
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public State state() {
        return state.get();
    }

    public AegisReport report() {
        return report;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public void complete(AegisReport report) {
        this.report = report;
        this.finishedAt = Instant.now();
        this.state.set(State.DONE);
    }

    public void fail(String errorMessage) {
        this.errorMessage = errorMessage;
        this.finishedAt = Instant.now();
        this.state.set(State.ERROR);
    }
}
