# AEGIS - Autonomous Exploratory QA Agent

> Moving QA from deterministic automation to autonomous exploratory testing.

---

# Vision

Traditional test automation validates predefined user journeys using scripted test cases.

AEGIS aims to simulate how real users behave by continuously observing an application, reasoning about possible actions, selecting the most appropriate action, executing it, and learning from the results.

Instead of executing fixed scripts, AEGIS makes decisions at runtime.

---

# Current Architecture

```text
                    Mission
                       │
                       ▼
                Mission Engine
                       │
                       ▼
                  Observation
                       │
                       ▼
                 Goal Evaluator
                       │
                       ▼
                    Planner
                       │
                       ▼
                Decision Engine
                       │
                       ▼
                 Goal Reasoner
                       │
                       ▼
              Candidate Action Generator
                       │
                       ▼
                Candidate Filter
                       │
                       ▼
                  Action Scorer
                       │
                       ▼
               Selected Action
                       │
                       ▼
               Action Executor
                       │
                       ▼
             Playwright Browser
```

---

# Project Structure

```
aegis
│
├── aegis-model
│      Domain Models
│
├── aegis-core
│      AI Engine
│
└── aegis-launcher
       Entry Point
```

---

# Completed Features

## Sprint 1-5

### Browser Layer

Implemented Playwright wrapper.

Supports

- Launch browser
- Close browser
- Navigate
- Click
- Type
- Read page title
- Current URL
- DOM inspection

---

### Mission

Mission abstraction introduced.

```java
Mission

- id
- name
- description
- parameters
```

Mission parameters allow the engine to execute different missions without changing code.

Example

```
startUrl
username
password
```

---

### Observer

Observer inspects the current page.

Extracts

- Inputs
- Buttons
- Links

Produces structured observations instead of relying on hardcoded locators.

---

### Goal Evaluator

Checks the current observation against the mission's success condition after every observe step.

```
Mission parameter: successUrlContains

Current URL contains it → SUCCESS
```

Missions that don't declare a success condition never resolve through this evaluator.

Previously the Mission Engine always reported `SUCCESS` regardless of outcome. It now reports `FAILED` if the iteration limit is reached without the goal being met, and returns as soon as the goal is detected instead of continuing to act after the mission has already succeeded.

---

### Mission Engine

Responsible for

- Navigation
- Observation loop
- Planning
- Action execution
- Mission lifecycle

---

### Planner

Coordinates decision making.

Planner itself contains no business logic.

---

### Decision Engine

Acts as the orchestration layer between Planner and Goal Reasoner.

---

## Sprint 6-10

### Goal Reasoner

Introduced a dedicated reasoning layer.

Responsibilities

- Understand current application state
- Generate possible actions
- Return the best action

This separates reasoning from orchestration.

---

### Candidate Action Pipeline

The architecture was redesigned from

```
Observation
      │
      ▼
 Action
```

to

```
Observation
      │
      ▼
Generate Candidates
      │
      ▼
Score Candidates
      │
      ▼
Best Action
```

This is the first major step toward autonomous decision making.

---

### Candidate Action Generator

Instead of directly returning an Action, the engine now produces multiple possible actions.

Example

```
Enter Username
Enter Password
Click Login
Wait
```

Each candidate includes

- confidence
- reasoning

---

### Action Scorer

Introduced a scoring component.

Responsibilities

- Evaluate candidate actions
- Select the highest confidence action

Current implementation

```
Highest Confidence Wins
```

Confidence is no longer a flat constant. A `CandidateConfidenceEstimator` scores each candidate heuristically:

- Inputs that look like credential fields (name/id contains "user"/"pass") score higher than generic inputs
- Submit-like buttons only score high once the visible inputs are actually filled

Kept as a swappable component so the scorer has something real to compare once the estimator itself is upgraded.

Future implementations may use

- ML
- LLM
- Reinforcement Learning

without changing the rest of the architecture.

---

### Candidate Filter

Introduced a filtering stage between candidate generation and scoring.

Responsibilities

- Remove candidates for actions already executed this mission
- Remove TYPE candidates for inputs that are already filled

Implemented as a chain of independent filter rules, composed together, so new rules can be added without touching the reasoner.

```
Generate Candidates
      │
      ▼
Filter Candidates
      │
      ▼
Score Candidates
```

This is what stops the reasoner from repeating the same action (e.g. retyping the username) every iteration when several candidates tie on confidence.

---

### Action Executor

Executes the selected action using registered handlers.

Supports

- Click
- Type
- Wait

Uses handler pattern for extensibility.

---

### Anomaly Detector

Turns raw browser signals into `Finding`s each iteration — the first real step toward AEGIS being a QA agent rather than a form-filler.

Detects, via Playwright's own event listeners on the page

- Page crashes (`page.onCrash`) → `CRITICAL`
- Uncaught JS exceptions (`page.onPageError`) → `HIGH`
- Failed network requests (`page.onRequestFailed`) → `MEDIUM`
- Console errors (`page.onConsoleMessage`, type=`error`) → `MEDIUM`

Findings are logged (`WARN`) as they're detected, collected on `ExecutionState`, and listed in the explainability report. They don't affect `MissionStatus` — reaching the goal and finding defects are reported as two separate signals, same as a real test run can pass while still logging warnings.

Two things keep findings useful instead of noisy, both plain deterministic code (no AI needed for this part):

- **Deduplicated** — a repeating error is reported once per mission, not once per occurrence
- **Contextualized** — each finding notes the action that preceded it, e.g. `CONSOLE_ERROR: TypeError: ... (after CLICK button[type='submit'])`, so it's reproducible instead of a bare stack trace

Only classifies what the browser already reported — it doesn't infer problems from page content (e.g. it won't notice a wrong number displayed, only things the browser itself flags as broken). Explaining *why* something broke, or judging whether a given error actually matters, is left for the AI reasoner (Sprint 26) rather than faked with more heuristics now.

---

# Current Workflow

```
Mission

↓

Navigate

↓

Observe

↓

Generate Candidates

↓

Filter Candidates

↓

Score Candidates

↓

Execute Best Action

↓

Observe Again

↓

Repeat

↓

Mission Complete
```

---

# Current Capabilities

✅ Autonomous login execution

✅ Goal-based mission outcome detection (SUCCESS / FAILED)

✅ Runtime decision making

✅ Candidate action generation

✅ Candidate filtering

✅ Heuristic candidate scoring

✅ Pluggable exploration strategies (greedy / random / risk-based / breadth-first / depth-first / form-first / navigation-first)

✅ Pluggable input value strategies (realistic / edge-case injection)

✅ Opt-in interrupted-workflow simulation (page refresh / back-navigation mid-flow)

✅ Opt-in double-click / duplicate-submission stress testing

✅ Opt-in race-condition stress testing (two clicks fired with no wait between them)

✅ Native dialog (alert/confirm/prompt) detection — captured as a Finding instead of silently auto-dismissed

✅ Opt-in LLM-backed exploration strategy (`explorationStrategy: llm`) — the first real AI decision-maker in the pipeline, provider-agnostic, falls back to the rule-based default on any failure

✅ Explainability report — text and interactive HTML, both generated per mission

✅ In-run visited-page / visited-state memory

✅ Basic anomaly detection (console errors, uncaught exceptions, failed requests, crashes)

✅ World Model (navigation graph, used to filter proven dead-end actions)

✅ Mission lifecycle management

✅ Modular architecture

✅ Playwright integration

---

# Current Limitations

Current reasoning is heuristic, not learned.

Example

```java
if(name/id contains "user" or "pass")

↓

Score input higher
```

The engine understands element shape (input vs button, filled vs empty) but has no real semantic understanding of the page or the mission.

---

# Roadmap

## Sprint 19 — Completed

### Candidate Evaluation Dashboard

Every reasoning step (all candidates considered + the one selected, with confidence and reason) is recorded on `ExecutionState` via `ReasoningStep`. `MissionReportData` derives everything a report needs (visited pages, World Model states/transitions, findings, reasoning steps) once from that data, and two renderers sit on top of it:

- `ExplainabilityReportGenerator` — plain text, `reports/aegis-report-<timestamp>.txt`
- `HtmlExplainabilityReportGenerator` — a single self-contained HTML file, `reports/aegis-report-<timestamp>.html`, with:
  - Stat tiles (pages visited, states, transitions, findings)
  - The World Model rendered as an actual node-link graph (inline SVG, circular layout computed server-side, self-loops and parallel edges handled, hover a node to highlight its connected edges)
  - Findings as severity-colored cards
  - Reasoning steps as collapsible sections, selected candidate highlighted, confidence shown as a bar

No external libraries or CDN — everything (CSS, JS, graph rendering) is inline, so the file works fully offline. Kept separate from the live console log so the run itself stays quiet.

Both are generated every run, side by side — nothing is lost by adding the HTML one.

---

## Sprint 20 — Completed

### Generic Candidate Generation

Done. The hardcoded login-rule generator has been removed entirely.

```
Inspect every element

↓

Determine interesting actions

↓

Generate candidates
```

Candidate generation is driven by `ElementActionMapper` + `ActionFactory`, not per-mission rules.

---

## Sprint 21 — Completed

### Action Filter

Done. See [Candidate Filter](#candidate-filter) above.

Currently implemented rules

- Skip already-executed actions
- Skip already-filled inputs

Future rules can be added as additional filter implementations without changing the reasoner.

---

## Sprint 22 — Completed

### Exploration Strategy

Pluggable strategies, selectable per mission via the `explorationStrategy` parameter (`greedy`, `random`, `risk-based`, `breadth-first`, `depth-first`, `form-first`, `navigation-first`).

- ✅ Random Exploration — picks any candidate, ignoring confidence
- ✅ Risk Based Exploration — prioritizes candidates whose locator suggests a destructive/high-value action (delete, checkout, submit, pay, ...); falls back to least-confident when nothing looks risky
- ✅ Greedy (default) — the original highest-confidence behavior
- ✅ Breadth First Exploration — proxy: exhausts non-link (local) actions on the current page before following a link away from it
- ✅ Depth First Exploration — proxy: prefers following a link over local actions, diving deep rather than wide
- ✅ Form First Exploration — proxy: ranks candidates input > select > button before falling back to a link
- ✅ Navigation First Exploration — proxy: prefers following a link, same as Depth First today (see note below)

BFS/DFS/Form-First/Navigation-First are honestly proxies, not real graph search — `Action` now carries the source element's HTML tag (`elementTag`), and that's the only signal used to guess "does this navigate away" or "is this a form control." A real implementation needs an actual navigation graph — which pages/states an action leads to — and that's World Model (Sprint 25), not built yet. Faking a graph traversal without a graph would just be decorative, the same problem the flat confidence score had before it was fixed.

Navigation First and Depth First currently make the same choice (both prefer any link) because element tag is the only signal available — they're kept as separate strategies because they express different intents (traversal order vs. element category) and are expected to diverge once Depth First becomes graph-aware.

### Input Value Strategy

Pluggable, same shape as Exploration Strategy — selectable per mission via the `inputStrategy` parameter (`realistic` default, `edge-case`).

- ✅ Realistic (default) — `DefaultInputValueResolver`, fills forms with values the app should accept
- ✅ Edge-Case — `EdgeCaseInputValueResolver`, deliberately returns malformed values instead: empty, oversized (5000 chars), XSS/SQL-injection-shaped, malformed emails, unicode, whitespace-only. Selection per field is deterministic (derived from the field's id/name), not random or call-order dependent, so the same field always gets the same edge case on identical runs.

Verified live against demowebshop.tricentis.com/register: fed `LastName` a SQL-injection-shaped string, `Email` a malformed address, and mismatched `Password`/`ConfirmPassword` — the site correctly refused registration (stayed on `/register`, no redirect to `registerresult`). A mission run with `inputStrategy: edge-case` should be read the opposite way from a normal one: FAILED means validation held (the healthy outcome); SUCCESS means malformed data got accepted, which is itself the defect worth reporting. See `EdgeCaseInjectionMain` for a ready-made mission.

### Interrupted Workflow Simulation

Opt-in via the `interruptions` mission parameter (value `enabled`, default off — an ordinary login/registration mission is unaffected). When enabled, `PageLevelCandidateActionGenerator` adds two page-level candidates every iteration — `REFRESH` (reload the page) and `BACK` (browser back-navigation) — alongside the normal element-scoped ones. Unlike every other candidate, these aren't tied to any element, so they come from a separate generator merged in by `CompositeCandidateActionGenerator`, and skip the element-centric confidence estimator in favor of a fixed, deliberately low confidence (0.2) — interruption is a stress probe, not something that should out-compete legitimate progress toward the goal under most exploration strategies.

Verified live against saucedemo.com with `explorationStrategy: random` (random ignores confidence, so it actually exercises these instead of the default greedy strategy always preferring the credential fields): 14 REFRESHes and 10 BACKs fired across 25 actions through the real browser, the anomaly pipeline correctly attributed findings to the action that preceded them (`REQUEST_FAILED ... (after BACK)`), and the engine kept running without crashing throughout. One honest finding from that run: enough consecutive `BACK`s walked the browser past the mission's own start page into `about:blank` (history underflow) — realistic behavior for a user mashing the back button, not a bug, and the engine's existing per-iteration error recovery (see DefaultMissionEngine) handled the resulting empty observation cleanly rather than crashing.

### Double-Click / Duplicate-Submission Stress Testing

Opt-in via the `doubleClicks` mission parameter (value `enabled`, default off). When enabled, `DoubleClickCandidateActionGenerator` offers a `DOUBLE_CLICK` alternative — a real Playwright `dblclick()` event, not two separate clicks — for every element `ElementActionMapper` already says is CLICK-capable (buttons, submit inputs, checkboxes, radios, links), probing for the classic "form doesn't disable its submit button, user gets double-charged/double-ordered" bug class. The generator delegates the "is this clickable" question to the same `ElementActionMapper` used for ordinary CLICK candidates instead of re-deriving its own list, so it can't drift out of sync with what CLICK is actually offered for. Same fixed low confidence (0.2) and same reasoning as REFRESH/BACK for why.

Verified live against saucedemo.com with `explorationStrategy: random`: `DOUBLE_CLICK` fired 3 times through the real browser — including once on the login button before the credential fields were even filled — and the mission still completed successfully with zero findings, meaning saucedemo's login handled it cleanly. That's a legitimate result in itself (no bug found is still information), and confirms the mechanism (candidate generation → scoring → handler → real `dblclick()`) works end-to-end without needing to wait for a site that actually breaks under it.

### Race-Condition Stress Testing

Opt-in via the `raceConditions` mission parameter (value `enabled`, default off). `RaceClickCandidateActionGenerator` offers a `RACE_CLICK` alternative for the same CLICK-capable elements as double-click, but instead of a browser-level `dblclick()` gesture, it fires the element's click handler twice back-to-back with zero wait between them, entirely inside a single `Locator.evaluate("el => { el.click(); el.click(); }")` call — this probes for genuine server-side races (e.g. two overlapping "place order" requests reaching the server before either completes), the "double submission" scenario from the original spec, rather than a UI-level double-click gesture.

Deliberately **not** implemented with two Java threads calling into the same Playwright `Page` concurrently — Playwright's Java API documents a `Page` as unsafe to drive from multiple threads, so both clicks fire from a single Playwright command, inside the browser's own JS engine, where they still dispatch synchronously back-to-back with no awaited delay between them.

Detection here is honestly indirect: this doesn't (and can't, without domain-specific assertions this project doesn't have — see the World Model's documented state-extraction gap) verify application-level side effects like "was exactly one order created." It exercises the concurrent-request code path and relies on the existing anomaly pipeline to catch whatever breaks as a result (console/page errors, failed requests) — same detection model as REFRESH/BACK/DOUBLE_CLICK.

Verified two ways: (1) mechanism-level, against a synthetic page with a click counter — confirmed the element's click handler fires exactly twice, synchronously, with `page.locator("#btn").evaluate(...)`; (2) live against saucedemo.com with `explorationStrategy: random` — `RACE_CLICK` fired 3 times through the real browser (including on the login button before the fields were filled), mission completed successfully, zero findings.

### Dialog Handling

Always on (not opt-in — this closes a silent-failure gap, not a stress-testing mode). Without a listener, Playwright auto-dismisses native dialogs (`alert`/`confirm`/`prompt`/`beforeunload`) with zero record of it happening — the action that triggered one just appears to do nothing, and nothing in the report explains why. `PlaywrightBrowser` now registers `page.onDialog(...)`, same pattern as the existing console/page-error/request-failed/crash listeners: it's captured as an anomaly (type `DIALOG`, severity LOW, e.g. `DIALOG: confirm: Are you sure? (after CLICK #delete-button)`) and given a plain-English explanation in the report.

Still dismisses by default rather than accepting — accepting would be needed to actually walk through some flows (e.g. a "delete this?" confirm), but auto-accepting is a real-damage risk against a live site under test, so this stays conservative (matches Playwright's own default) rather than optimizing for exploration coverage. Not yet integrated into the World Model's navigation graph — a dialog firing isn't recorded as a distinct state or edge attribute (see Sprint 25's known gaps).

Verified live: navigated to a page with a button wired to `confirm("Delete this item?")`, clicked it through the real `PlaywrightBrowser` — the click returned normally in under 300ms (didn't hang waiting on the dialog) and `drainAnomalies()` correctly captured a `DIALOG` signal with the dialog's type and message.

---

## Sprint 23

### Goal Management

Support

- Primary goals
- Secondary goals
- Dynamic goals

Example

```
Login

↓

Navigate Dashboard

↓

Open Settings

↓

Update Profile
```

---

## Sprint 24 — Partially Completed

### Memory

`VisitedStateMemory` tracks, in-run only:

- ✅ visited pages (distinct URLs)
- ✅ visited states (URL + set of interactive element locators — ignores element values, so filling a field isn't a new state, only navigating is)
- ⬜ visited buttons — not tracked separately; `ExecutionMemory` already covers "has this exact CLICK been executed", which is the closer existing analogue
- ⬜ visited forms — no `<form>` grouping exists in the Observer yet, so there's nothing to key a "form" memory on

Wired into `DefaultObserver`, which now logs "new state" vs "revisiting a known state" on every observation. Not persisted across separate runs — see the Memory scope discussion in this project's history: real persistence is deferred until there's a stable mission identity and an actual consumer (World Model / LLM reasoner) that needs it, so it isn't built speculatively.

---

## Sprint 25 — Partially Completed

### World Model

- ✅ Navigation graph — `WorldModel` records, as the mission runs, which action from which state led to which other state (`NavigationEdge`: fromState, actionType, actionTarget, toState)
- ⬜ Dialogs — now *detected* (see Sprint 22's dialog handling, below) and surfaced as a Finding, but not modeled in the graph itself: a dialog firing isn't recorded as a distinct state or edge attribute, so the World Model can't yet answer "does this action always trigger a confirm?"
- ⬜ Forms — same gap noted under Memory (Sprint 24); no `<form>` grouping exists
- ⬜ Broader "application state" beyond page/element structure (e.g. logged-in/out, cart contents) — would need domain-specific state extraction, not generic

In-run only, same reasoning as Memory (see `ARCHITECTURE.md`).

Used for one concrete decision so far: `KnownDeadEndCandidateFilter` removes a candidate only when its destination is **known** (this exact action was tried from this exact state before) **and** that destination is already visited — proven redundant, not guessed. Candidates whose destination has never been observed are left untouched.

The Sprint 8 `breadth-first`/`depth-first` strategies are **not yet** upgraded to consult this graph — they still use the element-tag proxy. Making them graph-aware (prefer a candidate whose *known* destination is unvisited, fall back to the tag heuristic when unknown) is a natural follow-up, not bundled into this sprint.

---

## Sprint 26 — Partially Completed

### LLM Integration

- ✅ `LlmActionScorer` — a real language model now makes one of the decisions in the pipeline: which already-generated, already-valid candidate to try next. Opt-in via `explorationStrategy: llm`, alongside the existing rule-based strategies. Provider-agnostic (`OpenAiCompatibleChatClient` speaks the OpenAI-compatible `/chat/completions` shape shared by Ollama, LM Studio, vLLM, and OpenAI itself), configured entirely via `AEGIS_LLM_*` environment variables so switching providers is a config change, not a code change. Falls back to `HighestConfidenceActionScorer` on any network failure or malformed response — a bad model response degrades to the old deterministic behavior, it never crashes the mission or executes something unvalidated.
- ⬜ The original diagram above — LLM as a full `GoalReasoner` replacement, seeing the raw observation directly and driving candidate generation itself — is a bigger step than what's built. `LlmActionScorer` deliberately narrows the model's job to "which of these" rather than "figure out how to interact with this page": candidates are still generated and validated by the existing deterministic pipeline first, so a wrong or nonsensical model response can only ever result in a fallback, never an invalid action. The full-replacement version remains open.

Verified two ways: (1) the raw HTTP/JSON client against a real (fake, in-test) OpenAI-compatible server — confirmed the request shape, auth header, and response parsing round-trip correctly; (2) the full pipeline — ran an actual saucedemo.com login mission through the real `EngineFactory` with `explorationStrategy: llm` pointed at that same fake server: it received exactly one call per decision (3 calls for a 3-step login), and the mission completed successfully end-to-end through a real browser session. The fake server always returns "pick candidate 0" rather than genuine reasoning — this proves the wiring is real, not the quality of any specific model's judgment, which depends entirely on whichever model you actually point `AEGIS_LLM_MODEL` at.

Honest framing for the project's own "Current State" line below: this makes AEGIS an **AI-assisted** rule-based agent, not yet a "Generic Exploratory AI Agent" — `greedy` (a fixed heuristic) is still the default, the LLM strategy is opt-in, and only one of several decision points (candidate scoring) is model-backed so far. Candidate generation, input values, and anomaly classification are all still deterministic.

---

## Sprint 27 — Completed

### Learning

- ✅ Every executed action's outcome is now recorded as an `Experience` (`ExperienceRecorder`, wired into `DefaultMissionEngine`) — SUCCESS if it ran without throwing, ERROR if it didn't — alongside the existing per-iteration resilience handling (a thrown action still becomes a HIGH `Finding` and the mission keeps going either way).
- ✅ `PatternAnalyzer` groups those experiences by action identity and computes a per-action success rate; `LearningEngine` turns that into a confidence adjustment in `[-0.20, +0.20]` (≥90% success → `+0.20`, ≥75% → `+0.10`, ≥50% → `0.00`, ≥25% → `-0.10`, below → `-0.20`).
- ✅ `HeuristicCandidateConfidenceEstimator` applies that adjustment on top of its static heuristic score, so a `(type, target)` that's been failing gets deprioritized and one that's been succeeding gets a boost — without hand-writing a rule for why.
- 🐛 Fixed a bug found during review before this was verifiable at all: `Action` is a record whose `equals()`/`hashCode()` include a random `UUID id` and `Instant createdAt`, both freshly generated every time a candidate is regenerated. Grouping or keying by the raw `Action` (what `PatternAnalyzer` and `LearningResult` originally did) meant no two "same logical action" instances from different iterations were ever considered equal — every group had exactly one member, so `successRate` was always 0.0 or 1.0 and the whole pipeline was silently a no-op. Fixed via `ActionKey.of(action) = type + "|" + target`, the same stable-identity convention `ExecutionMemory` already used for the same reason.

Verified live: ran an actual saucedemo.com login mission end-to-end. All 3 executed actions (type username, type password, click login) were recorded as SUCCESS experiences with real measured durations, and `learn()` correctly returned `+0.20` for each afterward (100% success rate bucket). 12 new unit tests cover the fixed grouping, the adjustment-bucket math, and the estimator's clamping — full suite is 88/88 passing (was 76/76).

Scoped like `WorldModel`/`VisitedStateMemory`: the experience repository is in-memory, per mission run. Learning doesn't carry over between separate mission runs yet — that would be real (cross-run) persistence, which is a distinct follow-up, not something this sprint claims.

---

## Sprint 28

### Autonomous Exploratory Testing

Final vision

```
Mission

↓

Observe

↓

Reason

↓

Explore

↓

Learn

↓

Discover Defects

↓

Generate Report
```

---

## Sprint 29 — Completed

### Coverage-Aware Exploration

- 🐛 Found and fixed a real bug during review: `KnownDeadEndCandidateFilter` was permanently unreachable dead code. `AlreadyExecutedCandidateFilter` blocked any candidate whose `(type, target)` had *ever* been executed, mission-wide, with no notion of which state it was tried from. `WorldModel` only has destination data for actions that have already been executed. So by construction, nothing that reached `KnownDeadEndCandidateFilter` could ever have the "known destination" it checks for — the dead-end check had never fired, in any mission, ever.
- ✅ Fixed by scoping `ExecutionMemory` per state (`StateSignature`) instead of globally — an exact repeat of the same action from the same state is still blocked (that's what actually prevents pointless looping), but a locator genuinely present on more than one page (a persistent nav link, say) can now be reconsidered once reached from a state it hasn't been tried from yet.
- ✅ Changed `WorldModel.knownDestinationsOf` to look up by action identity (type + target) across *every* recorded source state, not just "from this exact state" — so a candidate reached from a new state gets judged against everywhere that locator has led before, not just its own (necessarily empty) history. A candidate is now pruned only if every known destination it's ever led to is a state we've already visited; any unvisited destination, or no history at all, leaves it alone.
- ✅ 11 new/updated unit tests (`ExecutionMemoryTest`, `AlreadyExecutedCandidateFilterTest`, `WorldModelTest`, `KnownDeadEndCandidateFilterTest`) — including one that specifically reproduces the fixed scenario: a nav link proven to dead-end from page A gets pruned when offered again from page B, despite never having been tried from B itself. Full suite: 99/99 passing.

Verified live: a 10-iteration saucedemo.com run with no early goal (so it couldn't just stop at the first page) ran clean through several state revisits — including one genuine element-timeout mid-run, correctly recorded as an ERROR experience with a resulting `-0.20` learning adjustment, mission continuing afterward rather than crashing.

### Exploration Metrics

- ✅ New `ExplorationCoverage` (distinct interactive elements discovered across the whole mission vs. distinct elements actually the target of an executed action, as a percentage), computed once in `MissionReportData` and surfaced in both report formats — a line in the text report, a tile in the HTML report.
- Deliberately narrow: this is elements-on-pages-actually-visited coverage, not site-wide page coverage (that's Phase 5's "Page coverage," a different, harder metric needing a sitemap or DOM diffing this project doesn't have).
- 4 new unit tests (`MissionReportDataTest`). Full suite: 103/103 passing.

Verified live against saucedemo.com: the real generated report showed `Exploration Coverage: 3/32 discovered interactive elements exercised (9%)` after a 3-step login, and the HTML report's stats tile matched.

### State Prioritization

- ✅ New `CoverageAwareActionScorer`, opt-in via `explorationStrategy: coverage-aware` — the scoring counterpart to `KnownDeadEndCandidateFilter`'s fix above. Uses the exact same `WorldModel.knownDestinationsOf` evidence, but as a reward instead of a cut: a candidate confirmed, from history at any prior state, to lead somewhere not yet visited gets a fixed `+0.15` bonus over its plain heuristic confidence.
- Deliberately two-tier, not a full "rank by how unexplored" score: a candidate whose *every* known destination is already visited never reaches a scorer at all — the filter prunes those first — so there's only "confirmed still worth trying" vs. "no evidence either way" to rank between.
- 4 new unit tests (`CoverageAwareActionScorerTest`). Full suite: 107/107 passing. Verified live against saucedemo.com with no crashes.

### Automatic Dynamic Strategy Switching

- ✅ New `AdaptiveActionScorer`, opt-in via `explorationStrategy: adaptive`. A wrapper, not a new standalone strategy — it delegates to `greedy` normally and to `coverage-aware` once exploration looks stuck (no newly discovered state in the last 3 iterations), switching back the moment a new state turns up again.
- "Stuck" is recomputed fresh from `MissionContext`'s own observation history on every call — no separate flag or reset logic needed, it just falls out of re-checking the trailing window each time.
- 4 new unit tests (`AdaptiveActionScorerTest`), including one that specifically proves the switch-back-once-unstuck behavior. Full suite: 111/111 passing.

Verified live against saucedemo.com — ran clean with no crashes. That particular run never actually got stuck (each iteration hit a new page quickly), so it stayed on `greedy` the whole time, which is the correct behavior, not a gap in coverage — the switching logic itself is what the unit tests pin down deterministically, independent of whether any one live run happens to trigger it.

This closes out every goal Phase 4 – Exploration Intelligence originally listed. Remaining honest limitation: the 3-iteration stuck threshold is a fixed constant, not tuned or configurable per mission.

---

## Sprint 30 — Completed

### Phase 3 – Adaptive Decision Making

Audited Phase 3's five goals against what actually exists: historical success scoring, confidence adjustment, and failure penalty were already delivered by Phase 2's Learning Framework (Sprint 27) — that's what `PatternAnalyzer`'s success rate and `LearningEngine`'s bucketed `[-0.20, +0.20]` adjustment already are. Adaptive exploration follows for free from that same wiring: since the adjustment is applied in `HeuristicCandidateConfidenceEstimator`, upstream of every `ActionScorer`, *every* strategy adapts to learned history automatically — not just a dedicated "adaptive" one.

That left exactly one real gap:

- ✅ **Exploration bonus**: a candidate with zero recorded `Experience` — genuinely never tried, not just landed in the neutral 50-75% success-rate bucket — now gets a flat `+0.05` confidence bonus. New `LearningResult.hasExperience(type, target)` distinguishes "no data at all" from an explicit `0.0` adjustment, since `adjustmentFor()`'s `getOrDefault(..., 0.0)` collapses both cases to the same number otherwise.
- This is deliberately a different signal from Phase 4's `CoverageAwareActionScorer`: that one rewards a candidate *confirmed* (via WorldModel history) to lead somewhere unvisited; this one rewards a candidate we simply have *no information about yet*, at the confidence-estimation layer rather than the scoring layer, so it applies uniformly across every strategy rather than being opt-in to one.
- 7 new/updated unit tests (`LearningResultTest`, and `HeuristicCandidateConfidenceEstimatorTest` updated — its old "no adjustment" test conflated "never tried" with "tried and neutral," which the new `hasExperience` check now correctly tells apart). Full suite: 116/116 passing.

Verified live against saucedemo.com: the real generated report showed `(learning-adjusted +0.05, never tried yet this mission)` on every step, since nothing has any history on a fresh mission run.

This completes Phase 3 – Adaptive Decision Making.

---

## Sprint 31 — Completed

### Phase 5 – Coverage Intelligence

- ✅ **Page coverage**: new `PageCoverage` — the same discovered-vs-interacted element math as Phase 4's `ExplorationCoverage`, broken down per page URL instead of blended mission-wide. Surfaced as a table in the text report and a table with progress bars in the HTML report. Still elements-on-a-visited-page coverage, not a claim about the whole site — a true "% of the site visited" metric stays impossible without a sitemap or crawl, which this project deliberately doesn't have.
- ✅ **DOM coverage**: reframed rather than rebuilt — this is exactly what `ExplorationCoverage`/`PageCoverage` already measure, in the only sense achievable here (interactive elements the Observer actually saw vs. exercised). Elements that never render at all (behind an unopened modal or tab) are invisible to AEGIS and can't be measured — a permanent architectural boundary, not a gap.
- ✅ **Heat maps**: the HTML report's SVG navigation graph now visually encodes revisit frequency. Node radius and fill-opacity scale with how many times a state was observed (new `MissionReportData.stateVisitCounts`); edge stroke-width scales with how many times that exact transition was traversed, collapsing repeats into one thicker line instead of several overlapping near-duplicate arcs (the old behavior). A hover tooltip and an inline "(×N)" edge label spell out the raw number too, not just the visual.
- 2 new unit tests. Full suite: 118/118 passing.

Verified live against saucedemo.com: real report showed a per-page breakdown (`https://www.saucedemo.com/ 3/3 (100%)`, `.../inventory.html 5/29 (17%)`, `.../cart.html 2/12 (17%)`), and a state visited 5 times rendered at radius 42.0/opacity 0.40 in the actual SVG output versus the base 26.0/no-heat for a single visit — the live numbers match the formula exactly.

This completes Phase 5 – Coverage Intelligence, with the shared honest caveat from Phase 4: nothing here can measure coverage against the whole site, only against what AEGIS has actually observed.

---

## Sprint 32 — Completed

### Phase 6 – Bug Intelligence

- ✅ **Bug clustering / Duplicate detection**: new `com.aegis.core.bug` package. `BugFingerprint.of(finding)` normalizes a Finding's message — collapsing embedded digits, UUIDs, and URLs — into a coarser identity than exact-string equality, so "add-to-cart failed for item 4" and "...for item 7" recognize as the same underlying bug. `DefaultBugClusterAnalyzer` groups a mission's Findings by that fingerprint into `BugCluster`s. This sits *above* the existing `BrowserSignalAnomalyDetector` exact-signature dedup (untouched, still a Stable Component) — that layer already collapses byte-identical repeats to one Finding; this layer clusters what's left over.
- ✅ **Severity prediction**: a cluster's severity is the worst severity among its members, escalated one level (never past CRITICAL) once it's recurred 3+ times — a problem that keeps happening is worse than any single occurrence's own rating.
- ✅ **Root cause grouping**: a cluster spanning more than one distinct page URL is flagged as likely sharing a root cause. Deliberately hedged in the actual wording ("may share a root cause") — this is a co-occurrence heuristic, not causal analysis.
- Purely a report-layer addition (computed in `MissionReportData.from()`, same shape as Phase 4/5's coverage metrics) — no changes to the live mission loop or any other Stable Component. Surfaced as a new "Bug Clusters" section in both report formats, above the existing flat findings list.
- 14 new unit tests (`BugFingerprintTest`, `DefaultBugClusterAnalyzerTest`, one `MissionReportDataTest` addition). Full suite: 132/132 passing.

Verified live against saucedemo.com: a genuine Playwright `TimeoutError` mid-run correctly rendered as a single-occurrence HIGH-severity cluster in both real report formats.

This completes Phase 6 – Bug Intelligence. Honest limitation: fingerprinting is regex normalization, not semantic understanding — findings that differ only in wording, with no embedded digit/UUID/URL to normalize away, still fingerprint separately even where a human reader would call them the same bug.

---

## Sprint 33 — Partially Completed

### Phase 8 – AI Intelligence: Bug Explanation

- ✅ New `BugExplainer` interface, two implementations: `RuleBasedBugExplainer` (default — just the existing fixed-per-signal-type sentence) and `LlmBugExplainer` (opt-in — asks a real model to write a short triage note for a Phase 6 `BugCluster`, same provider-agnostic client and fallback-on-any-failure discipline as `LlmActionScorer` from Sprint 26).
- ✅ Explanations are computed once per cluster inside `MissionReportData.from(...)` itself, not lazily per report format — generating both the text and HTML report from the same mission data never calls the model twice for the same bug.
- ✅ Opt-in via a new `AEGIS_LLM_BUG_EXPLANATIONS=enabled` environment variable, not a mission parameter — same reasoning as the rest of `AEGIS_LLM_*` config: mission parameters get serialized into every report.
- 6 new unit tests (`RuleBasedBugExplainerTest`, `LlmBugExplainerTest`, two `MissionReportDataTest` additions). Full suite: 138/138 passing.

Verified live end-to-end against a fake OpenAI-compatible server (no real LLM provider reachable in this sandbox — same verification method Sprint 26's `LlmActionScorer` integration used): a real saucedemo.com run produced two genuine bug clusters, each independently triggered its own model call, and the model's response text showed up correctly in both real generated report formats.

### Phase 8 – AI Intelligence: Recommendation Engine

- ✅ New `RecommendationEngine` interface, same two-implementation shape as bug explanation: `RuleBasedRecommendationEngine` (default — points at the single highest-severity `BugCluster`, e.g. "3 issue(s) found. Highest priority: [CRITICAL] ... (occurred 2 times).") and `LlmRecommendationEngine` (opt-in — looks across *all* of a mission's bug clusters at once, not one at a time like `LlmBugExplainer`, and writes a short prioritized recommendation).
- ✅ Skips the model call entirely when there are no bug clusters — nothing to prioritize, so nothing to ask about.
- ✅ Its own opt-in, `AEGIS_LLM_RECOMMENDATIONS=enabled`, independent of `AEGIS_LLM_BUG_EXPLANATIONS` — a mission run can want one AI feature without the other.
- ✅ Surfaced prominently in both report formats, directly under the outcome summary — the "so what do I do" a reader wants before the raw findings list, not buried after it.
- 8 new unit tests (`RuleBasedRecommendationEngineTest`, `LlmRecommendationEngineTest`, three `MissionReportDataTest` additions). Full suite: 146/146 passing.

Verified live end-to-end with an upgraded fake server that inspects the request body to tell a bug-explanation prompt apart from a recommendation prompt and returns a distinct canned response for each — confirming the two AI features make genuinely independent model calls rather than one being silently reused for the other. Both `AI-GENERATED-EXPLANATION:...` and `AI-GENERATED-RECOMMENDATION:...` showed up correctly, in the right places, in the same real report.

### Phase 8 – AI Intelligence: Natural Language Missions

- ✅ New `MissionParser` interface: `RuleBasedMissionParser` (default — a bare URL regex against the raw instruction, the one thing mechanically extractable without understanding the sentence; goal/success-condition/credentials left unset, since there's no honest rule-based way to infer those from arbitrary prose) and `LlmMissionParser` (opt-in — asks a model to extract `baseUrl`, `goal`, `successUrlContains`, `username`, and `password` as JSON from a free-text QA instruction).
- ✅ The model's `baseUrl` is never trusted blindly: validated as a real `http(s)` URL with an actual host before use. An invalid or missing URL falls back exactly like a network failure or malformed JSON would — same discipline as validating an `LlmActionScorer` candidate index before trusting it.
- ✅ New `NaturalLanguageMissionMain` launcher entry point — every other `*Main` class builds a `Mission` by hand; this one starts from a plain-English instruction string and lets the parser do it.
- 12 new unit tests (`RuleBasedMissionParserTest`, `LlmMissionParserTest`). Full suite: 158/158 passing.

Verified live twice, both through the real mission engine, not just the parser in isolation:
1. **No reachable LLM server** — correctly fell back to bare-URL extraction, produced a runnable `Mission` with just `baseUrl` set, and the real mission ran its full 10 iterations and failed cleanly (no credentials extracted, so the generic fallback login values didn't match) — degraded, not broken.
2. **Fake server returning a complete extraction** — produced a `Mission` with the correct `baseUrl`, `successUrlContains`, `username`, and `password` all pulled from the instruction text, and the resulting mission genuinely logged into saucedemo.com and reached `SUCCESS`.

### Phase 8 – AI Intelligence: AI Mission Planning

- ✅ New `MissionPlanner` interface: `RuleBasedMissionPlanner` (default — a mechanical plan derived from which mission parameters are set, e.g. "there's a baseUrl, so navigate there") and `LlmMissionPlanner` (opt-in — asks a model to sketch 3-6 short ordered high-level steps from the mission's name/description/parameters).
- ✅ Scoped narrow on purpose, given this is nominally the riskiest of the four AI features: the plan is generated *before* the mission runs and is **purely advisory** — nothing in the live reasoning pipeline (`GoalReasoner`, `ActionScorer`, `CandidateFilter`) ever reads it. A bad or nonsensical plan step is a display-quality problem, never something that gets executed against a real page — unlike `LlmActionScorer`'s candidate index or `LlmMissionParser`'s `baseUrl`, both of which *do* feed into execution and need strict validation. This is what makes "AI mission planning" safe to ship without the invention-of-invalid-actions risk that made it the deferred, "bigger" goal in the first place.
- ✅ Threaded through as already-computed data, not a strategy invoked internally like `BugExplainer`/`RecommendationEngine` — the plan has to exist *before* the `MissionContext` a report is normally derived from is even created, so `MissionReportData`'s 5-arg `from(...)` overload just takes a finished `MissionPlan`.
- ✅ Its own opt-in, `AEGIS_LLM_MISSION_PLANNING=enabled`, independent of the other three AI features.
- ✅ Surfaced as a new "Mission Plan" section in both report formats, right after the summary — the intended approach, before the detailed results.
- 12 new unit tests (`RuleBasedMissionPlannerTest`, `LlmMissionPlannerTest`, two `MissionReportDataTest` additions). Full suite: 170/170 passing.

Verified live end-to-end against a fake server: the AI-generated plan appeared correctly in both the console output and the real report — and, critically, the mission still ran on its real, independently-configured credentials and reached `SUCCESS`, confirming the plan genuinely never influences what the tester actually does.

**This completes Phase 8 – AI Intelligence.** Four AI capabilities now exist across the pipeline: `LlmActionScorer` (picks among validated candidates), `LlmBugExplainer`/`LlmRecommendationEngine` (write prose about data they're handed), `LlmMissionParser` (extracts structured fields from a description), and `LlmMissionPlanner` (previews an approach, advisory only). None of them generate or execute an action the deterministic pipeline hasn't already validated — that boundary held across all four.

---

## Sprint 34 — Reporting v2 (Complete, all 3 stages)

Ground-up report redesign against a user-authored spec: the report alone should answer eight questions — mission, what the agent did, why each choice, what it discovered, what bugs it found, what it learned, how much of the app was explored, and what to do next. Explicitly frozen: `MissionEngine`, `Planner`, `DecisionEngine`, `GoalReasoner`, `LearningEngine`, `WorldModel`, `Observer`, `ActionScorer`, `ExecutionMemory` — reporting reads their output, never influences them.

### Mission Timeline ⭐

- ✅ The centerpiece: a chronological, replayable reconstruction of the whole run — Mission Started → Observed (with new-state/revisit detection) → Generated N candidates → Selected X, reason → Execution Successful/Failed → Findings → Mission Finished. New `TimelineEvent`/`TimelineEventKind`.
- ✅ Built entirely from timestamps that already existed on existing records (`Observation.capturedAt`, `ReasoningStep.timestamp`, `Experience.createdAt`, `Finding.detectedAt`) — nothing is captured specially for the timeline, it's a pure reconstruction.
- ✅ Getting accurate per-action Execution Successful/Failed (Findings alone only capture errors, not successes) required exposing `ExperienceRepository` outside `EngineFactory.create()` for the first time — a new `EngineFactory.CreatedEngine` return type bundling it with the `MissionEngine`. `EngineFactory` itself isn't on the frozen list (it's composition/wiring, not a reasoning component), so this was in scope.
- ✅ Rendered as a real vertical timeline in the HTML report (color-coded dots per event kind, a connecting line) and as an indented chronological list in the text report.

### Executive Summary & Statistics

- ✅ Executive Summary redesigned: Mission, Duration, Result, Coverage, Findings, Bug Clusters, a one-line Learning teaser, and the Recommendation, all together at the top — "a manager should stop here."
- ✅ Statistics Dashboard expanded to a full card grid: Actions Executed, Pages, States, Transitions, Coverage, Avg Confidence, Bug Count, Clusters, Duration.

### Better Reasoning Visualization

- ✅ A `learned` badge on any reasoning step or candidate whose confidence carries a Phase 2/3 learning adjustment — parsed from the existing reasoning text (a display-layer read, not a new field on `CandidateAction`, which stays untouched) — plus a `winner` badge marking the selected candidate in the HTML candidate comparison table.

### Learning (teaser now, full section in Stage 2)

- ✅ New `LearningSummary` (new vs. updated actions, confidence improved vs. declined this mission) — computed by reusing `DefaultPatternAnalyzer`'s own `ActionKey` grouping over the mission's `Experience` list, the same grouping `LearningEngine` itself uses internally, just read back afterward for display.

13 new unit tests (`MissionReportDataTimelineTest`). Full suite: 183/183 passing (was 170/170) — including catching and fixing a genuinely flaky test along the way: an early version of the timeline test reused one frozen `Observation` object (one fixed timestamp) for both an initial visit and its revisit, so timestamp-based sorting could reorder events depending on clock resolution. Fixed by giving test observations explicit, strictly-increasing timestamps instead of relying on back-to-back `Instant.now()` calls.

Verified live against saucedemo.com: the real generated report showed a genuinely readable, replayable timeline of a 3-step login (each step's reasoning, execution outcome, and timing visible in order), a correctly populated Executive Summary, and a "3 new, 0 updated action(s) — 3 improved, 0 declined" learning line — all consistent with the actual mission that ran.

### Stage 2 — Coverage, Full Learning, Findings Dashboard, Recommendation Presentation

- ✅ **Coverage visualization**: a "Pages" checklist (✓ per discovered page) alongside the existing per-page coverage bars — with an explicit note that there's no sitemap, so a page AEGIS never found simply can't appear as "not visited." No fabricated "expected pages" list.
- ✅ **Full Learning section**: `LearningSummary` extended with `actionPerformance` — every distinct action this mission touched, sorted best-success-rate-first, reusing the exact same `PatternStatistics` `DefaultPatternAnalyzer` already computes internally. Rendered as ranked Best/Worst Performing Actions tables (top 3 each) in both formats.
- ✅ **Findings Dashboard**: new `FindingCategory` enum (`JAVASCRIPT`, `NETWORK`, `NAVIGATION`, `TIMEOUT`, `STABILITY`, `OTHER`), mapping each `BugCluster`'s underlying signal type to a category. Deliberately excludes `ACCESSIBILITY`/`PERFORMANCE` from the category list — AEGIS has no detector for either, and labeling findings that way would be invented, not derived.
- ✅ **Improved recommendation presentation**: pulled out of the Executive Summary paragraph into its own highlighted callout section right below it — presentation only, `RecommendationEngine`/`LlmRecommendationEngine` (Phase 8) untouched.
- 4 new unit tests (`MissionReportDataStage2Test`). Full suite: 187/187 passing.

Verified live against saucedemo.com with a real timeout finding present: Learning correctly showed 10 new experiences (9 confidence-increased, 1 reduced) and ranked the one failed action — `CLICK [id='inventory_sidebar_link']`, 0% success — at the top of Worst Performing Actions; the Findings Dashboard correctly filed that same finding under `TIMEOUT`.

### Stage 3 — Screenshot Hooks, Interactive HTML, JSON Export

- ✅ **Screenshot hooks**: `TimelineEvent` gains a nullable `screenshotPath` via a new 5-arg constructor — every existing 4-arg call site keeps compiling unchanged and gets `null` automatically. No live capture happens today (that would mean instrumenting `Observer` or the browser layer, both frozen); both report generators already render the path when one's present, so a future capture mechanism is purely additive from here.
- ✅ **Interactive HTML**: a sticky table-of-contents nav (all 12 sections now have real `id` attributes) and a Mission Timeline filter — All / Observations / Reasoning / Executions / Findings toggle buttons, pure client-side JS, no new data.
- ✅ **JSON export**: new `JsonReportGenerator`, same overload chain as the text/HTML generators. Built by hand with Jackson's tree API — the same low-level approach already used elsewhere in this codebase (`OpenAiCompatibleChatClient`, `LlmActionScorer`, `LlmMissionParser`) — rather than reflecting `MissionReportData` directly, which would need a new `jackson-datatype-jsr310` dependency and would leak internal Java shapes into what should be a stable, intentional export schema. `MissionRunner` now writes all three formats every run.
- **PDF export — deliberately skipped.** The original ask flagged this as conditional ("if it still aligns with the roadmap"). A real PDF renderer means a real new dependency with licensing baggage (iText's current versions are AGPL/commercial; OpenHTMLtoPDF is LGPL) for something a browser's own "Print to PDF" already does for free against the self-contained HTML report. That's a worse trade than this project's own "no external dependencies" principle should accept for the marginal benefit.
- 8 new unit tests (`TimelineEventTest`, `JsonReportGeneratorTest`). Full suite: 195/195 passing.

Verified live against saucedemo.com: all three report formats (`.txt`/`.html`/`.json`) written successfully from one run; the JSON output parsed as valid JSON with correct mission/coverage/timeline/learning/recommendation data; the HTML table-of-contents and timeline filter both rendered correctly against all 12 section IDs.

**Reporting v2 is complete** — all three stages, all eight of the original success-criteria questions (what was the mission, what did the agent do, why each choice, what it discovered, what bugs it found, what it learned, how much was explored, what to do next) answerable from the report alone, in three formats, without reading a single log line. Every frozen component (`MissionEngine`, `Planner`, `DecisionEngine`, `GoalReasoner`, `LearningEngine`, `WorldModel`, `Observer`, `ActionScorer`, `ExecutionMemory`) stayed untouched throughout.

---

## Sprint 35 — Phase 9: Self-Healing (Complete)

All four roadmap goals — locator healing, a retry engine, dialog recovery, navigation recovery — land as one new `com.aegis.core.resilience` package that decorates `Browser`. `Browser` was confirmed *not* on the Stable Components list (unlike `Executor`/`ActionExecutor`/`ActionHandler` sitting above it, or `MissionEngine` above that), so every real consumer — `Observer`, the action handlers, `AnomalyDetector`, `DefaultMissionEngine` itself — gets this for free with zero changes: `Browser`'s contract (complete, or throw) is unchanged, only how often it throws. `EngineFactory` does the one-line wrap.

- ✅ **Retry engine**: `SelfHealingBrowser` retries a failed element action or navigation call once, after a short delay, before giving up or attempting to heal.
- ✅ **Locator healing**: new `LocatorHealer` — pure string logic, no DOM access, scoped to exactly the 3 locator shapes AEGIS itself generates (see `PlaywrightBrowser.buildBestLocator`). `[id='X']`/`[name='X']` heal to a substring match (`[id*='X']`, then `:nth-match([id*='X'], 1)` if that's still ambiguous) — covers the common real-world case of a framework appending a generated suffix to an otherwise-stable id. `:nth-match(tag, N)` heals to the neighboring indices — covers the DOM gaining or losing one matching element since the locator was generated. Anything else is left alone; there's nothing to heal.
- ✅ **Navigation recovery**: the same retry-once treatment for `navigate`/`refresh`/`goBack` — no locator involved, just a transient-network-blip safety net.
- ✅ **Dialog recovery**: `PlaywrightBrowser`'s dialog handler went from blanket-dismiss to type-aware — `prompt` now accepts a blank answer instead of dismissing (dismiss returns `null`, which aborts most prompt-gated flows outright; accepting `""` lets the flow continue while committing to nothing). `confirm`/`beforeunload` deliberately keep dismissing — auto-accepting those is a real-damage risk against a live site under test, an explicit safety call already made and documented; this phase adds intelligence only where it doesn't fight that. `alert` is unchanged.
- ✅ Retry/heal attempts use a short, explicit bounded timeout rather than Playwright's full default wait: `Browser` gained additive default-method overloads (`click(locator, Duration)`, etc. — non-breaking, every existing caller is unaffected), implemented for real in `PlaywrightBrowser` via Playwright's per-call `Options.setTimeout`. The first attempt still pays the full default wait once, unchanged from today; only the bounded retry/heal attempts are short, so a genuinely-broken locator fails fast instead of stalling a mission for minutes.
- 17 new unit tests (`LocatorHealerTest`, `SelfHealingBrowserTest` — the latter against a hand-written fake `Browser` proving the exact retry → heal → give-up-and-rethrow sequence, single-retry navigation, and that read-only/lifecycle calls pass straight through with no retry logic at all). Full suite: 212/212 passing.

Verified live against a real Playwright browser and a real local page, not just the fake double: a button/input whose real `id` had gained a `-v2`-style suffix since the locator was generated — the exact locator genuinely fails against the live DOM, retries, heals to the substring-match candidate, and the click/type actually lands (confirmed via a real page-title mutation the element's own `onclick`/`oninput` triggers). A third scenario with an unhealable locator confirmed the give-up path correctly rethrows Playwright's own `TimeoutError` rather than swallowing it. Also re-ran the existing SauceDemo login mission end-to-end through the real `EngineFactory` wiring (now including `SelfHealingBrowser`) — still `SUCCESS`, confirming the happy path is unaffected.

**This completes Phase 9 – Self-Healing.**

---

## Sprint 36 — Phase 10: AEGIS v1.0 (Complete)

The wrap-up milestone. Five of its six deliverables — autonomous exploratory testing, adaptive learning, coverage intelligence, AI reasoning, production reporting — were already shipped by earlier phases; this sprint's job was confirming each still holds and closing the one genuine gap.

- ✅ **Stable public API**: until now, the only way to run a mission end-to-end (execute + generate all three reports) was `aegis-launcher`'s `MissionRunner` — package-private, `aegis-launcher`-only. New public facade in `aegis-core`, the actual library module: `Aegis.run(Mission)` → `AegisReport` (the `MissionResult`, the `MissionPlan`, and text/HTML/JSON report content, all in-memory). Deliberately doesn't write to disk — that's a caller decision, not the library's. `aegis-launcher`'s `MissionRunner` now delegates to it and handles file-writing/console output itself; every existing `*Main` class works identically.
- ✅ `Mission`, `Aegis`/`AegisReport`, `MissionResult`, and the three report generators' `generate(...)` methods are now the versioned public contract — a different, narrower kind of "stable" than the Architecture Status Stable Components list (that one's about internal reasoning components not being redesigned; this is about what external code can rely on).
- 1 new unit test (`AegisReportTest`). Full suite: 213/213 passing.

Verified live with a harness compiled and run against **only** the `aegis-model`/`aegis-core` build outputs — `aegis-launcher` deliberately excluded from the classpath — proving a real external consumer can embed AEGIS with just the library module and one call. Ran a single capstone mission through `Aegis.run(...)` alone exercising most of what this project has built at once: `explorationStrategy: adaptive`, interruptions/double-clicks/race-conditions all enabled, all three AI report features enabled against a fake OpenAI-compatible server, against a real saucedemo.com login. Result: `SUCCESS`, the AI-generated mission plan appeared correctly in the returned report (confirming the LLM toggle wiring survived the refactor), all three report formats had the expected shape. Bug-explanation/recommendation markers correctly didn't fire — zero bugs found this run, nothing to explain — both already proven live against a real anomaly back in Sprint 32/33.

Also bumped every module's Maven version from `0.1.0-SNAPSHOT` to `1.0.0-SNAPSHOT`, so the build metadata matches what this document now says. Still a SNAPSHOT — no tag, publish, or release was performed; that's a separate call for whoever owns the release process, not something to do silently while closing out a roadmap milestone.

**This completes Phase 10 – AEGIS v1.0 — and with it, every phase on the roadmap.**

---

## Sprint 37 — Real Screenshot Capture (Complete)

First item off the "Next Milestone" list from Sprint 36 — not one of the original 10 phases. Reporting v2 (Phase 7) shipped `TimelineEvent.screenshotPath` as a data-model-only hook, always `null`, because live capture meant instrumenting the browser layer — assumed frozen at the time alongside `Observer`. Phase 9 (Self-Healing) later proved `Browser` specifically isn't on the Stable Components list, which is what makes this possible.

- ✅ `Browser` gained an additive `screenshotPng()` default method; `PlaywrightBrowser` implements it for real via `page.screenshot()`.
- ✅ `SelfHealingBrowser` — already the one layer every browser call flows through — now captures a screenshot after every element action and navigation call, success or failure (via try/finally, so a capture failure never masks the real outcome), exposed via a new `capturedScreenshots()`.
- ✅ `EngineFactory.CreatedEngine` gained a third field (`Supplier<List<ScreenshotSample>>`), read by `Aegis.run()` after execution finishes, same pattern as the existing `experienceRepository` field.
- ✅ `MissionReportData.buildTimeline` matches each Execution event to its nearest captured screenshot by timestamp (1-second tolerance — keeps a WAIT action, which never touches the browser, from borrowing a neighbor's capture). Deliberately scoped to Execution events only: an observation/reasoning/finding event has no specific browser action to capture against, so giving it a screenshot would imply a causal link that isn't real.
- ✅ Renamed `screenshotPath` → `screenshotDataUri`, now a real `data:image/png;base64,...` URI instead of a (never-populated) filesystem path — required for the HTML report to stay self-contained now that this is actually populated. Safe to rename: `TimelineEvent` was never part of the v1.0 public API promise.
- ✅ Text report shows a short placeholder instead of the raw base64 (would make a grep/diff-oriented format unreadable); HTML renders a real inline `<img>`; JSON carries the full data URI.
- 8 new unit tests. Full suite: 221/221 passing.

Verified live against a real saucedemo.com run: all 3 Execution events got a matched screenshot (100% match rate, zero orphans), zero non-Execution events got one, and an extracted screenshot was confirmed a genuine, valid PNG — correct magic bytes, visually the real saucedemo.com login page.

---

## Sprint 38 — Framework Adoption, Stage 1 of 6 (Complete)

A much larger, separate, user-authored 6-stage initiative: turn AEGIS from internal Java classes into an adoptable framework — public SDK, plugins, enterprise features, a CLI, docs, and eventually a community release. Stage 1's exit criteria: *"A QA engineer with no prior knowledge can run AEGIS in under 15 minutes."* Confirmed with the user before starting: "implement only application-specific pieces: Login" means declarative config (baseUrl/credentials/success-condition — same as existing mission parameters, just typed), not a new scripted-flow capability — AEGIS's autonomous exploration engine is completely untouched.

- ✅ **New `aegis-api` module**: `MissionBuilder` (typed wrapper over every mission parameter), `ApplicationConfig`/`MissionConfig`/`AegisConfig` + `AegisConfigLoader` (YAML via SnakeYAML — already cached locally, parsed into a generic tree and hand-mapped, same style as `JsonReportGenerator`'s hand-built JSON rather than reflection binding), `AegisApplication` (the one interface a consumer implements — `name()` + `config()`, nothing scripted), `Launcher` (`MissionRunner`'s logic promoted up a layer — `aegis-launcher`'s own `MissionRunner` now delegates to it).
- ✅ **New `BrowserConfig`** (`aegis-core`) — `type`/`headless`, additive, defaults match exactly what was hardcoded before. Real chromium/firefox/webkit switching in `PlaywrightBrowser`, not just a schema field.
- ✅ **3 sample projects** (`samples/sample-saucedemo`, `-orangehrm`, `-nopcommerce`) — standalone modules depending **only on `aegis-api`**, proving the SDK boundary for real, with `exec-maven-plugin` preconfigured (`mvn -pl samples/X exec:java`, no flags).
- ✅ Public API surface now spans `aegis-model` + `aegis-core` + `aegis-api` — still documentation/convention, not JPMS (no `module-info.java` anywhere; introducing one would be a high-risk detour for a usability goal, not a security one).
- 27 new unit tests. Full suite: 246/246 passing.

**Two real, narrow bugs found and fixed** building samples against sites this project had never touched before — same "fix it, it blocks this milestone's own deliverable" precedent as Phase 4 earlier this session:

- `PlaywrightBrowser.navigate()` only waited for `DOMCONTENTLOADED`, unlike every other action's `settle()` — fine for simple pages, but confirmed live that OrangeHRM's Vue.js login form has 0 real inputs/buttons at that point, only appearing once `NETWORKIDLE`. Now the initial navigation waits for `NETWORKIDLE` specifically; per-action `settle()` stays at the faster `DOMCONTENTLOADED`.
- An `<input>` with no `type` attribute (implicitly `text` per HTML spec) silently got zero candidate actions generated for it, ever — confirmed live that OrangeHRM's username field (unlike its password field) omits `type` entirely, and AEGIS never once selected it across 20 iterations. Fixed at the observation layer (`PlaywrightBrowser`, not frozen), benefiting every downstream consumer uniformly.

Live-verified all 3 samples end-to-end, each run with nothing but `aegis-api` on the classpath: SauceDemo `SUCCESS` immediately; OrangeHRM `SUCCESS` after both fixes (using `strategy: adaptive`); nopCommerce — `demo.nopcommerce.com` itself turned out to be blocked by bot protection even against real Playwright traffic, so the sample points at `demowebshop.tricentis.com` instead (same underlying platform, already proven reachable all session) — `SUCCESS` with `strategy: form-first` after it initially wandered into the page's 60+ other links instead of finishing the registration form. Also smoke-tested `headless: true` and `type: firefox`, both reaching real `SUCCESS`. Confirmed all 6 of `aegis-launcher`'s existing dev/regression `*Main` classes still work unchanged.

**This completes Stage 1 of 6.** Stages 2–6 (plugin architecture, enterprise readiness, ecosystem/CLI, performance & quality, community release) are real, tracked in `AEGIS_ROADMAP.md`, and not started.

---

# Long-Term Vision

AEGIS is not intended to replace Selenium or Playwright.

Those remain execution engines.

AEGIS sits above them as an intelligent decision-making layer.

```
                AEGIS

Decision Making

Reasoning

Planning

Learning

Memory

-------------------------

Playwright

Selenium

Appium

REST

Database
```

---

# Technology Stack

- Java 23
- Maven
- Playwright
- SLF4J
- Logback
- Jackson
- LLM (opt-in, one decision point so far — see Sprint 26)

Future

- Vector Database
- Reinforcement Learning
- Graph Database
- Knowledge Graph

---

# Status

Current Version

```
v1.0.0-SNAPSHOT
```

Current State

**Rule-Based Autonomous QA Agent, with five independent opt-in AI-assisted decision points** (`explorationStrategy: llm` candidate scoring, mission-plan preview, bug explanation, recommendation, natural-language mission parsing — see Phase 8), plus adaptive/coverage-aware exploration, self-healing execution with real live screenshot capture, and a public SDK (`aegis-api`: `AegisApplication`, `MissionBuilder`, YAML config, `Launcher`) with 3 working sample projects. Every roadmap phase (0 through 10) is complete, plus real screenshot capture (Sprint 37) and Stage 1 of the 6-stage Framework Adoption initiative (Sprint 38).

Honest framing, unchanged from every earlier sprint's own discipline: rule-based is still the *default* everywhere, not AI. Every LLM-backed feature is opt-in, falls back to a deterministic rule-based implementation on any failure, and candidate generation/input values/anomaly classification stay fully deterministic. "AEGIS v1.0" means the roadmap's original six deliverables are real and shipped — it doesn't mean AI-by-default; that would be a different, larger milestone this project hasn't taken on.

Next Milestone

**Stage 2 of Framework Adoption: Plugin Architecture** — extension points (browser/observer/finding/LLM/auth/report plugins) so external teams can add capabilities without modifying `aegis-core`. Not started. Stages 3–6 (enterprise readiness, ecosystem/CLI, performance & quality, community release) queue up after that — see `AEGIS_ROADMAP.md`. Separately, the older v1.1+ candidates from before this initiative are still real but not scheduled: an AI-backed decision-maker as the *default* rather than opt-in; cross-run experience persistence; a published/tagged release now that the version line has moved to `1.0.0-SNAPSHOT`.