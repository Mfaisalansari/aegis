package com.aegis.web.mission;

import com.aegis.core.AegisReport;
import com.aegis.core.stream.MissionEventListener;
import com.aegis.core.stream.MissionStreamEvent;
import com.aegis.model.mission.Mission;
import com.aegis.reporting.RedesignedMissionReportGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One submitted mission run's mutable lifecycle: created RUNNING, then
 * transitions exactly once to either DONE (with a real {@link
 * AegisReport}) or ERROR (with the failure message) when the background
 * execution finishes. {@link #state} is an {@link AtomicReference} rather
 * than a plain field because the HTTP thread polling {@code
 * /missions/{id}} and the mission executor thread completing the run
 * both touch it concurrently.
 *
 * Implements {@link MissionEventListener} directly so {@code
 * MissionExecutor} can pass {@code this} straight to {@code Aegis.run(...)}
 * — every real observation/decision/execution the mission engine emits
 * lands in {@link #events}, a {@link CopyOnWriteArrayList} for the same
 * reason {@link #state} is an {@code AtomicReference}: one background
 * mission thread writes, any number of HTTP poll threads read, and event
 * volume is small enough (roughly 3 events per iteration, capped at
 * {@link #maxIterations()}) that no external locking is worth it.
 *
 * A job can also come from {@link #reloaded} — reconstructed by {@code
 * MissionHistoryStore} from a file written at a previous completion,
 * surviving a server restart. A reloaded job never has a live {@link
 * #report} (there is no real {@code AegisReport} object graph to
 * reconstruct, only what was persisted), so {@link #hasLiveReport()}
 * distinguishes the two; every dashboard-facing accessor
 * ({@link #summary()}, {@link #htmlReport()}, etc.) works identically
 * either way so callers never need to branch on which kind of job they
 * have.
 */
public final class MissionJob implements MissionEventListener {

    public enum State { RUNNING, DONE, ERROR }

    private static final String MAX_ITERATIONS_PARAMETER = "maxIterations";
    private static final int DEFAULT_MAX_ITERATIONS = 10;

    private final String id;
    private final Mission mission;
    private final String browserType;
    private final Instant submittedAt;
    private final List<MissionStreamEvent> events = new CopyOnWriteArrayList<>();

    private final AtomicReference<State> state;
    private volatile Instant finishedAt;
    private volatile AegisReport report;
    private volatile String errorMessage;
    private volatile MissionSummary summary;

    /** Only ever non-null for a {@link #reloaded} job — a live job's report strings live on {@link #report} instead. */
    private final String persistedHtmlReport;
    private final String persistedJsonReport;
    private final String persistedTextReport;
    private final String persistedRedesignedHtmlReport;

    public MissionJob(String id, Mission mission, String browserType) {
        this(id, mission, browserType, Instant.now(), null, State.RUNNING, null, null, null, null, null, null, null);
    }

    /**
     * Reconstructs a finished job from persisted data — see {@code
     * MissionHistoryStore#loadAll()}. Only ever called with {@code state}
     * DONE or ERROR; a mission that was still RUNNING when the server
     * stopped was never persisted in the first place (there's nothing to
     * reload it into — see {@code Aegis.run}'s lack of any
     * checkpointing), so this factory has no RUNNING case to handle.
     */
    public static MissionJob reloaded(
            String id, Mission mission, String browserType, Instant submittedAt, Instant finishedAt,
            State state, String errorMessage, MissionSummary summary,
            String htmlReport, String jsonReport, String textReport, String redesignedHtmlReport) {

        return new MissionJob(
                id, mission, browserType, submittedAt, finishedAt, state, errorMessage, summary,
                htmlReport, jsonReport, textReport, redesignedHtmlReport, null);
    }

    private MissionJob(
            String id, Mission mission, String browserType, Instant submittedAt, Instant finishedAt,
            State state, String errorMessage, MissionSummary summary,
            String persistedHtmlReport, String persistedJsonReport, String persistedTextReport,
            String persistedRedesignedHtmlReport, AegisReport report) {

        this.id = id;
        this.mission = mission;
        this.browserType = browserType;
        this.submittedAt = submittedAt;
        this.finishedAt = finishedAt;
        this.state = new AtomicReference<>(state);
        this.errorMessage = errorMessage;
        this.summary = summary;
        this.persistedHtmlReport = persistedHtmlReport;
        this.persistedJsonReport = persistedJsonReport;
        this.persistedTextReport = persistedTextReport;
        this.persistedRedesignedHtmlReport = persistedRedesignedHtmlReport;
        this.report = report;
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

    /** The live report object, if this job finished during this server session — {@code null} for a reloaded job. See {@link #hasLiveReport()}. */
    public AegisReport report() {
        return report;
    }

    /** False for every reloaded job, and for a live job before it reaches DONE. */
    public boolean hasLiveReport() {
        return report != null;
    }

    public String errorMessage() {
        return errorMessage;
    }

    /** Coverage/findings/actions/plan — computed once here so both a live and a reloaded job expose it identically. */
    public MissionSummary summary() {
        return summary;
    }

    public String htmlReport() {
        return report != null ? report.htmlReport() : persistedHtmlReport;
    }

    public String jsonReport() {
        return report != null ? report.jsonReport() : persistedJsonReport;
    }

    public String textReport() {
        return report != null ? report.textReport() : persistedTextReport;
    }

    /** Regenerated fresh on every call for a live job (always up to date); a frozen snapshot for a reloaded one. */
    public String redesignedHtmlReport() {
        return report != null
                ? new RedesignedMissionReportGenerator().generate(report.reportData())
                : persistedRedesignedHtmlReport;
    }

    public void complete(AegisReport report) {
        this.report = report;
        this.summary = MissionSummary.from(report);
        this.finishedAt = Instant.now();
        this.state.set(State.DONE);
    }

    public void fail(String errorMessage) {
        this.errorMessage = errorMessage;
        this.finishedAt = Instant.now();
        this.state.set(State.ERROR);
    }

    @Override
    public void onEvent(MissionStreamEvent event) {
        events.add(event);
    }

    /** A snapshot, not the live list — a caller can't mutate this job's real event history through it. */
    public List<MissionStreamEvent> events() {
        return List.copyOf(events);
    }

    /** The real engine's own iteration count, read off the most recent stream event — 0 before the first one arrives. */
    public int iteration() {
        return events.isEmpty() ? 0 : events.get(events.size() - 1).iteration();
    }

    /** Mirrors {@code DefaultMissionController.DEFAULT_MAX_ITERATIONS}/its parsing — that constant is private on a frozen class. */
    public int maxIterations() {

        String configured = mission.parameter(MAX_ITERATIONS_PARAMETER);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_MAX_ITERATIONS;
        }

        try {
            return Integer.parseInt(configured.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_ITERATIONS;
        }
    }

    public long elapsedSeconds() {
        Instant end = finishedAt != null ? finishedAt : Instant.now();
        return Duration.between(submittedAt, end).toSeconds();
    }
}
