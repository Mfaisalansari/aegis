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

## Sprint 27

### Learning

Learn from execution history.

Increase confidence for successful actions.

Reduce confidence for failing actions.

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
v0.1
```

Current State

**Rule-Based Autonomous QA Agent, with an opt-in AI-assisted decision point (`explorationStrategy: llm`, Sprint 26)**

Next Milestone

**Generic Exploratory AI Agent** — the LLM strategy is real but narrow (candidate scoring only, opt-in, falls back to the rule-based default); the milestone isn't reached until an AI-backed decision-maker is the default, not an option, and covers more of the pipeline than picking among pre-validated candidates.