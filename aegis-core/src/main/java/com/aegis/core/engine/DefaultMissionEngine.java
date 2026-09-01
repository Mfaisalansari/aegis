package com.aegis.core.engine;

import com.aegis.core.anomaly.AnomalyDetector;
import com.aegis.core.browser.Browser;
import com.aegis.core.controller.MissionController;
import com.aegis.core.executor.Executor;
import com.aegis.core.goal.GoalEvaluator;
import com.aegis.core.observer.Observer;
import com.aegis.core.planner.Planner;
import com.aegis.core.reasoning.experience.ExperienceRecorder;
import com.aegis.core.reasoning.memory.ExecutionMemory;
import com.aegis.core.reasoning.memory.StateSignature;
import com.aegis.core.world.WorldModel;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.ExecutionState;
import com.aegis.model.context.MissionContext;
import com.aegis.model.experience.ExperienceOutcome;
import com.aegis.model.finding.Finding;
import com.aegis.model.finding.FindingSeverity;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionResult;
import com.aegis.model.mission.MissionStatus;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

public class DefaultMissionEngine implements MissionEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultMissionEngine.class);

    /**
     * How many times in a row the exact same (type, target) action may be
     * executed before the mission is stopped early as stagnated, rather
     * than exhausting the full {@code maxIterations} budget doing nothing
     * new. A global, strategy-independent backstop — deliberately keyed
     * on the raw executed action rather than {@code StateSignature}
     * (which a live, dynamically-changing page — third-party scripts,
     * rotating ads, lazy-loaded widgets — can flicker between near-
     * duplicate variants of, silently defeating any signature-based
     * stuck detector). Distinct actions on the same URL (real
     * exploration of a page's many elements) never trip this; only the
     * identical action recurring back-to-back does.
     */
    private static final int STAGNATION_LIMIT = 20;

    /**
     * How many consecutive iterations may produce an observation
     * identical to the one before (same interactive elements, same
     * values) before the mission is stopped early. Catches a broader
     * class of non-progress than {@link #STAGNATION_LIMIT} alone: two or
     * more DISTINCT actions oscillating back and forth (e.g. retyping
     * the same already-typed value into two fields in alternation, seen
     * on a real login page after logging out) never repeat the exact
     * same (type, target) back-to-back, so the identical-action counter
     * never trips, yet nothing is actually happening either. Genuine
     * exploration — even many actions in a row on the same URL — almost
     * always changes something observable (a new element, a changed
     * value, a toggled state) and never approaches this threshold.
     */
    private static final int NO_VISIBLE_CHANGE_LIMIT = 20;

    /**
     * How many consecutive planned actions are inspected for a strict
     * period-2 oscillation (A, B, A, B, A, B, ...) before stopping the
     * mission early. Deliberately keyed on the raw (type, target) action
     * pair the planner chose, not {@code StateSignature} — a page whose
     * DOM is jittery (e.g. an unstable locator elsewhere on the page,
     * such as a submit button that intermittently falls back to a
     * positional {@code :nth-match} selector) can flip the *whole* page
     * signature even though nothing about the two oscillating fields
     * themselves changed, which silently defeats ExecutionMemory's
     * per-signature "already tried" tracking. This backstop also catches
     * cases {@link #NO_VISIBLE_CHANGE_LIMIT} cannot: retyping into two
     * fields IS a real, visible change each step (blank -> filled ->
     * blank again), so the broader no-visible-change detector never
     * trips even though the mission is making no real progress.
     */
    private static final int OSCILLATION_WINDOW = 20;

    private final Browser browser;
    private final Observer observer;
    private final Planner planner;
    private final Executor executor;
    private final MissionController controller;
    private final ExecutionMemory memory;
    private final GoalEvaluator goalEvaluator;
    private final AnomalyDetector anomalyDetector;
    private final WorldModel worldModel;
    private final ExperienceRecorder experienceRecorder;

    public DefaultMissionEngine(
            Browser browser,
            Observer observer,
            Planner planner,
            Executor executor,
            MissionController controller,
            ExecutionMemory memory,
            GoalEvaluator goalEvaluator,
            AnomalyDetector anomalyDetector,
            WorldModel worldModel,
            ExperienceRecorder experienceRecorder) {

        this.browser = browser;
        this.observer = observer;
        this.planner = planner;
        this.executor = executor;
        this.controller = controller;
        this.memory = memory;
        this.goalEvaluator = goalEvaluator;
        this.anomalyDetector = anomalyDetector;
        this.worldModel = worldModel;
        this.experienceRecorder = experienceRecorder;
    }

    @Override
    public MissionResult execute(Mission mission) {

        log.info("Mission started: {}", mission.name());

        MissionContext context = new MissionContext(mission);
        ExecutionState state = context.getExecutionState();

        Observation previousObservation = null;
        String stagnantActionKey = null;
        int stagnantActionRepeatCount = 0;
        int noVisibleChangeStreak = 0;
        Deque<String> recentActionKeys = new ArrayDeque<>();

        try {

            /*
             * Navigate to the mission start page.
             *
             * Inside the try (Stage 5 hardening): an unreachable/invalid
             * baseUrl is a realistic, everyday failure — without this, it
             * would throw before the finally below ever runs, leaking the
             * browser's Playwright driver subprocess.
             */
            String startUrl = mission.parameter("baseUrl");

            if (startUrl != null && !startUrl.isBlank()) {
                browser.navigate(startUrl);
            }

            while (controller.shouldContinue(context)) {

                try {

                    // Observe current application state
                    observer.observe(context);

                    Observation currentObservation = context.getExecutionState().getCurrentObservation();

                    // Record the transition that led here, if we know what caused it
                    List<Action> actionsSoFar = state.getActions();

                    if (previousObservation != null && !actionsSoFar.isEmpty()) {

                        Action lastAction = actionsSoFar.get(actionsSoFar.size() - 1);

                        worldModel.recordTransition(previousObservation, lastAction, currentObservation);
                    }

                    // Stagnation backstop, broader form: the LAST action changed
                    // nothing observable at all (same interactive elements, same
                    // values as before it ran). Catches non-progress that never
                    // repeats one exact action back-to-back — e.g. two distinct
                    // actions (retyping an already-typed value into two fields)
                    // oscillating forever — which STAGNATION_LIMIT below can't see.
                    if (previousObservation != null
                            && previousObservation.url().equals(currentObservation.url())
                            && previousObservation.elements().equals(currentObservation.elements())) {
                        noVisibleChangeStreak++;
                    } else {
                        noVisibleChangeStreak = 0;
                    }

                    previousObservation = currentObservation;

                    // Detect anomalies (console errors, crashes, failed requests)
                    for (Finding finding : anomalyDetector.detect(context)) {
                        state.addFinding(finding);
                        log.warn("Finding [{}] {}", finding.severity(), finding.summary());
                    }

                    // Check whether the goal has already been reached
                    Optional<MissionStatus> outcome = goalEvaluator.evaluate(context);

                    if (outcome.isPresent()) {

                        log.info("Goal reached: {}", outcome.get());

                        return new MissionResult(context, outcome.get());
                    }

                    if (noVisibleChangeStreak > NO_VISIBLE_CHANGE_LIMIT) {

                        log.warn("Mission stagnated: {} actions in a row produced no observable change; stopping early.",
                                noVisibleChangeStreak);

                        state.addFinding(new Finding(
                                FindingSeverity.MEDIUM,
                                "Exploration stagnated: " + noVisibleChangeStreak + " actions in a row produced no "
                                        + "observable change on the page, so the mission was stopped early instead "
                                        + "of exhausting the full iteration budget.",
                                currentUrlOrUnknown(context),
                                Instant.now()
                        ));

                        return new MissionResult(context, MissionStatus.PARTIAL);
                    }

                    // Ask planner for the next action
                    Action action = planner.plan(context);

                    // Planner indicates mission is complete
                    if (action.type() == ActionType.COMPLETE) {

                        log.info("Planner marked mission as complete.");

                        return new MissionResult(context, MissionStatus.SUCCESS);
                    }

                    // Stagnation backstop: the same exact action chosen
                    // STAGNATION_LIMIT times in a row means nothing new is
                    // happening — stop here instead of burning the rest of
                    // the iteration budget repeating it (see field Javadoc).
                    String actionKey = action.type() + "|" + action.target();

                    if (actionKey.equals(stagnantActionKey)) {
                        stagnantActionRepeatCount++;
                    } else {
                        stagnantActionKey = actionKey;
                        stagnantActionRepeatCount = 1;
                    }

                    if (stagnantActionRepeatCount > STAGNATION_LIMIT) {

                        log.warn("Mission stagnated: '{}' was selected {} times in a row with no new progress; stopping early.",
                                actionKey, stagnantActionRepeatCount);

                        state.addFinding(new Finding(
                                FindingSeverity.MEDIUM,
                                "Exploration stagnated: '" + actionKey + "' was selected " + stagnantActionRepeatCount
                                        + " times in a row without making progress, so the mission was stopped early "
                                        + "instead of exhausting the full iteration budget.",
                                currentUrlOrUnknown(context),
                                Instant.now()
                        ));

                        return new MissionResult(context, MissionStatus.PARTIAL);
                    }

                    recentActionKeys.addLast(actionKey);
                    if (recentActionKeys.size() > OSCILLATION_WINDOW) {
                        recentActionKeys.removeFirst();
                    }

                    if (isOscillating(recentActionKeys)) {

                        log.warn("Mission stagnated: actions {} are oscillating with no real progress; stopping early.",
                                recentActionKeys);

                        state.addFinding(new Finding(
                                FindingSeverity.MEDIUM,
                                "Exploration stagnated: the last " + OSCILLATION_WINDOW + " actions alternated "
                                        + "between the same two actions (" + recentActionKeys
                                        + ") without making progress, so the mission was stopped early instead of "
                                        + "exhausting the full iteration budget.",
                                currentUrlOrUnknown(context),
                                Instant.now()
                        ));

                        return new MissionResult(context, MissionStatus.PARTIAL);
                    }

                    // Record the action
                    state.addAction(action);

                    // Execute the action, recording what happened as an
                    // Experience for the learning pipeline (Phase 2) — this
                    // is about THIS specific action's own outcome, separate
                    // from the broader per-iteration resilience below. On
                    // failure, record ERROR and rethrow so the existing
                    // outer catch's Finding-recording/iteration-advancing
                    // behavior runs exactly as before.
                    Instant executionStart = Instant.now();
                    CandidateAction executedCandidate =
                            new CandidateAction(action, action.confidence(), action.reasoning());

                    try {

                        executor.execute(action, context);

                        experienceRecorder.record(
                                context,
                                currentObservation,
                                executedCandidate,
                                ExperienceOutcome.SUCCESS,
                                Duration.between(executionStart, Instant.now())
                        );

                    } catch (Exception e) {

                        experienceRecorder.record(
                                context,
                                currentObservation,
                                executedCandidate,
                                ExperienceOutcome.ERROR,
                                Duration.between(executionStart, Instant.now())
                        );

                        throw e;
                    }

                    // Remember the action so it is not re-selected next iteration
                    // from this same state (see ExecutionMemory / AlreadyExecutedCandidateFilter)
                    memory.remember(StateSignature.of(currentObservation), action);

                    // Update execution state
                    state.incrementIteration();

                    log.info("Iteration {} complete", state.getIteration());

                } catch (Exception e) {

                    // A single bad step (a stray navigation, a locator
                    // resolving to nothing, an unexpected page state) must
                    // not end the whole mission — exploratory testing runs
                    // against real, messy sites where this is expected,
                    // not exceptional. Record it and keep going;
                    // incrementIteration guarantees the loop still
                    // terminates even if the same failure repeats.
                    log.error("Unhandled error during iteration {}: {}",
                            state.getIteration(), e.toString(), e);

                    state.addFinding(new Finding(
                            FindingSeverity.HIGH,
                            "Engine error: " + e,
                            currentUrlOrUnknown(context),
                            Instant.now()
                    ));

                    state.incrementIteration();
                }
            }

        } finally {
            browser.close();
        }

        log.warn("Mission failed: iteration limit reached without reaching the goal.");

        return new MissionResult(
                context,
                MissionStatus.FAILED
        );
    }

    private String currentUrlOrUnknown(MissionContext context) {

        Observation observation = context.getExecutionState().getCurrentObservation();

        return observation != null ? observation.url() : "unknown";
    }

    /**
     * True once {@code recentActionKeys} is full ({@link #OSCILLATION_WINDOW}
     * entries) and forms a strict period-2 pattern: A, B, A, B, ... with
     * A != B. Anything less structured (three or more distinct actions
     * rotating, or genuine forward progress) does not match.
     */
    private boolean isOscillating(Deque<String> recentActionKeys) {

        if (recentActionKeys.size() < OSCILLATION_WINDOW) {
            return false;
        }

        List<String> keys = List.copyOf(recentActionKeys);

        String a = keys.get(keys.size() - 2);
        String b = keys.get(keys.size() - 1);

        if (a.equals(b)) {
            return false;
        }

        for (int i = 0; i < keys.size(); i++) {
            int distanceFromEnd = keys.size() - 1 - i;
            String expected = (distanceFromEnd % 2 == 0) ? b : a;
            if (!keys.get(i).equals(expected)) {
                return false;
            }
        }

        return true;
    }
}