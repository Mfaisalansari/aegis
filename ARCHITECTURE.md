# Architecture

## Vision

AEGIS explores a web application the way a QA engineer with no test script would: it looks at what's on the page, reasons about which action is most likely to make progress toward the mission's goal, tries it, observes what changed, and repeats — learning from what worked and what didn't as it goes. It is deliberately *not* a scripted automation tool: there is no recorded sequence of clicks anywhere in this codebase for it to replay. Every mission run reasons about the live DOM in front of it.

## High-Level Architecture

```
Mission
  │
  ▼
MissionEngine ──▶ Observer ──▶ GoalEvaluator
  │                                  │
  │            (not yet reached)     │ (reached)
  ▼                                  ▼
Planner                          Mission Complete
  │
  ▼
DecisionEngine ──▶ GoalReasoner
  │
  ▼
CandidateGenerator ──▶ CandidateFilter ──▶ ActionScorer
  │
  ▼
Executor ──▶ Browser
  │
  └──▶ (loop back to Observer)
```

## Component Responsibilities

**Mission** — an immutable record of what to do: a name, a goal description, and a parameter map (`baseUrl`, `username`/`password`, `successUrlContains`, `explorationStrategy`, and more — see USAGE.md §4). It carries no logic; every other component reads from it, nothing writes back to it.

**MissionEngine** (`DefaultMissionEngine`) — the outer loop. Each iteration: observe, check the goal, generate/filter/score candidates, execute the winner, record what happened, repeat until the goal is reached or `maxIterations` is exhausted.

**Observer** — builds an `Observation` from the live page: every button, input, link, and select the `Browser` layer can currently see. This is AEGIS's only window into the DOM; anything the Observer can't see (behind an unopened modal, a tab never clicked) is invisible to every downstream component, which is why "coverage" in this project is always scoped to *what was observed*, never *the whole site* (see Phase 4/5 in `AEGIS_ROADMAP.md`).

**GoalEvaluator** — a small, fast check run every iteration ("does the current URL contain `successUrlContains`?") — cheap enough to call before doing any of the more expensive reasoning below.

**Planner** — produces the human-readable `MissionPlan` shown at the start of a run and in every report; advisory only; nothing in the reasoning path reads it back.

**DecisionEngine / GoalReasoner** — decide, given the current `Observation` and the mission's goal, what *kind* of progress is worth making this iteration (e.g. "the goal needs a login and no login form is visible yet — prioritize navigation" vs. "a form is visible — prioritize filling it").

**CandidateGenerator** (`CandidateActionGenerator` and its implementations) — turns the `Observation`'s raw elements into a list of candidate `Action`s (click this button, fill that input, ...). Stage 2 made this a *composite*: every built-in generator's output is merged with anything a discovered `CandidateActionGenerator` plugin contributes.

**CandidateFilter** — a chain of independent filters (`AlreadyExecutedCandidateFilter`, `KnownDeadEndCandidateFilter`, ...) that each get to veto a candidate before scoring — classic Chain of Responsibility, so a new exclusion rule is a new filter, not a change to an existing one.

**ActionScorer** (including `ActionScorerRegistry`) — assigns each surviving candidate a confidence score. Ten built-in strategies (`greedy`, `random`, `risk-based`, `breadth-first`, `depth-first`, `form-first`, `navigation-first`, `coverage-aware`, `adaptive`, `llm`) are selectable per mission via the `explorationStrategy` parameter; Stage 2 lets a `NamedActionScorer` plugin register an eleventh without touching this file.

**Executor** — carries out the winning candidate against the `Browser`, and is itself wrapped (Phase 9) by `SelfHealingBrowser`, which retries once and attempts locator healing before letting a failure propagate.

**ExecutionMemory / WorldModel** — in-run memory of what's been tried from which state and what it led to; this is what lets `KnownDeadEndCandidateFilter` prune a candidate whose every known destination is already visited, and what `CoverageAwareActionScorer` rewards a candidate for *not* being one of those.

**Browser** — the only component that ever touches Playwright directly (`page.click()`, `.fill()`, `.navigate()`, ...). Every other component, and every plugin, is one layer removed from the real browser — see "Why This Architecture?" below for why that boundary is deliberate and enforced. `PlaywrightBrowser` traverses every `<iframe>` on the page (via a `frame:N>`-prefixed locator scheme, invisible to every other component) as well as the top-level document, and its `page.locator(...)`-based element discovery incidentally pierces *open* Shadow DOM roots too, since that's Playwright's own CSS engine default. **Closed shadow roots stay permanently invisible** — that's a real browser security boundary, not an AEGIS limitation, and no browser automation tool (Playwright, Selenium, or otherwise) can see through it from outside. iframe support does not extend into shadow roots, or vice versa — they're separate boundaries, handled (or not) independently.

## Runtime Flow

A mission starts by navigating to `baseUrl`. Each iteration then repeats: **Observe** (build a fresh `Observation` of the current page) → **Check Goal** (if `successUrlContains` already matches, the mission ends `SUCCESS` right here; if `maxIterations` has been reached instead, it ends `FAILED`) → **Generate Candidates** (every visible interactive element becomes a candidate action) → **Filter Candidates** (drop anything already tried from this state, or confirmed to lead nowhere new) → **Score** (rank what's left by confidence, adjusted by what's been learned this mission) → **Execute** (carry out the top-scoring candidate, self-healing if it fails) → **Observe Again**, closing the loop. This is the same loop for every exploration strategy and every mission — strategies differ only in how `ActionScorer` ranks candidates, not in the loop's shape.

## Design Principles

**Single Responsibility** — each component above does exactly one job (observe, filter, score, execute, ...); a change to how candidates are *scored* never requires touching how they're *generated* or *filtered*.

**Open/Closed Principle** — Stage 2's plugin architecture is this principle made concrete: `com.aegis.core.plugin`'s interfaces (`FindingRule`, `ReportRenderer`, `NamedActionScorer`, `BrowserFactory`, `SessionProvider`, `CredentialProvider`) let a consumer add real capability by implementing an interface and registering it via `META-INF/services/<interface>` (Java's `ServiceLoader`) — zero edits to `aegis-core` itself. See USAGE.md §8b and the Extension Points section below.

**Chain of Responsibility** — `CandidateFilter` implementations are applied in sequence, each free to veto a candidate independently; adding a new exclusion rule (a new `CandidateFilter`) never means editing an existing one.

**Pipeline Pattern** — Observe → Reason → Filter → Score → Execute is a strict one-directional pipeline; no stage reaches backward into an earlier one's internals.

**Factory Pattern** — `EngineFactory` is the single place that wires every component above into a working `MissionEngine` for a given `Mission`/`BrowserConfig`; nothing else in the codebase constructs these components directly.

**Dependency Injection** — every component depends on interfaces (`Browser`, `ActionScorer`, `LlmChatClient`, ...), constructed and passed in by `EngineFactory`, not looked up or instantiated internally — which is exactly what makes `SelfHealingBrowser` (Phase 9) and the Stage 2 plugin composites possible without touching the components that consume them.

## Why This Architecture?

**Traditional automation** (a Selenium/Playwright test script) encodes a fixed sequence of steps: click this id, fill that field, assert this text. It's fast and precise for the exact page it was written against, and breaks the moment that page changes in a way the author didn't anticipate — a renamed id, a reordered form, an extra confirmation dialog.

**AEGIS** encodes a *goal*, not a sequence. It looks at whatever the page actually shows right now, reasons about what to do next, and adapts within the mission if something doesn't go as expected (Phase 9's self-healing, `AdaptiveActionScorer` switching strategy mid-mission when it gets stuck). The tradeoff is real: AEGIS is slower and less deterministic than a hand-written script, and it can't guarantee it will find the *same* path through an app twice. What it buys instead is resilience to exactly the kind of incidental UI change that breaks scripted tests, and the ability to point it at an application nobody has written a single test line for yet and get a real exploratory pass.

This is also why plugins are deliberately data-only where they touch execution: a `SessionProvider` can hand AEGIS a pre-authenticated session, but it can never call `page.click()` itself (see USAGE.md §8b). Letting a plugin drive the browser directly would reintroduce exactly the scripted-automation model this architecture exists to avoid.

## Extension Points

Stage 2 ("Plugin Architecture") added `ServiceLoader`-discovered extension points without touching any component listed above. The full interface reference — what each one is for, its exact signature, and how it's selected — lives in USAGE.md §8b and `API_REFERENCE.md`; the short version: `FindingRule`/`ReportRenderer` add new detection/output without new interfaces to invent, `NamedActionScorer`/`NamedInputValueResolver` add new named strategies to the existing registries, `BrowserFactory` swaps the browser implementation, and `SessionProvider`/`CredentialProvider` handle identity — the two extension points explicitly forbidden from ever calling into the browser directly.

## Knowledge Enrichment Layer

A permanent architectural layer, not a one-off feature — sits between the raw facts AEGIS discovers and everything that eventually consumes them:

```
Explorer → WorldModel (Facts) → Knowledge Enrichment Layer → Consumers
                                    ├── State Catalog (facts)
                                    ├── Node Catalog (facts + naming)
                                    ├── Flow Catalog
                                    ├── Journey Catalog
                                    ├── UX Quality Catalog (backtracking, journey divergence,
                                    │   navigation friction, structural accessible-name signal)
                                    ├── Page Inspection Catalog (console errors, network failures,
                                    │   broken links, contrast/accessible-name/size/overflow checks)
                                    ├── Risk/Business Metadata, Requirements, Defects, Historical Learning (future)
                                    └── (Reports, Test Planner, Impact Analyzer, Recommendation Engine, AI Assistant)
```

**Governing principle**: *discovery is AEGIS's responsibility, business meaning is the organization's responsibility.* AEGIS auto-generates a mechanical default name for every discovered state (from the real page title, falling back to the URL) so nothing is ever unnamed — but real business meaning (this is the "Policy Creation" flow, this sequence is the "New Customer Onboarding" journey) is always organization-declared in `knowledge.yml`, never fabricated by AEGIS. Every node is tagged with a `NameSource` (`CONFIGURED`/`AUTO_TITLE`/`AUTO_URL`) so a reader can always tell which is which. The same discipline extends to the two quality-analysis catalogs below: they report facts (a backtrack happened, a console error fired) and only render a judgment call ("too many steps") against an organization-declared expectation, never an invented one.

**Fully additive, zero changes to any frozen component** — `com.aegis.core.knowledge` is built entirely from `ExecutionState`'s raw `Observation`/`Action` history (the same source `MissionReportData` itself already independently reads to build its own `states`/`edges`), never from the live `WorldModel` object. `WorldModel`, `NavigationEdge`, `StateSignature`, `Observer`, `AnomalyDetector`, and the 3 report generators remain completely untouched. The Page Inspection Layer's live capture (see below) needed genuinely new signals `Observation`/`ElementInfo` were never designed to carry (console output, network status, computed style); rather than touch the frozen `Observer`, capture is added by *wrapping* it — `SignalCapturingObserver` delegates to the real `Observer` first, then records — the same wrap-not-modify pattern `CompositeAnomalyDetector` already established for the frozen `AnomalyDetector`.

**Extensible by design, not by promise**: `KnowledgeBase` is a type-safe registry (`Map<Class<? extends KnowledgeCatalog>, KnowledgeCatalog>`), not a fixed-field record — every future catalog (risk metadata, business metadata, requirements, defects, historical learning) slots in as a new `KnowledgeProvider` without ever touching `KnowledgeBase` itself. The 6 built-in catalogs (state/node/flow/journey/ux-quality/page-inspection) are implemented as `KnowledgeProvider`s too, no special-casing, so the same `ServiceLoader`-based extension mechanism a third party would use is proven by AEGIS's own built-ins — identical discovery pattern to Stage 2's plugins.

- **State Catalog** — pure facts: every distinct discovered state (`StateSignature`), a per-run label ("S1", "S2", ... by first-discovery order), url, page title, element count.
- **Node Catalog** — the same states, dressed with names: a stable cross-run `key` (config-declared or URL-derived), `displayName`/`technicalName`/`aliases`, all with a visible `NameSource`.
- **Flow Catalog** — declared business-capability groupings of node keys (a *definition*, no runtime data).
- **Journey Catalog** — declared, ordered reference paths (`JourneyDefinition`) *plus* what actually happened this run (`Journey`, an execution instance — the real, observed sequence of distinct nodes visited, checked against each definition as a subsequence match). AEGIS never segments one run into multiple sub-journeys or guesses at flow/journey groupings from graph structure — that would mean inventing business intent it doesn't have; both are explicit future "recommend and assist" work, not this layer's job today.
- **UX Quality Catalog** — an opinion on how good the navigation experience was, built purely from the catalogs above plus the raw observation/action history: backtracking (a state revisited, with immediate A→B→A ping-pong flagged worse than a distant revisit), journey divergence (for a declared journey that wasn't followed — *which* nodes were missing vs. out of order, not just a yes/no), navigation friction (only when the organization declared an `expectedMaxSteps` for a journey), and a structural accessible-name signal (an interactive element with blank text/name/id — honestly a proxy, since `ElementInfo` carries no role/aria data).
- **Page Inspection Catalog** — per-page defect inspection: console errors and uncaught exceptions, network failures (4xx/5xx/failed requests), broken in-app links (opt-in active probe, post-run only, same-origin, rate-limited), and DOM-snapshot-based UI checks (real WCAG contrast-ratio math, a higher-fidelity accessible-name check than the UX Quality Catalog's proxy, zero-size/off-screen elements, text overflow). Needs signals no post-hoc catalog can recover on its own — see "live capture" below.

### Live capture (Page Inspection Layer only)

Every other catalog is post-hoc — built entirely from what's already recorded in `ExecutionState` after the mission finishes. Console output and network activity are ephemeral (gone the moment they fire), and computed style/bounding-box data was never captured at all — so the Page Inspection Catalog alone needs a live capture step *during* the mission:

- **`SignalRecorder`** (`com.aegis.core.browser`) accumulates signals over the run — owned by mission orchestration (`EngineFactory`/`Aegis.run()`), never stored on `ExecutionState`/`MissionContext`, so no frozen component needs a new field.
- **Console/network** — `Browser.attachSignalRecorder(SignalRecorder)`, a new additive interface method (default no-op; `PlaywrightBrowser` is the only real implementation) registers a second, independent set of Playwright listeners alongside the existing anomaly-signal ones. Always attached — passive and cheap, no reason to gate it.
- **DOM snapshots** — `SignalCapturingObserver` (wraps the frozen `Observer`, doesn't modify it) captures bounding box, computed style, and accessible name per element, only when `InspectionConfig.captureDom()` is on — the one genuinely added per-state cost this layer introduces.

`InspectionConfig` is a `knowledge.yml` sibling of `nodes:`/`flows:`/`journeys:` (same file, same loader, a different kind of declaration — operational tuning, not business meaning). Everything defaults to a safe, low-noise posture; broken-link probing in particular is off by default, runs only after the mission completes, and never during exploration.

See `API_REFERENCE.md` for exact signatures and `USAGE.md` for a worked `knowledge.yml` example.

## Web UI (`aegis-web`)

A thin presentation layer, not a new reasoning path — every mission it submits
flows through the exact same `aegis-api`/`Launcher` entry point a CLI or
hand-written `Main` class would use, on the JDK's built-in
`com.sun.net.httpserver.HttpServer` (no framework dependency), rendering
plain server-side HTML with inline CSS and no JavaScript anywhere except one
small, narrowly-scoped script for the parsing-progress overlay described
below.

- **`MissionJobStore`/`MissionJob`/`MissionExecutor`** — an in-memory,
  in-process job registry: submitting a mission from the browser hands it to
  a background executor and returns immediately, so the request thread never
  blocks on a multi-second/multi-minute mission run; `MissionJob` tracks
  status (`RUNNING`/`DONE`/`ERROR`) for the dashboard and status pages to
  poll via page reloads. Not persisted — restarting the process forgets
  every prior job, same in-run-only scoping discipline as `ExecutionMemory`
  (see Memory Scope below).
- **`DashboardHandler`/`DashboardView`/`DashboardStats`** (`GET /`) — the
  landing page: stat tiles (total runs, pass rate, average duration, active
  count) computed by the pure `DashboardStats.compute(...)`, and a list of
  every submitted job linking to its own status page.
- **`RunHandler`/`MissionFormView`/`MissionRequestMapper`** (`GET`/`POST
  /run`) — the structured mission form and its validate-then-submit path.
- **`NaturalLanguageHandler`** (`POST /run/parse`) — the one place this
  module calls back into `aegis-core` directly: builds a `LlmMissionParser`
  over `OpenAiCompatibleChatClient.fromEnvironment()` and re-renders the
  structured form pre-filled from its output, so a user reviews and can edit
  every inferred field before anything actually runs — parsing is always a
  review step, never a direct trigger to execute.
- **`MissionsHandler`/`MissionStatusView`** (`GET /missions/{id}`) — a single
  job's live status and, once finished, a reasoning-step timeline reconstructed
  from the mission's own advisory `MissionPlan`.

## Memory Scope

Memory (`ExecutionMemory`, `VisitedStateMemory`, `WorldModel`) is in-run only, not persisted across separate runs.

Two reasons:

- Missions get a random UUID per launch today — there's no stable identity to key persisted state on.
- Persistence should be designed for a real consumer (an LLM reasoner that could use cross-run history) rather than built speculatively ahead of one.

## Future AI Extensions

These are ideas this architecture leaves room for, not commitments or in-progress work:

- **Memory** — a real cross-run store, once there's a stable mission identity to key it on (see above).
- **LLM** — already real, not just future: Phase 8 shipped `LlmActionScorer`, `LlmBugExplainer`, `LlmRecommendationEngine`, `LlmMissionParser`, `LlmMissionPlanner`, all opt-in and provider-agnostic (any OpenAI-compatible endpoint). What's still future is deeper use — e.g. an LLM-backed `CandidateFilter` or `Observer`.
- **RL** — the `Experience`/`LearningEngine` pipeline (Phase 2/3) is a simple heuristic-adjustment scheme, not reinforcement learning; a real RL policy could replace `HeuristicCandidateConfidenceEstimator`'s adjustment logic without changing anything upstream or downstream of it.
- **Knowledge Graph** — `WorldModel`'s `NavigationEdge` records are already graph-shaped; a persisted, cross-mission version of that graph is the natural next step if cross-run memory is ever built.
- **Planning** — `MissionPlanner` (Phase 8) is deliberately advisory-only today (see its `AEGIS_ROADMAP.md` entry for why); a planner whose output actually constrains `GoalReasoner` would be a real architecture change, not an additive one, and hasn't been attempted.
