package com.aegis.core.engine;

import com.aegis.core.anomaly.AnomalyDetector;
import com.aegis.core.browser.Browser;
import com.aegis.core.controller.MissionController;
import com.aegis.core.executor.Executor;
import com.aegis.core.goal.GoalEvaluator;
import com.aegis.core.observer.Observer;
import com.aegis.core.planner.Planner;
import com.aegis.core.reasoning.memory.ExecutionMemory;
import com.aegis.core.world.WorldModel;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.ExecutionState;
import com.aegis.model.context.MissionContext;
import com.aegis.model.finding.Finding;
import com.aegis.model.finding.FindingSeverity;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionResult;
import com.aegis.model.mission.MissionStatus;
import com.aegis.model.observation.Observation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class DefaultMissionEngine implements MissionEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultMissionEngine.class);

    private final Browser browser;
    private final Observer observer;
    private final Planner planner;
    private final Executor executor;
    private final MissionController controller;
    private final ExecutionMemory memory;
    private final GoalEvaluator goalEvaluator;
    private final AnomalyDetector anomalyDetector;
    private final WorldModel worldModel;

    public DefaultMissionEngine(
            Browser browser,
            Observer observer,
            Planner planner,
            Executor executor,
            MissionController controller,
            ExecutionMemory memory,
            GoalEvaluator goalEvaluator,
            AnomalyDetector anomalyDetector,
            WorldModel worldModel) {

        this.browser = browser;
        this.observer = observer;
        this.planner = planner;
        this.executor = executor;
        this.controller = controller;
        this.memory = memory;
        this.goalEvaluator = goalEvaluator;
        this.anomalyDetector = anomalyDetector;
        this.worldModel = worldModel;
    }

    @Override
    public MissionResult execute(Mission mission) {

        log.info("Mission started: {}", mission.name());

        MissionContext context = new MissionContext(mission);
        ExecutionState state = context.getExecutionState();

        /*
         * Navigate to the mission start page.
         */
        String startUrl = mission.parameter("baseUrl");

        if (startUrl != null && !startUrl.isBlank()) {
            browser.navigate(startUrl);
        }

        Observation previousObservation = null;

        try {

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

                    // Ask planner for the next action
                    Action action = planner.plan(context);

                    // Planner indicates mission is complete
                    if (action.type() == ActionType.COMPLETE) {

                        log.info("Planner marked mission as complete.");

                        return new MissionResult(context, MissionStatus.SUCCESS);
                    }

                    // Record the action
                    state.addAction(action);

                    // Execute the action
                    executor.execute(action, context);

                    // Remember the action so it is not re-selected next iteration
                    memory.remember(action);

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
}