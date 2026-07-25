package com.aegis.core.engine;

import com.aegis.core.action.executor.ActionExecutor;
import com.aegis.core.action.handler.ActionHandler;
import com.aegis.core.action.handler.BackActionHandler;
import com.aegis.core.action.handler.ClickActionHandler;
import com.aegis.core.action.handler.DoubleClickActionHandler;
import com.aegis.core.action.handler.NavigateActionHandler;
import com.aegis.core.action.handler.RaceClickActionHandler;
import com.aegis.core.action.handler.RefreshActionHandler;
import com.aegis.core.action.handler.ScrollActionHandler;
import com.aegis.core.action.handler.SelectActionHandler;
import com.aegis.core.action.handler.TypeActionHandler;
import com.aegis.core.action.handler.WaitActionHandler;
import com.aegis.core.anomaly.AnomalyDetector;
import com.aegis.core.anomaly.BrowserSignalAnomalyDetector;
import com.aegis.core.browser.Browser;
import com.aegis.core.browser.BrowserConfig;
import com.aegis.core.browser.playwright.PlaywrightBrowser;
import com.aegis.core.llm.OpenAiCompatibleChatClient;
import com.aegis.core.resilience.ScreenshotSample;
import com.aegis.core.resilience.SelfHealingBrowser;
import com.aegis.core.controller.DefaultMissionController;
import com.aegis.core.decision.DecisionEngine;
import com.aegis.core.decision.RuleBasedDecisionEngine;
import com.aegis.core.executor.Executor;
import com.aegis.core.goal.GoalEvaluator;
import com.aegis.core.goal.UrlContainsGoalEvaluator;
import com.aegis.core.impl.observer.DefaultObserver;
import com.aegis.core.impl.planner.MockPlanner;
import com.aegis.core.observer.Observer;
import com.aegis.core.planner.Planner;
import com.aegis.core.reasoning.GoalReasoner;
import com.aegis.core.reasoning.RuleBasedGoalReasoner;
import com.aegis.core.reasoning.confidence.CandidateConfidenceEstimator;
import com.aegis.core.reasoning.confidence.HeuristicCandidateConfidenceEstimator;
import com.aegis.core.reasoning.experience.DefaultExperienceRecorder;
import com.aegis.core.reasoning.experience.ExperienceRecorder;
import com.aegis.core.reasoning.experience.ExperienceRepository;
import com.aegis.core.reasoning.experience.InMemoryExperienceRepository;
import com.aegis.core.reasoning.factory.ActionFactory;
import com.aegis.core.reasoning.factory.DefaultActionFactory;
import com.aegis.core.reasoning.filter.AlreadyExecutedCandidateFilter;
import com.aegis.core.reasoning.filter.AlreadyFilledInputCandidateFilter;
import com.aegis.core.reasoning.filter.CandidateFilter;
import com.aegis.core.reasoning.filter.CompositeCandidateFilter;
import com.aegis.core.reasoning.filter.KnownDeadEndCandidateFilter;
import com.aegis.core.reasoning.generator.CandidateActionGenerator;
import com.aegis.core.reasoning.generator.CompositeCandidateActionGenerator;
import com.aegis.core.reasoning.generator.DoubleClickCandidateActionGenerator;
import com.aegis.core.reasoning.generator.GenericCandidateActionGenerator;
import com.aegis.core.reasoning.generator.PageLevelCandidateActionGenerator;
import com.aegis.core.reasoning.generator.RaceClickCandidateActionGenerator;
import com.aegis.core.reasoning.learning.DefaultLearningEngine;
import com.aegis.core.reasoning.learning.DefaultPatternAnalyzer;
import com.aegis.core.reasoning.learning.LearningEngine;
import com.aegis.core.reasoning.learning.PatternAnalyzer;
import com.aegis.core.reasoning.mapper.DefaultElementActionMapper;
import com.aegis.core.reasoning.mapper.ElementActionMapper;
import com.aegis.core.reasoning.memory.ExecutionMemory;
import com.aegis.core.reasoning.memory.VisitedStateMemory;
import com.aegis.core.reasoning.scorer.ActionScorer;
import com.aegis.core.reasoning.scorer.ActionScorerRegistry;
import com.aegis.core.reasoning.scorer.AdaptiveActionScorer;
import com.aegis.core.reasoning.scorer.BreadthFirstActionScorer;
import com.aegis.core.reasoning.scorer.CoverageAwareActionScorer;
import com.aegis.core.reasoning.scorer.DepthFirstActionScorer;
import com.aegis.core.reasoning.scorer.FormFirstActionScorer;
import com.aegis.core.reasoning.scorer.HighestConfidenceActionScorer;
import com.aegis.core.reasoning.scorer.LlmActionScorer;
import com.aegis.core.reasoning.scorer.NavigationFirstActionScorer;
import com.aegis.core.reasoning.scorer.RandomActionScorer;
import com.aegis.core.reasoning.scorer.RiskBasedActionScorer;
import com.aegis.core.reasoning.value.DefaultInputValueResolver;
import com.aegis.core.reasoning.value.EdgeCaseInputValueResolver;
import com.aegis.core.reasoning.value.InputValueResolver;
import com.aegis.core.reasoning.value.InputValueResolverRegistry;
import com.aegis.core.world.WorldModel;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class EngineFactory {

    private EngineFactory() {
    }

    /**
     * What create() hands back: the engine to run a mission with, the
     * ExperienceRepository that engine's own LearningEngine writes to
     * during the run (needed by Reporting v2's Mission Timeline and
     * Learning summary), and a way to read back every screenshot
     * SelfHealingBrowser captured during the run (needed by the Mission
     * Timeline's screenshot hooks). All three are read *after* the
     * mission finishes — create() itself runs before execute() is even
     * called, so at this point the repository is still empty and no
     * screenshots have been taken yet. Nothing about how the engine
     * itself behaves changes; reporting only reads what already
     * happened.
     */
    public record CreatedEngine(
            MissionEngine engine,
            ExperienceRepository experienceRepository,
            Supplier<List<ScreenshotSample>> screenshots) {
    }

    public static CreatedEngine create() {
        return create(BrowserConfig.defaults());
    }

    public static CreatedEngine create(BrowserConfig browserConfig) {

        /*
         * Browser
         *
         * Wrapped in SelfHealingBrowser (Phase 9) so every downstream
         * consumer below — Observer, the action handlers, AnomalyDetector,
         * DefaultMissionEngine itself — gets retry/locator-healing/
         * navigation-recovery for free, with zero changes to any of them:
         * Browser's contract (complete, or throw) is unchanged, only how
         * often it throws.
         */
        SelfHealingBrowser selfHealingBrowser = new SelfHealingBrowser(new PlaywrightBrowser(browserConfig));
        Browser browser = selfHealingBrowser;
        browser.launch();

        /*
         * Visited-State Memory
         *
         * Tracks pages/states seen this run. In-run only, not persisted
         * across runs (see ARCHITECTURE.md). Feeds Observer's revisit
         * detection and the World Model's dead-end filter below.
         */
        VisitedStateMemory visitedStateMemory = new VisitedStateMemory();

        /*
         * World Model
         *
         * A navigation graph built as the mission runs: which action, from
         * which state, led to which other state. In-run only, same
         * reasoning as Memory. Tracks state transitions only — no dialog
         * or form modelling, since the Observer has no concept of either.
         */
        WorldModel worldModel = new WorldModel();

        /*
         * Observer
         */
        Observer observer = new DefaultObserver(browser, visitedStateMemory);

        /*
         * Generic Candidate Generation
         */
        ElementActionMapper mapper =
                new DefaultElementActionMapper();

        ActionFactory actionFactory =
                new DefaultActionFactory();

        /*
         * "realistic" (default) fills forms with values the app should
         * accept, so a mission needing a real login/registration to reach
         * its goal isn't disrupted. "edge-case" swaps in deliberately
         * malformed values instead, for a dedicated input-validation
         * stress run — selected per mission via "inputStrategy".
         */
        Map<String, InputValueResolver> inputStrategies = Map.of(
                "realistic", new DefaultInputValueResolver(),
                "edge-case", new EdgeCaseInputValueResolver()
        );

        InputValueResolverRegistry valueResolverRegistry =
                new InputValueResolverRegistry(inputStrategies);

        /*
         * Learning Framework (Phase 2)
         *
         * In-run only, same as VisitedStateMemory/WorldModel above: every
         * executed action's outcome is recorded as an Experience, grouped
         * by stable (type, target) identity (see ActionKey), and turned
         * into a per-action confidence adjustment that
         * HeuristicCandidateConfidenceEstimator applies on top of its
         * static heuristics. DefaultMissionEngine records into it via
         * experienceRecorder; the estimator reads back out of it via
         * learningEngine — both share this one repository instance so
         * within a single mission run, later iterations benefit from
         * earlier ones.
         */
        ExperienceRepository experienceRepository =
                new InMemoryExperienceRepository();

        PatternAnalyzer patternAnalyzer =
                new DefaultPatternAnalyzer();

        LearningEngine learningEngine =
                new DefaultLearningEngine(experienceRepository, patternAnalyzer);

        ExperienceRecorder experienceRecorder =
                new DefaultExperienceRecorder(experienceRepository);

        CandidateConfidenceEstimator confidenceEstimator =
                new HeuristicCandidateConfidenceEstimator(learningEngine);

        /*
         * Page-level candidates (REFRESH, BACK) aren't tied to any
         * element; double-click and race-click candidates are alternate
         * actions on elements CLICK already covers — all come from
         * separate generators merged in alongside the element-scoped
         * ones. All opt-in per mission ("interruptions", "doubleClicks",
         * "raceConditions") — see PageLevelCandidateActionGenerator /
         * DoubleClickCandidateActionGenerator / RaceClickCandidateActionGenerator.
         */
        CandidateActionGenerator generator =
                new CompositeCandidateActionGenerator(List.of(
                        new GenericCandidateActionGenerator(
                                mapper,
                                actionFactory,
                                valueResolverRegistry,
                                confidenceEstimator
                        ),
                        new PageLevelCandidateActionGenerator(),
                        new DoubleClickCandidateActionGenerator(mapper),
                        new RaceClickCandidateActionGenerator(mapper)
                ));

        /*
         * Execution Memory (already-executed actions)
         */
        ExecutionMemory memory = new ExecutionMemory();

        /*
         * Candidate Filter
         */
        CandidateFilter filter =
                new CompositeCandidateFilter(
                        List.of(
                                new AlreadyExecutedCandidateFilter(memory),
                                new AlreadyFilledInputCandidateFilter(),
                                new KnownDeadEndCandidateFilter(worldModel, visitedStateMemory)
                        )
                );

        /*
         * Exploration Strategies
         *
         * "greedy" is the default, selectable per mission via the
         * "explorationStrategy" parameter. "breadth-first", "depth-first",
         * "form-first", and "navigation-first" are still proxies based on
         * element tag (link vs local, or input/select/button ranking) —
         * making them graph-aware using the World Model above is a
         * follow-up, not bundled into this one.
         *
         * "coverage-aware" is graph-aware, unlike the four proxies above:
         * it prefers a candidate WorldModel history confirms leads
         * somewhere not yet visited (State Prioritization, Phase 4). A
         * candidate whose every known destination is already visited
         * never reaches it — KnownDeadEndCandidateFilter prunes those
         * first — so this only ever ranks "confirmed new" above "no
         * evidence either way", never below.
         *
         * "llm" is the first strategy backed by a real model instead of a
         * hand-written rule — see LlmActionScorer. Points at a local
         * server (Ollama-shaped defaults) unless AEGIS_LLM_* env vars
         * say otherwise, so trying it costs nothing but doesn't silently
         * call out to a paid API either.
         *
         * "adaptive" is the one strategy that switches mid-mission on its
         * own: it uses "greedy" normally, but falls back to
         * "coverage-aware" once 3 iterations have passed with no newly
         * discovered state (see AdaptiveActionScorer) — and switches back
         * the moment a new state turns up again. This is the Phase 4
         * "automatic dynamic strategy switching" goal; every other
         * strategy here is a fixed, manually-chosen-per-mission choice.
         */
        Map<String, ActionScorer> strategies = Map.ofEntries(
                Map.entry("greedy", new HighestConfidenceActionScorer()),
                Map.entry("random", new RandomActionScorer()),
                Map.entry("risk-based", new RiskBasedActionScorer()),
                Map.entry("breadth-first", new BreadthFirstActionScorer()),
                Map.entry("depth-first", new DepthFirstActionScorer()),
                Map.entry("form-first", new FormFirstActionScorer()),
                Map.entry("navigation-first", new NavigationFirstActionScorer()),
                Map.entry("coverage-aware", new CoverageAwareActionScorer(worldModel, visitedStateMemory)),
                Map.entry("llm", new LlmActionScorer(OpenAiCompatibleChatClient.fromEnvironment())),
                Map.entry("adaptive", new AdaptiveActionScorer(
                        new HighestConfidenceActionScorer(),
                        new CoverageAwareActionScorer(worldModel, visitedStateMemory)))
        );

        ActionScorerRegistry scorerRegistry =
                new ActionScorerRegistry(strategies);

        /*
         * Goal Reasoner
         */
        GoalReasoner goalReasoner =
                new RuleBasedGoalReasoner(
                        generator,
                        filter,
                        scorerRegistry
                );

        /*
         * Decision Engine
         */
        DecisionEngine decisionEngine =
                new RuleBasedDecisionEngine(
                        goalReasoner
                );

        /*
         * Planner
         */
        Planner planner =
                new MockPlanner(
                        decisionEngine
                );

        /*
         * Action Handlers
         */
        List<ActionHandler> handlers = List.of(
                new ClickActionHandler(browser),
                new DoubleClickActionHandler(browser),
                new RaceClickActionHandler(browser),
                new TypeActionHandler(browser),
                new SelectActionHandler(browser),
                new ScrollActionHandler(browser),
                new NavigateActionHandler(browser),
                new RefreshActionHandler(browser),
                new BackActionHandler(browser),
                new WaitActionHandler()
        );

        /*
         * Executor
         */
        Executor executor =
                new ActionExecutor(handlers);

        /*
         * Goal Evaluator
         */
        GoalEvaluator goalEvaluator =
                new UrlContainsGoalEvaluator();

        /*
         * Anomaly Detector
         */
        AnomalyDetector anomalyDetector =
                new BrowserSignalAnomalyDetector(browser);

        /*
         * Mission Engine
         */
        MissionEngine engine = new DefaultMissionEngine(
                browser,
                observer,
                planner,
                executor,
                new DefaultMissionController(),
                memory,
                goalEvaluator,
                anomalyDetector,
                worldModel,
                experienceRecorder
        );

        return new CreatedEngine(engine, experienceRepository, selfHealingBrowser::capturedScreenshots);
    }
}