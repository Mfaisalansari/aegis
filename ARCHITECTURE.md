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

**Browser** — the only component that ever touches Playwright directly (`page.click()`, `.fill()`, `.navigate()`, ...). Every other component, and every plugin, is one layer removed from the real browser — see "Why This Architecture?" below for why that boundary is deliberate and enforced.

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
