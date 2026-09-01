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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultMissionEngineTest {

    // Stage 5 hardening regression test: an unreachable/invalid baseUrl
    // (a realistic, everyday failure) throwing out of the initial
    // navigate() call must still close the browser — see the try/finally
    // restructure in DefaultMissionEngine.execute().
    @Test
    void aThrowingInitialNavigateStillClosesTheBrowser() {

        AtomicInteger closeCalls = new AtomicInteger();

        Browser browser = new Browser() {
            @Override public void launch() { }
            @Override public void close() { closeCalls.incrementAndGet(); }
            @Override public void navigate(String url) { throw new RuntimeException("simulated unreachable host"); }
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

        DefaultMissionEngine engine = new DefaultMissionEngine(
                browser,
                alwaysObserves(),
                context -> completeAction(),
                noOpExecutor(),
                new DefaultMissionController(),
                new ExecutionMemory(),
                neverReachesGoal(),
                noAnomalies(),
                new WorldModel(),
                noOpExperienceRecorder()
        );

        Mission mission = new Mission(UUID.randomUUID(), "Test", "Test", Map.of("baseUrl", "https://unreachable.invalid/"));

        assertThrows(RuntimeException.class, () -> engine.execute(mission));

        assertEquals(1, closeCalls.get());
    }

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

        // A distinct observation per iteration (unlike the shared
        // alwaysObserves() fixture, which is intentionally static) — this
        // test guards the iteration-cap termination guarantee specifically,
        // not the no-visible-change stagnation backstop, so it must not
        // trip that backstop as an unrelated side effect of the fixture.
        DefaultMissionEngine engine = new DefaultMissionEngine(
                noOpBrowser(),
                observesADifferentPageEachCall(),
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

    // Regression test for the real-world stuck-in-a-refresh-loop bug: a
    // planner that keeps offering the exact same action (as
    // PageLevelCandidateActionGenerator + a tied HighestConfidenceActionScorer
    // did on a fully-explored page) must not be allowed to burn the full
    // iteration budget doing nothing. The engine's stagnation backstop
    // should end the mission early, well short of the 10-iteration
    // default cap, with MissionStatus.PARTIAL and a diagnostic Finding.
    @Test
    void repeatingTheExactSameActionStopsTheMissionEarlyInsteadOfExhaustingTheIterationBudget() {

        Planner planner = context -> refreshAction();

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

        MissionResult result = engine.execute(missionWithLargeIterationBudget());

        assertEquals(MissionStatus.PARTIAL, result.status());

        List<Finding> findings = result.context().getExecutionState().getFindings();

        assertEquals(1, findings.size());
        assertTrue(findings.get(0).summary().contains("stagnated"));

        // Stopped well short of the iteration cap.
        assertTrue(result.context().getExecutionState().getIteration() < 50);
    }

    @Test
    void aDifferentActionEachTimeNeverTripsTheStagnationBackstop() {

        AtomicInteger calls = new AtomicInteger();

        Planner planner = context -> clickAction("#el-" + calls.getAndIncrement());

        // Real exploration of distinct elements changes the observed page
        // each time (a new element appears, a value changes, etc.) — this
        // fixture reflects that, unlike the shared static alwaysObserves(),
        // so this test actually proves what its name claims: neither
        // stagnation backstop trips on genuine, varied exploration.
        DefaultMissionEngine engine = new DefaultMissionEngine(
                noOpBrowser(),
                observesADifferentPageEachCall(),
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
        assertEquals(10, result.context().getExecutionState().getIteration());
    }

    // Regression test for a second real-world stuck pattern, found by
    // live-testing the STAGNATION_LIMIT fix above against saucedemo.com:
    // several DISTINCT actions producing no visible change at all (every
    // field already holds those exact values). None repeats itself
    // back-to-back, so STAGNATION_LIMIT never trips; a plain two-action
    // alternation would also now trip the faster, StateSignature-independent
    // OSCILLATION_WINDOW backstop instead (see
    // twoDistinctActionsOscillatingWithVisibleChangeEachStepStillStopEarly),
    // so this uses a three-way rotation to isolate the no-visible-change
    // backstop specifically.
    @Test
    void severalDistinctActionsWithNoVisibleChangeStopTheMissionEarly() {

        AtomicInteger calls = new AtomicInteger();
        String[] targets = {"#user-name", "#password", "#login-button"};

        Planner planner = context -> clickAction(targets[calls.getAndIncrement() % targets.length]);

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

        MissionResult result = engine.execute(missionWithLargeIterationBudget());

        assertEquals(MissionStatus.PARTIAL, result.status());
        assertTrue(result.context().getExecutionState().getIteration() < 50);

        List<Finding> findings = result.context().getExecutionState().getFindings();

        assertEquals(1, findings.size());
        assertTrue(findings.get(0).summary().contains("no observable change"));
    }

    // Regression test for the exact pattern in a real mission report: after
    // logging back in, an unrelated element's locator on the login page
    // intermittently fell back to a positional selector (a submit button
    // sometimes resolving as [id='login-button'], sometimes as
    // :nth-match(button, 1), depending on transient DOM state around a
    // failed submit) — which flips the whole StateSignature and silently
    // resets ExecutionMemory's per-state "already typed here" tracking for
    // the completely unrelated username/password fields. Each retype IS a
    // real, visible change (blank -> filled -> blank), so
    // twoDistinctActionsOscillatingWithNoVisibleChangeStopTheMissionEarly's
    // backstop never trips either. Only a StateSignature-independent,
    // action-key cycle detector can catch this.
    @Test
    void twoDistinctActionsOscillatingWithVisibleChangeEachStepStillStopEarly() {

        AtomicInteger planCalls = new AtomicInteger();
        AtomicInteger observeCalls = new AtomicInteger();

        Planner planner = context -> planCalls.getAndIncrement() % 2 == 0
                ? typeAction("#user-name", "standard_user")
                : typeAction("#password", "secret_sauce");

        Observer changingObserver = context -> context.getExecutionState().setCurrentObservation(
                new Observation(
                        "https://www.saucedemo.com/", "Title",
                        List.of(), List.of(), List.of(), List.of(),
                        List.of(new ElementInfo(
                                "input", "", "", "", "text",
                                "value-" + observeCalls.getAndIncrement(), true, true, "#whatever")),
                        Instant.now()
                )
        );

        DefaultMissionEngine engine = new DefaultMissionEngine(
                noOpBrowser(),
                changingObserver,
                planner,
                noOpExecutor(),
                new DefaultMissionController(),
                new ExecutionMemory(),
                neverReachesGoal(),
                noAnomalies(),
                new WorldModel(),
                noOpExperienceRecorder()
        );

        MissionResult result = engine.execute(missionWithLargeIterationBudget());

        assertEquals(MissionStatus.PARTIAL, result.status());
        assertTrue(result.context().getExecutionState().getIteration() < 50);

        List<Finding> findings = result.context().getExecutionState().getFindings();

        assertEquals(1, findings.size());
        assertTrue(findings.get(0).summary().contains("alternated between the same two actions"));
    }

    private Action typeAction(String target, String value) {
        return new Action(
                UUID.randomUUID(), ActionType.TYPE, target, value, "test",
                0.5, "test", Duration.ofSeconds(5), Instant.now(), ""
        );
    }

    private Action refreshAction() {
        return new Action(
                UUID.randomUUID(), ActionType.REFRESH, "", "", "test",
                0.2, "test", Duration.ofSeconds(5), Instant.now(), ""
        );
    }

    private Action clickAction(String target) {
        return new Action(
                UUID.randomUUID(), ActionType.CLICK, target, "", "test",
                0.5, "test", Duration.ofSeconds(5), Instant.now(), ""
        );
    }

    private Mission mission() {
        return new Mission(UUID.randomUUID(), "Test", "Test", Map.of());
    }

    // The stagnation backstops (STAGNATION_LIMIT / NO_VISIBLE_CHANGE_LIMIT /
    // OSCILLATION_WINDOW) each require 20 repeats before tripping, so a
    // stagnation test needs a budget comfortably above that to prove the
    // backstop fires before the iteration cap would anyway.
    private Mission missionWithLargeIterationBudget() {
        return new Mission(UUID.randomUUID(), "Test", "Test", Map.of("maxIterations", "50"));
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

    private Observer observesADifferentPageEachCall() {

        AtomicInteger calls = new AtomicInteger();

        return context -> context.getExecutionState().setCurrentObservation(
                new Observation(
                        "https://example.com/" + calls.getAndIncrement(), "Title",
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
