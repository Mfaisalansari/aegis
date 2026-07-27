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
import com.aegis.core.anomaly.CompositeAnomalyDetector;
import com.aegis.core.browser.Browser;
import com.aegis.core.browser.BrowserConfig;
import com.aegis.core.browser.SignalRecorder;
import com.aegis.core.browser.playwright.PlaywrightBrowser;
import com.aegis.core.knowledge.InspectionConfig;
import com.aegis.core.knowledge.SignalLog;
import com.aegis.core.llm.OpenAiCompatibleChatClient;
import com.aegis.core.plugin.AuthenticatedSession;
import com.aegis.core.plugin.BrowserFactory;
import com.aegis.core.plugin.FindingRule;
import com.aegis.core.plugin.NamedActionScorer;
import com.aegis.core.plugin.NamedInputValueResolver;
import com.aegis.core.plugin.SessionProvider;
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
import com.aegis.core.observer.SignalCapturingObserver;
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
import com.aegis.model.mission.Mission;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;

public final class EngineFactory {

    private EngineFactory() {
    }

    /**
     * What create() hands back: the engine to run a mission with, the
     * ExperienceRepository that engine's own LearningEngine writes to
     * during the run (needed by Reporting v2's Mission Timeline and
     * Learning summary), a way to read back every screenshot
     * SelfHealingBrowser captured during the run (needed by the Mission
     * Timeline's screenshot hooks), and a way to read back everything the
     * Page Inspection Layer's SignalRecorder captured live (console/
     * network signals, DOM snapshots). All four are read *after* the
     * mission finishes — create() itself runs before execute() is even
     * called, so at this point the repository is still empty and nothing
     * has been captured yet. Nothing about how the engine itself behaves
     * changes; reporting only reads what already happened.
     */
    public record CreatedEngine(
            MissionEngine engine,
            ExperienceRepository experienceRepository,
            Supplier<List<ScreenshotSample>> screenshots,
            Supplier<SignalLog> signals) {
    }

    public static CreatedEngine create() {
        return create(BrowserConfig.defaults());
    }

    public static CreatedEngine create(BrowserConfig browserConfig) {
        return create(null, browserConfig);
    }

    /**
     * Mission-aware overload — needed so {@link SessionProvider} plugins
     * (Stage 2 "Identity Integration") have a Mission to inspect when
     * deciding whether/how to establish a session. {@code mission} may be
     * null (the 1-arg overloads above pass null): session-provider
     * lookup is simply skipped then, everything else is unaffected.
     */
    public static CreatedEngine create(Mission mission, BrowserConfig browserConfig) {
        return create(mission, browserConfig, InspectionConfig.disabled());
    }

    /**
     * Same as the 2-arg overload, plus the Page Inspection Layer's live
     * capture — {@link InspectionConfig#disabled()} (what the 2-arg
     * overload above passes) means a {@link SignalRecorder} is still
     * attached for the cheap, passive console/network listeners (there's
     * no reason to gate those), but the DOM snapshot step — the only
     * per-state cost this layer adds — is skipped entirely.
     */
    public static CreatedEngine create(Mission mission, BrowserConfig browserConfig, InspectionConfig inspectionConfig) {

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
        SelfHealingBrowser selfHealingBrowser = new SelfHealingBrowser(resolveBrowser(browserConfig));
        Browser browser = selfHealingBrowser;
        browser.launch();

        /*
         * Identity Integration (Stage 2) — establishes a pre-authenticated
         * session, if any plugin provides one for this mission, before
         * anything else happens. First provider to return a non-empty
         * session wins; the plugin only ever hands back data (cookies/
         * storage/headers/profile) — applying it to the browser is
         * entirely AEGIS's own code (Browser.applySession), never the
         * plugin's. From here on, the autonomous engine takes over exactly
         * as if the site had been visited fresh and already logged in.
         *
         * Wrapped in try/catch (Stage 5 hardening): a discovered
         * SessionProvider is third-party plugin code, exactly the kind
         * most likely to throw. Without this, an already-launched browser
         * would leak its Playwright driver subprocess — DefaultMissionEngine
         * doesn't exist yet to guarantee close() at this point.
         *
         * close() itself is wrapped separately (code-review follow-up):
         * if closing the browser also throws, that must not replace the
         * original failure — the caller needs to know the SessionProvider
         * was the real root cause, not just that cleanup afterward failed
         * too. addSuppressed attaches the close failure without losing it.
         */
        try {
            if (mission != null) {
                for (SessionProvider provider : ServiceLoader.load(SessionProvider.class)) {
                    Optional<AuthenticatedSession> session = provider.createSession(mission);
                    if (session.isPresent()) {
                        browser.applySession(session.get());
                        break;
                    }
                }
            }
        } catch (RuntimeException e) {
            try {
                browser.close();
            } catch (RuntimeException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }

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
         * Page Inspection Layer — live capture
         *
         * The recorder is always attached: console/network listeners are
         * cheap and passive (nothing changes what the mission does), so
         * there's no reason to gate them. The DOM snapshot step is the one
         * genuinely added per-state cost, so *that* stays behind
         * inspectionConfig.captureDom() — SignalCapturingObserver only
         * wraps the observer when it's on; otherwise DefaultObserver runs
         * completely unwrapped, unchanged from before this layer existed.
         */
        SignalRecorder signalRecorder = new SignalRecorder();
        browser.attachSignalRecorder(signalRecorder);

        /*
         * Observer
         */
        Observer observer = new DefaultObserver(browser, visitedStateMemory);

        if (inspectionConfig.captureDom()) {
            observer = new SignalCapturingObserver(observer, browser, signalRecorder);
        }

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
        Map<String, InputValueResolver> inputStrategies = new LinkedHashMap<>(Map.of(
                "realistic", new DefaultInputValueResolver(),
                "edge-case", new EdgeCaseInputValueResolver()
        ));

        // Stage 2 "InputResolver" plugin — discovered resolvers register
        // under their own strategyName(), alongside the 2 built-ins.
        for (NamedInputValueResolver resolver : ServiceLoader.load(NamedInputValueResolver.class)) {
            inputStrategies.put(resolver.strategyName(), resolver);
        }

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
        List<CandidateActionGenerator> generators = new ArrayList<>(List.of(
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

        // Stage 2 "ActionProvider" plugin — no new interface needed,
        // CandidateActionGenerator was already exactly this shape.
        ServiceLoader.load(CandidateActionGenerator.class).forEach(generators::add);

        CandidateActionGenerator generator = new CompositeCandidateActionGenerator(generators);

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
        Map<String, ActionScorer> strategies = new LinkedHashMap<>(Map.ofEntries(
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
        ));

        // Stage 2 "MissionStrategy" plugin — discovered scorers register
        // under their own strategyName(), alongside the 10 built-ins.
        for (NamedActionScorer scorer : ServiceLoader.load(NamedActionScorer.class)) {
            strategies.put(scorer.strategyName(), scorer);
        }

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
         *
         * Always wrapped in CompositeAnomalyDetector (Stage 2 "Finding
         * Plugin") — with zero discovered FindingRule plugins this is
         * functionally identical to using BrowserSignalAnomalyDetector
         * directly, so there's no reason to special-case the "no plugins"
         * path.
         */
        List<FindingRule> findingRules = new ArrayList<>();
        ServiceLoader.load(FindingRule.class).forEach(findingRules::add);

        AnomalyDetector anomalyDetector =
                new CompositeAnomalyDetector(new BrowserSignalAnomalyDetector(browser), findingRules);

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

        return new CreatedEngine(engine, experienceRepository, selfHealingBrowser::capturedScreenshots, signalRecorder::toSignalLog);
    }

    /**
     * Stage 2 "Browser Plugin": the 3 built-in Playwright engines resolve
     * directly; anything else is looked up among discovered
     * {@link BrowserFactory} plugins by exact (case-insensitive)
     * {@link BrowserFactory#type()} match.
     */
    private static Browser resolveBrowser(BrowserConfig config) {

        String type = config.type().toLowerCase();

        if (type.equals("chromium") || type.equals("firefox") || type.equals("webkit")) {
            return new PlaywrightBrowser(config);
        }

        for (BrowserFactory factory : ServiceLoader.load(BrowserFactory.class)) {
            if (factory.type().equalsIgnoreCase(config.type())) {
                return factory.create(config);
            }
        }

        throw new IllegalArgumentException(
                "Unknown browser type: " + config.type()
                        + " (expected chromium, firefox, webkit, or a type registered by a BrowserFactory plugin)");
    }
}