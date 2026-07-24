package com.aegis.core.engine;

import com.aegis.core.anomaly.AnomalyDetector;
import com.aegis.core.browser.Browser;
import com.aegis.core.controller.DefaultMissionController;
import com.aegis.core.executor.Executor;
import com.aegis.core.goal.GoalEvaluator;
import com.aegis.core.observer.Observer;
import com.aegis.core.planner.Planner;
import com.aegis.core.reasoning.experience.ExperienceRecorder;
import com.aegis.core.reasoning.memory.ExecutionMemory;
import com.aegis.core.world.WorldModel;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.finding.Finding;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionResult;
import com.aegis.model.mission.MissionStatus;
import com.aegis.model.observation.AnomalySignal;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultMissionEngineTest {

    @Test
    void aThrowingStepIsRecordedAsAFindingInsteadOfCrashingTheMission() {

        AtomicInteger planCalls = new AtomicInteger();

        Planner planner = context -> {
            if (planCalls.getAndIncrement() == 0) {
                throw new RuntimeException("simulated locator failure");
            }
            return completeAction();
        };

        DefaultMissionEngine engine = new DefaultMissionEngine(
                noOpBrowser(),
                alwaysObserves(),
                planner,
                noOpExecutor(),
                new DefaultMissionController(),
                new ExecutionMemory(),
                neverReachesGoal(),
                noAnomalies(),
                new WorldModel(),
                noOpExperienceRecorder()
        );

        MissionResult result = engine.execute(mission());

        assertEquals(MissionStatus.SUCCESS, result.status());

        List<Finding> findings = result.context().getExecutionState().getFindings();

        assertEquals(1, findings.size());
        assertTrue(findings.get(0).summary().contains("simulated locator failure"));
    }

    @Test
    void aStepThatKeepsThrowingStillTerminatesAtTheIterationCap() {

        Planner planner = context -> {
            throw new RuntimeException("always fails");
        };

        DefaultMissionEngine engine = new DefaultMissionEngine(
                noOpBrowser(),
                alwaysObserves(),
                planner,
                noOpExecutor(),
                new DefaultMissionController(),
                new ExecutionMemory(),
                neverReachesGoal(),
                noAnomalies(),
                new WorldModel(),
                noOpExperienceRecorder()
        );

        MissionResult result = engine.execute(mission());

        assertEquals(MissionStatus.FAILED, result.status());
        assertEquals(10, result.context().getExecutionState().getFindings().size());
    }

    private Mission mission() {
        return new Mission(UUID.randomUUID(), "Test", "Test", Map.of());
    }

    private Action completeAction() {
        return new Action(
                UUID.randomUUID(), ActionType.COMPLETE, "", "", "test",
                1.0, "test", Duration.ofSeconds(5), Instant.now(), ""
        );
    }

    private Observer alwaysObserves() {
        return context -> context.getExecutionState().setCurrentObservation(
                new Observation(
                        "https://example.com", "Title",
                        List.of(), List.of(), List.of(), List.of(), List.of(),
                        Instant.now()
                )
        );
    }

    private GoalEvaluator neverReachesGoal() {
        return context -> Optional.empty();
    }

    private AnomalyDetector noAnomalies() {
        return context -> List.of();
    }

    private Executor noOpExecutor() {
        return (action, context) -> { };
    }

    private ExperienceRecorder noOpExperienceRecorder() {
        return (missionContext, observation, candidateAction, outcome, duration) -> { };
    }

    private Browser noOpBrowser() {
        return new Browser() {

            @Override public void launch() { }
            @Override public void close() { }
            @Override public void navigate(String url) { }
            @Override public void refresh() { throw new UnsupportedOperationException(); }
            @Override public void goBack() { throw new UnsupportedOperationException(); }
            @Override public void click(String locator) { throw new UnsupportedOperationException(); }
            @Override public void doubleClick(String locator) { throw new UnsupportedOperationException(); }
            @Override public void raceClick(String locator) { throw new UnsupportedOperationException(); }
            @Override public void type(String locator, String text) { throw new UnsupportedOperationException(); }
            @Override public void select(String locator) { throw new UnsupportedOperationException(); }
            @Override public void scrollTo(String locator) { throw new UnsupportedOperationException(); }
            @Override public String getPageTitle() { throw new UnsupportedOperationException(); }
            @Override public String getCurrentUrl() { throw new UnsupportedOperationException(); }
            @Override public List<ElementInfo> getButtons() { throw new UnsupportedOperationException(); }
            @Override public List<ElementInfo> getInputs() { throw new UnsupportedOperationException(); }
            @Override public List<ElementInfo> getLinks() { throw new UnsupportedOperationException(); }
            @Override public List<ElementInfo> getSelects() { throw new UnsupportedOperationException(); }
            @Override public List<AnomalySignal> drainAnomalies() { return List.of(); }
        };
    }
}
