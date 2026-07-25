# AEGIS Roadmap

> Version: 1.0 (Architecture Freeze)
>
> Status: AEGIS v1.0 — every roadmap phase complete
>
> Last Updated: 2026-07-25

---

# Vision

AEGIS (Autonomous Exploratory QA Agent) is an AI-driven exploratory QA platform that autonomously explores applications, learns from previous executions, and continuously improves its testing strategy.

---

# Project Goals

- Autonomous exploratory testing
- Adaptive decision making
- Intelligent learning
- Coverage analysis
- Bug detection
- AI-assisted reasoning
- Production-ready reporting

---

# Architecture Status

**Status:** 🔒 Frozen

The following components are considered stable and must **not** be redesigned without an architecture review.

## Stable Components

- MissionEngine
- MissionController
- Planner
- DecisionEngine
- GoalReasoner
- CandidateGenerator (`CandidateActionGenerator` and its implementations)
- CandidateFilter
- ActionScorer (including `ActionScorerRegistry`)
- Executor
- ExecutionMemory
- WorldModel
- Observer
- AnomalyDetector
- InputValueResolver (including `InputValueResolverRegistry`)
- ElementActionMapper
- CandidateConfidenceEstimator
- Report generators (`ExplainabilityReportGenerator`, `HtmlExplainabilityReportGenerator`)
- LlmChatClient

---

# Development Rules

## Rule 1

No architecture changes during milestone implementation.

---

## Rule 2

Every milestone must be fully completed before moving to the next.

---

## Rule 3

Every implementation must compile before the next milestone starts.

---

## Rule 4

Every change must align with the frozen architecture.

---

## Rule 5

No partial implementations.

Every feature will be delivered as a complete, compile-ready change set.

---

# Milestones

---

# ✅ Phase 0 – Foundation

## Status

Completed

## Deliverables

- Project structure
- Domain model
- Configuration
- Logging
- Build system

---

# ✅ Phase 1 – Core Autonomous Engine

## Status

Completed

### Mission

- MissionEngine
- MissionContext
- MissionController
- ExecutionState

### Observation

- Observer
- Observation

### Planning

- Planner
- DecisionEngine
- GoalReasoner

### Reasoning

- CandidateGenerator
- CandidateFilter
- ActionScorer

### Execution

- Executor
- Browser integration

### Memory

- ExecutionMemory
- WorldModel
- VisitedStateMemory

---

# 🟢 Phase 2 – Learning Framework

## Status

Completed (2026-07-24)

### Completed

- Experience / ExperienceOutcome / ExperienceRepository / InMemoryExperienceRepository / LearningEngine / PatternAnalyzer / PatternStatistics / LearningResult (bootstrap classes, this milestone's starting point).
- Fixed a correctness bug found during review: `Action` is a record whose `equals()`/`hashCode()` include a random `UUID id` and `Instant createdAt`, both freshly generated every time a candidate is regenerated — so grouping or keying by the raw `Action` (as `PatternAnalyzer` and `LearningResult` originally did) never matched "the same logical action" twice. Every group had exactly one member and `successRate` was always 0.0 or 1.0, making the whole pipeline a no-op. Fixed via `ActionKey.of(action) = type + "|" + target`, the same stable-identity convention `ExecutionMemory` already used for the same reason.
- `ExperienceRecorder` wired into `DefaultMissionEngine`: every executed action's own outcome (SUCCESS on success, ERROR on a thrown exception) is now recorded as a real `Experience`, alongside — not instead of — the existing per-iteration resilience handling (a thrown action still becomes a HIGH `Finding` and the mission keeps going).
- `HeuristicCandidateConfidenceEstimator` now takes a `LearningEngine` and applies a clamped `[-0.20, +0.20]` adjustment on top of its static heuristic score, keyed by `(ActionType, element locator)` — the same identity `ActionKey` uses, looked up before an `Action` object exists yet.
- Full wiring in `EngineFactory`: `InMemoryExperienceRepository` → `DefaultPatternAnalyzer` → `DefaultLearningEngine` → `DefaultExperienceRecorder`, shared with the confidence estimator so later iterations of the same mission benefit from what earlier iterations learned.
- Verified live against saucedemo.com: a real mission run recorded 3/3 executed actions as SUCCESS Experiences with real measured durations, and `learn()` correctly returned `+0.20` for each (100% success rate bucket).
- 12 new unit tests added (`DefaultPatternAnalyzerTest`, `DefaultLearningEngineTest`, `HeuristicCandidateConfidenceEstimatorTest`) — full suite now 88/88 passing (was 76/76).

### Note — scope of "persistence"

The repository is in-memory and scoped to a single mission run, the same convention already established for `WorldModel` and `VisitedStateMemory` elsewhere in this architecture (see Stable Components). Learning does not accumulate across *separate* mission runs — each run starts with no prior history. Cross-run persistence (a real datastore) was not part of this milestone's original deliverables and is left as a distinct, future follow-up if wanted, not something quietly assumed done here.

---

# 🟢 Phase 3 – Adaptive Decision Making

## Status

Completed (2026-07-24)

## Goals

- Historical success scoring — done as part of Phase 2's Learning Framework: `PatternAnalyzer` computes a per-action success rate from recorded `Experience`s, grouped by the stable `ActionKey` (type + target) identity.
- Confidence adjustment — done as part of Phase 2: `HeuristicCandidateConfidenceEstimator` applies `LearningEngine`'s adjustment (`[-0.20, +0.20]`, bucketed by success rate) on top of its static heuristic score, for every candidate, every strategy.
- Failure penalty — done as part of Phase 2: the same bucketed adjustment goes negative for a low success rate (an action that's failed repeatedly gets deprioritized without a hand-written rule for why).
- Adaptive exploration — done as a consequence of the above: since confidence adjustment happens in `HeuristicCandidateConfidenceEstimator`, upstream of every `ActionScorer` (not a special "adaptive" strategy of its own), *every* exploration strategy automatically adapts its choices to what's been learned this mission — not just a dedicated one.
- Exploration bonus (2026-07-24, the one goal not already covered by Phase 2/4): a candidate with *zero* recorded `Experience` — never tried, not merely landed in the neutral 50-75% success-rate bucket — now gets a flat `+0.05` bonus. New `LearningResult.hasExperience(type, target)` distinguishes "no data" from an explicit `0.0` adjustment, since `adjustmentFor()` alone can't tell them apart (`getOrDefault(..., 0.0)` collapses both to the same value). This is the confidence-estimation-layer counterpart to Phase 4's `CoverageAwareActionScorer`: that one rewards a candidate *confirmed* (by WorldModel history) to lead somewhere new; this one rewards a candidate we simply have *no information about yet* — a different, complementary signal. 7 new/updated unit tests (`LearningResultTest`, updated `HeuristicCandidateConfidenceEstimatorTest`); full suite: 116/116 passing. Verified live against saucedemo.com: real report showed `(learning-adjusted +0.05, never tried yet this mission)` on every first-time candidate.

---

# 🟢 Phase 4 – Exploration Intelligence

## Status

Completed (2026-07-24)

## Already in place

- Dynamic strategy switching, but manual/per-mission, not automatic or coverage-aware: 8 pluggable `explorationStrategy` values (`greedy`, `random`, `risk-based`, `breadth-first`, `depth-first`, `form-first`, `navigation-first`, `llm`) selectable via mission parameter.
- Coverage-aware exploration / a first cut of state prioritization (2026-07-24): fixed a bug found during review that made `KnownDeadEndCandidateFilter` (a frozen Stable Component) permanently unreachable dead code. `AlreadyExecutedCandidateFilter` blocked any candidate whose `(type, target)` had *ever* been executed, mission-wide, with no notion of which state it was tried from — so nothing with WorldModel history could ever reach the dead-end check, which only had data for actions already executed. Fixed by scoping `ExecutionMemory` per state (an exact repeat from the same state is still blocked — that's what prevents pointless looping) and changing `WorldModel.knownDestinationsOf` to look up by action identity across *all* recorded source states, not just the current one. Net effect: a candidate reached from a state it's never been tried from (e.g. a persistent nav link seen on a new page) is now actually evaluated against cross-state history and pruned if every destination it's ever led to is already visited — real coverage-aware pruning, not a no-op. Verified via 11 new/updated unit tests (`ExecutionMemoryTest`, `AlreadyExecutedCandidateFilterTest`, `WorldModelTest`, `KnownDeadEndCandidateFilterTest`) plus a live 10-iteration run against saucedemo.com with no crashes across state revisits. Full suite: 99/99 passing.
- Exploration metrics (2026-07-24): a new `ExplorationCoverage` (distinct interactive elements discovered across every observation this mission vs. distinct elements actually the target of an executed action, as a percentage), computed once in `MissionReportData` and surfaced in both report formats — a line in the text report, a tile in the HTML report. Deliberately scoped to elements-on-pages-actually-visited coverage, not site-wide page coverage (that's Phase 5's "Page coverage," a different, harder metric needing a sitemap or DOM diffing this project doesn't have). Verified live against saucedemo.com: real report showed "3/32 discovered interactive elements exercised (9%)" after a 3-step login. 4 new unit tests; full suite: 103/103 passing.
- State prioritization as an active ranking signal (2026-07-24): new `CoverageAwareActionScorer`, opt-in via `explorationStrategy: coverage-aware`. Uses the same `WorldModel.knownDestinationsOf` evidence `KnownDeadEndCandidateFilter` prunes with, but as a reward instead of a cut: a candidate confirmed (from history at any prior state) to lead somewhere not yet visited gets a fixed `+0.15` bonus over its plain heuristic confidence; everything else (the common case — no history either way) is unaffected. Deliberately two-tier, not a full "rank by how unexplored" — a candidate whose every known destination is already visited never reaches a scorer at all, so there's no third tier to rank. 4 new unit tests (`CoverageAwareActionScorerTest`); full suite: 107/107 passing. Verified live against saucedemo.com with no crashes.
- Automatic dynamic strategy switching (2026-07-24): new `AdaptiveActionScorer`, opt-in via `explorationStrategy: adaptive`. Wraps two existing scorers (`greedy` normally, `coverage-aware` once stuck) rather than being a new standalone strategy — the only genuinely new logic is *when* to switch: "stuck" is recomputed fresh from `MissionContext`'s own observation history on every call (no newly discovered state in the last 3 iterations), so it self-corrects the moment a new state turns up again with no separate reset needed. 4 new unit tests (`AdaptiveActionScorerTest`, including one proving the switch-back behavior); full suite: 111/111 passing. Verified live against saucedemo.com with no crashes (that particular run never actually got stuck, so it stayed on `greedy` throughout — expected; the switching logic itself is what the unit tests pin down deterministically).

Phase 4 is now feature-complete against its original goal list. Its remaining honest limitation: `STUCK_THRESHOLD` (3 iterations) is a fixed heuristic constant, not tuned or made configurable — a reasonable default, not a claim it's the "right" number for every site.

---

# 🟢 Phase 5 – Coverage Intelligence

## Status

Completed (2026-07-24)

## Already in place

- Navigation graph / transition graph: `WorldModel` records `NavigationEdge` (fromState, actionType, actionTarget, toState) as the mission runs, and the HTML explainability report already renders it as an interactive SVG graph.
- Page coverage (2026-07-24): new `PageCoverage` — the same discovered-vs-interacted element computation as Phase 4's `ExplorationCoverage`, but broken down per page URL instead of blended mission-wide. "The login page got 100% coverage but the inventory page only got 17%" is actionable in a way one blended number isn't. Surfaced in both report formats — a table in the text report, a table with progress bars in the HTML report. Still elements-on-a-visited-page coverage, not a claim about what fraction of the whole site that page represents — a sitewide "% of all pages visited" metric remains impossible without a sitemap or crawl, which this project deliberately doesn't have (see ARCHITECTURE.md).
- DOM coverage: this is what `ExplorationCoverage` (Phase 4) and the new per-page `PageCoverage` above already measure, in the only sense this architecture can — interactive DOM elements the Observer actually saw vs. exercised. Elements that never appear in any observation (behind a modal never opened, a tab never clicked) are invisible to AEGIS entirely and can't be measured against; that's an honest, permanent limitation of "coverage" here, not a gap to be closed by more code.
- Heat maps (2026-07-24): the HTML report's SVG navigation graph now visually encodes revisit frequency — node radius and fill-opacity scale with how many times a state was observed (`MissionReportData.stateVisitCounts`), and edge stroke-width scales with how many times that exact transition (type + target, not just from/to) was traversed, collapsing repeated identical transitions into one thicker line instead of several overlapping near-duplicate arcs. Both a hover tooltip ("visited N times") and an inline "(×N)" edge label make the underlying number explicit, not just implicit in the visual. Verified live against saucedemo.com: a state visited 5 times rendered at radius 42.0/opacity 0.40 versus a base 26.0/0.0 for a single visit — exactly matching the formula.
- 2 new unit tests (`MissionReportDataTest` additions for `PageCoverage`/`stateVisitCounts`); full suite: 118/118 passing (was 116/116).

Phase 5's honest remaining limitation, shared with Phase 4's page-coverage note above: nothing here can measure coverage against the *whole site* — only against what AEGIS has actually observed. That's an architectural boundary (no sitemap/crawl), not an oversight.

---

# 🟢 Phase 6 – Bug Intelligence

## Status

Completed (2026-07-24)

## Goals

- **Bug clustering** and **Duplicate detection** (2026-07-24, one deliverable covers both): new `com.aegis.core.bug` package — `BugFingerprint.of(finding)` normalizes a Finding's message (collapsing embedded digits, UUIDs, and URLs) into a coarser identity than exact-string equality, so "add-to-cart failed for item 4" and "...for item 7" fingerprint as the same underlying bug. `DefaultBugClusterAnalyzer` groups all of a mission's Findings by that fingerprint into `BugCluster`s (representative summary, occurrence count, distinct URLs, first/last seen), sorted most-severe-first. This sits *above*, not instead of, `BrowserSignalAnomalyDetector`'s existing exact-signature dedup (a Stable Component, untouched) — that layer already collapses byte-identical repeats to one Finding; this layer clusters the *remaining*, non-identical-but-related ones.
- **Severity prediction** (2026-07-24): a cluster's severity is the worst severity among its own members, escalated one level (never past CRITICAL) once it has recurred at least 3 times — a problem that keeps happening is a stronger signal than any single occurrence's independently-assigned severity.
- **Root cause grouping** (2026-07-24): a cluster spanning more than one distinct URL is flagged (`BugCluster.spansMultiplePages()`) as likely sharing a root cause (a broken shared component) rather than being a page-specific glitch — inferred from co-occurrence across pages, not real causal analysis; the report is explicit about that ("may share a root cause", not "is caused by").
- Surfaced in both report formats — a "Bug Clusters" section above the existing flat findings list in each. Purely a report-layer feature (computed in `MissionReportData.from()`, same pattern as Phase 4/5's coverage metrics) — no changes to the live mission loop, `AnomalyDetector`, or any other Stable Component.
- 14 new unit tests (`BugFingerprintTest`, `DefaultBugClusterAnalyzerTest`, plus a `MissionReportDataTest` addition). Full suite: 132/132 passing (was 118/118).

Verified live against saucedemo.com: a genuine `TimeoutError` mid-run correctly appeared as a single-occurrence, non-recurring HIGH-severity cluster in both the real text and HTML reports.

Honest limitation carried over from the design: fingerprinting is regex normalization, not semantic understanding — two findings that differ only in wording (not in any embedded digit/UUID/URL) will still fingerprint separately even if a human would call them "the same bug". Root-cause grouping is a co-occurrence heuristic, not causal inference.

---

# 🟢 Phase 7 – Reporting

## Status

Completed (2026-07-24) — original scope plus a full 3-stage "Reporting v2" rebuild (user-specified)

## Completed (original scope)

- Mission Report (`ExplainabilityReportGenerator`, text)
- Reasoning Report (per-step candidate/selection detail, confidence margin over runner-up)
- HTML Report (`HtmlExplainabilityReportGenerator`) — self-contained, interactive SVG navigation graph, executive summary (goal + plain-English outcome), severity-ranked findings with plain-English explanations, collapsible reasoning steps
- Coverage Report — folded into Phase 5's `PageCoverage`/`ExplorationCoverage` rather than shipped as a separate artifact; no longer tracked as a distinct remaining item

## Reporting v2 (2026-07-24)

A ground-up redesign against an explicit, user-authored spec: the report should answer eight questions on its own — what was the mission, what did the agent do, why each choice, what it discovered, what bugs it found, what it learned, how much was explored, what to do next — for readers ranging from QA Engineer to Manager. Explicitly frozen and untouched: `MissionEngine`, `Planner`, `DecisionEngine`, `GoalReasoner`, `LearningEngine`, `WorldModel`, `Observer`, `ActionScorer`, `ExecutionMemory` — reporting only reads their already-recorded output, never influences them.

**Stage 1 (complete):**
- **Mission Timeline** — the "heart" of the rebuild: a chronological, replayable reconstruction of the entire run (Mission Started → Observed → Reasoning → Execution Successful/Failed → Findings → Mission Finished), built purely from timestamps already present on existing records (`Observation.capturedAt`, `ReasoningStep.timestamp`, `Experience.createdAt`, `Finding.detectedAt`). New `TimelineEvent`/`TimelineEventKind`. Getting accurate per-action Execution Successful/Failed events required widening `EngineFactory.create()`'s return type (`EngineFactory.CreatedEngine`, bundling the `MissionEngine` with the `ExperienceRepository` it already writes to) since `EngineFactory` itself is composition/wiring, not a frozen reasoning component.
- **Executive Summary redesign** — Mission, Duration, Result, Coverage, Findings, Bug Clusters, a one-line Learning teaser, and the Recommendation all in one place at the top of both report formats — "a manager should stop here."
- **Improved Statistics Dashboard** — Actions Executed, Pages, States, Transitions, Coverage, Avg Confidence, Bug Count, Clusters, Duration as a card grid.
- **Better reasoning visualization** — a `[learned]`/`learned` badge on any step or candidate whose confidence carries a Phase 2/3 learning adjustment (parsed from the existing reasoning text, not a new pipeline field — CandidateAction's shape wasn't touched), plus a `winner` badge on the selected candidate in the HTML candidate table.
- New `LearningSummary` (new/updated actions, confidence improved/declined), computed by reusing `DefaultPatternAnalyzer`'s own `ActionKey` grouping over this mission's `Experience` list — the same grouping `LearningEngine` itself uses, read back afterward rather than duplicated.
- 13 new unit tests (`MissionReportDataTimelineTest`); full suite: 183/183 passing (was 170/170). Verified live against saucedemo.com: real report showed a genuine, readable replay of a 3-step login with correct new-state/revisit detection, execution outcomes, and a "3 new, 0 updated action(s) — 3 improved, 0 declined" learning line.

**Stage 2 (complete, 2026-07-24):**
- **Coverage visualization** — a "Pages" checklist (✓ per discovered page) added alongside the existing per-page coverage bars, with an explicit caption noting there's no sitemap so a page AEGIS never found can't be listed as "not visited" — no fabricated "expected pages" list.
- **Full Learning section** — `LearningSummary` extended with `actionPerformance` (every distinct action this mission touched, sorted best success rate first, reusing the exact same `PatternStatistics` `DefaultPatternAnalyzer` already computes). Best/Worst Performing Actions render as two ranked tables (capped at 3 each) in both report formats.
- **Findings Dashboard** — new `FindingCategory` enum (`JAVASCRIPT`, `NETWORK`, `NAVIGATION`, `TIMEOUT`, `STABILITY`, `OTHER`) and a `categoryOf(BugCluster)` mapping from each cluster's underlying signal type. Deliberately excludes `ACCESSIBILITY`/`PERFORMANCE` — AEGIS has no detector for either, so labeling findings that way would be fabricated, not derived. Bug clusters (Phase 6) now group one level further, by category, in a dedicated section.
- **Improved recommendation presentation** — pulled out of the Executive Summary paragraph into its own highlighted callout section, immediately below the summary — presentation only, `RecommendationEngine`/`LlmRecommendationEngine` (Phase 8) themselves untouched.
- 4 new unit tests (`MissionReportDataStage2Test`). Full suite: 187/187 passing (was 183/183). Verified live against saucedemo.com with a real timeout finding present: Learning correctly showed 10 new experiences, 9 confidence-increased/1 reduced, and ranked the failed action (`CLICK [id='inventory_sidebar_link']` — 0% success) at the top of Worst Performing Actions; the Findings Dashboard correctly categorized the same finding as `TIMEOUT`.

**Stage 3 (complete, 2026-07-24):**
- **Screenshot hooks** — `TimelineEvent` gains a nullable `screenshotPath` (5th field), via a new 5-arg constructor; every existing call site keeps using the original 4-arg one and gets `null` automatically. Deliberately doesn't capture anything today — that would mean instrumenting the live execution path (`Observer` or the browser layer), both frozen. Both report generators already render the path when present (an `<img>` in HTML, a `[screenshot] <path>` line in text), so wiring in a real capture mechanism later is purely additive.
- **Interactive HTML improvements** — a sticky table-of-contents nav (`id` added to all 12 sections) since the report has grown past a dozen sections, and a Mission Timeline filter (All / Observations / Reasoning / Executions / Findings toggle buttons, pure client-side JS, no new data).
- **JSON export** — new `JsonReportGenerator`, same overload chain as the other two formats. Built by hand with Jackson's tree API (`ObjectMapper.createObjectNode()`) — the same low-level approach `OpenAiCompatibleChatClient`/`LlmActionScorer`/`LlmMissionParser` already use elsewhere in this codebase — rather than reflecting the `MissionReportData` record directly, which would need an extra `jackson-datatype-jsr310` dependency for `Instant`/`Duration` and would leak internal Java shapes into what should be an intentionally-designed, stable export schema. `MissionRunner` now writes all three formats (`.txt`/`.html`/`.json`) unconditionally on every run.
- **PDF export — deliberately not built.** Judgment call, as flagged as conditional in the original ask ("if it still aligns with the roadmap"): a bespoke PDF renderer needs a real new dependency (iText's current versions are AGPL/commercial-licensed; OpenHTMLtoPDF is LGPL) for something a browser's own "Print to PDF" already does for free against the existing self-contained HTML report. That doesn't align with this project's own stated non-functional requirement — "no external dependencies" — for marginal benefit over what's already achievable with zero new code.
- 8 new unit tests (`TimelineEventTest`, `JsonReportGeneratorTest`). Full suite: 195/195 passing (was 187/187). Verified live against saucedemo.com: all three report formats written successfully in one run; the JSON output parsed as valid JSON with correct mission/coverage/timeline/learning/recommendation data; the HTML report's table-of-contents links and timeline filter buttons rendered with all 12 matching section IDs present.

**Reporting v2 is now complete** (all 3 stages). The eight questions from the original success criteria — what was the mission, what did the agent do, why each choice, what it discovered, what bugs it found, what it learned, how much was explored, what to do next — are all answerable from the report alone, in three formats (text, HTML, JSON), without reading a single log line.

---

# 🟢 Phase 8 – AI Intelligence

## Status

Completed (2026-07-24)

## Completed

- AI reasoning for one decision point: `LlmActionScorer`, opt-in via `explorationStrategy: llm`, provider-agnostic (`OpenAiCompatibleChatClient` — Ollama/LM Studio/vLLM/OpenAI all speak the same `/chat/completions` shape), falls back to the deterministic default on any failure. Verified live end-to-end.
- **Bug explanation** (2026-07-24): new `BugExplainer` interface with two implementations — `RuleBasedBugExplainer` (default, just returns the existing fixed-per-signal-type sentence `MissionReportData.explain()` already produced) and `LlmBugExplainer` (opt-in, asks a real model to write a short triage note for a `BugCluster` — Phase 6's clustering output — using the same provider-agnostic `LlmChatClient`/fallback-on-any-failure discipline as `LlmActionScorer`). Explanations are computed once per cluster inside `MissionReportData.from(...)`, not per report format, so generating both text and HTML from the same data never calls the model twice for the same bug. Opt-in via a new `AEGIS_LLM_BUG_EXPLANATIONS=enabled` environment variable (not a mission parameter — same reasoning as the rest of `AEGIS_LLM_*` config: mission parameters get serialized into every report). Verified live end-to-end against a fake OpenAI-compatible server (no real provider reachable in this sandbox, same verification method the original `LlmActionScorer` integration used).
- **Recommendation engine** (2026-07-24): new `RecommendationEngine` interface, same two-implementation shape — `RuleBasedRecommendationEngine` (default: points at the single highest-severity `BugCluster`, already sorted first by `DefaultBugClusterAnalyzer`, e.g. "3 issue(s) found. Highest priority: [CRITICAL] ... (occurred 2 times).") and `LlmRecommendationEngine` (opt-in, asks a model to look across *all* of a mission's bug clusters at once — not one at a time like `LlmBugExplainer` — and write a short prioritized recommendation; skips the model call entirely when there are no clusters, since there's nothing to prioritize). Opt-in via its own `AEGIS_LLM_RECOMMENDATIONS=enabled` variable, independent of bug explanations — a mission run can want one AI feature without the other. Surfaced prominently in both report formats, directly under the outcome summary (the "so what do I do" a reader wants before the raw findings list). Verified live end-to-end with the same fake-server technique — a request-body-inspecting fake server confirmed the two AI features (explanation vs. recommendation) are genuinely independent calls, not one reused response.
- **Natural language missions** (2026-07-24): new `MissionParser` interface — `RuleBasedMissionParser` (default: a bare URL regex against the raw instruction text, the one thing extractable without understanding the sentence; everything else — goal, success condition, credentials — left unset) and `LlmMissionParser` (asks a model to extract `baseUrl`/`goal`/`successUrlContains`/`username`/`password` as JSON from a free-text QA instruction). The model's `baseUrl` is never trusted blindly — validated as a real `http(s)` URL with a host via `java.net.URI` before use, same as any other LLM output feeding into something that gets executed; an invalid or missing URL falls back exactly like a network failure would. New `NaturalLanguageMissionMain` launcher entry point demonstrates it against a hardcoded instruction. Verified live twice: (1) with no reachable LLM server, correctly fell back to the bare-URL extraction and ran a real (unsuccessful, since no credentials were extracted) mission without crashing; (2) against a fake server returning a complete extraction, produced a `Mission` whose `baseUrl`/`successUrlContains`/`username`/`password` were all correct, and the resulting real mission run genuinely logged into saucedemo.com and reached `SUCCESS`.
- **AI mission planning** (2026-07-24): new `MissionPlanner` interface — `RuleBasedMissionPlanner` (default: a mechanical plan derived from which mission parameters are set — "there's a baseUrl, so navigate there; there's a username, so expect a login step") and `LlmMissionPlanner` (opt-in: asks a model, given the mission's name/description/parameters, to sketch 3-6 short ordered high-level steps). Scoped deliberately narrow to keep this safe despite being "planning": the plan is generated *before* the mission runs and is purely advisory — nothing in the live reasoning pipeline (`GoalReasoner`, `ActionScorer`, `CandidateFilter`) ever reads it, so a bad or nonsensical plan step is a display-quality problem, never something that gets executed against a real page (unlike `LlmActionScorer`'s candidate index or `LlmMissionParser`'s `baseUrl`, which both need strict validation because they *do* feed into execution). Threaded through as pre-computed data (`MissionReportData`'s 5-arg `from(...)` overload takes an already-built `MissionPlan`, unlike `BugExplainer`/`RecommendationEngine` which are invoked internally) since the plan has to exist before the `MissionContext` it would otherwise be derived from is even created. Opt-in via its own `AEGIS_LLM_MISSION_PLANNING=enabled` variable. Surfaced as a new "Mission Plan" section in both report formats, right after the summary. Verified live end-to-end: with mission planning enabled against a fake server, the AI-generated plan appeared correctly in the console output and the real report, and — critically — the mission still ran on its real, independently-configured credentials and reached `SUCCESS`, confirming the plan genuinely never influences execution.
- 12 new unit tests (`RuleBasedMissionPlannerTest`, `LlmMissionPlannerTest`, two `MissionReportDataTest` additions). Full suite: 170/170 passing (was 132/132 before this full run of Phase 8 increments).

This closes out every goal Phase 8 – AI Intelligence originally listed.

---

# 🟢 Phase 9 – Self-Healing

## Status

Completed (2026-07-25)

## Design

All four goals land in one place: a new `com.aegis.core.resilience` package that decorates `Browser` — confirmed not on the Stable Components list, unlike `Executor`/`ActionExecutor`/`ActionHandler` above it and `MissionEngine` above that. Every consumer of `Browser` (`Observer`, the action handlers, `AnomalyDetector`, `DefaultMissionEngine` itself) is frozen and stayed completely untouched: `Browser`'s contract (a call either completes or throws) is unchanged, only how often it throws. `EngineFactory` — already established as composition/wiring, not architecture — does the one-line wrap.

- **Retry engine** — `SelfHealingBrowser` retries a failed call once (after a short delay) before giving up or trying to heal. Applies to every element action (click/doubleClick/raceClick/type/select/scrollTo) and every navigation call (navigate/refresh/goBack).
- **Locator healing** — new `LocatorHealer`: pure string logic, no DOM access, scoped to exactly the 3 locator shapes AEGIS itself generates (`PlaywrightBrowser.buildBestLocator`): `[id='X']` / `[name='X']` heal to a substring match (`[id*='X']`, then `:nth-match([id*='X'], 1)` if that's still ambiguous) — the common real-world case of a framework appending a generated suffix to an otherwise-stable id; `:nth-match(tag, N)` heals to the neighboring indices (N-1, N+1) — the DOM gained or lost one matching element since the locator was generated. If the locator isn't one of these 3 shapes, there's nothing to heal and the retry's failure is what gets rethrown.
- **Navigation recovery** — the same retry-once treatment applied to `navigate`/`refresh`/`goBack`, covering a transient network blip without the healing logic (there's no locator involved).
- **Dialog recovery** — upgraded `PlaywrightBrowser`'s dialog handler from blanket-dismiss to type-aware: `prompt` is now accepted with a blank answer (dismiss returns `null`, which aborts most prompt-gated flows outright — accepting with `""` lets the flow continue while committing to nothing). `confirm`/`beforeunload` deliberately keep dismissing — flipping those to auto-accept would reverse an explicit, already-documented safety call (auto-accepting a "delete this?" confirm is real-damage risk against a live site under test), so this phase adds intelligence only where it doesn't fight that principle. `alert` is unchanged (no real choice either way).
- Retry/heal attempts use a short, explicit bounded timeout (`Browser` gained additive default-method overloads — `click(locator, Duration)` etc. — implemented for real in `PlaywrightBrowser` via Playwright's per-call `Options.setTimeout`), not Playwright's full default wait: the first attempt already pays that cost once, so repeating it verbatim on every retry would let a genuinely-broken locator stall a mission for minutes instead of seconds.

## Verification

17 new unit tests: `LocatorHealerTest` (pure locator-shape → candidate-list logic, including the escape/unescape round-trip for ids containing a quote) and `SelfHealingBrowserTest` (a hand-written fake `Browser` recording every call, proving the exact retry → heal → give-up-and-rethrow sequence, that navigation retries once and no more, and that read-only/lifecycle methods pass straight through with zero retry logic). Full suite: 212/212 passing.

Live-verified against a real Playwright browser and a real local page (not just the fake double): a button/input whose real `id` had gained a `-v2`-style suffix since the locator was generated — `browser.click("[id='submit-btn-abc123']")` against a live DOM where the real id is `submit-btn-abc123-v2` genuinely fails the exact match, retries, heals to `[id*='submit-btn-abc123']`, and the click actually lands (confirmed via a real page-title mutation the click's own `onclick` triggers) — same for `type()`. A third scenario (an unhealable `text=` locator) confirmed the give-up path correctly rethrows Playwright's own `TimeoutError` rather than swallowing it. Also re-ran the existing SauceDemo login mission end-to-end through the real `EngineFactory` wiring (now including `SelfHealingBrowser`) — still `SUCCESS`, confirming the happy path is unaffected.

---

# 🟢 Phase 10 – AEGIS v1.0

## Status

Completed (2026-07-25)

## Deliverables

Five of the six were already delivered by earlier phases — this milestone's Review step is confirming each still holds, not rebuilding it:

- ✅ **Autonomous exploratory testing** — Phase 1 (Core Autonomous Engine): the full Observe → Reason → Decide → Execute loop, no scripted steps.
- ✅ **Adaptive learning** — Phase 2 (Learning Framework) + Phase 3 (Adaptive Decision Making): `ExperienceRepository`/`LearningEngine` feed `HeuristicCandidateConfidenceEstimator`, and `AdaptiveActionScorer` switches strategy mid-mission on its own.
- ✅ **Coverage intelligence** — Phase 5: `CoverageAwareActionScorer`, `WorldModel`-backed dead-end pruning, `ExplorationCoverage`/`PageCoverage` in every report.
- ✅ **AI reasoning** — Phase 8: four independent, opt-in LLM-backed features (`LlmActionScorer`, `LlmBugExplainer`, `LlmRecommendationEngine`, `LlmMissionParser`, `LlmMissionPlanner` — five, technically), every one with a rule-based fallback and never trusted with anything more than it validates.
- ✅ **Production reporting** — Phase 7 + Reporting v2: text/HTML/JSON, Mission Timeline, coverage, learning, findings dashboard, all frozen-component-safe.
- ✅ **Stable public API** — new this milestone, see below. This was the one genuine gap: every other deliverable already existed, but there was no actual public entry point for embedding AEGIS — `MissionRunner`, the class that ran a mission end-to-end and generated all three reports, was package-private inside `aegis-launcher`. A consumer had nothing to call short of copy-pasting it.

## Design: the public API

New public facade in `aegis-core` (the reusable library module, not `aegis-launcher`, which is example/CLI code): `com.aegis.core.Aegis.run(Mission)` returns a new `AegisReport` record — the `MissionResult`, the `MissionPlan`, and all three report formats as in-memory `String` content. Deliberately **not** writing to disk: where (or whether) to persist a report is the caller's decision, not the library's — `aegis-launcher`'s `MissionRunner` now delegates to `Aegis.run(...)` and does the file-writing/console-printing itself, exactly as before from every `*Main` class's point of view.

This is the actual meaning of "stable" here: `Mission`, `Aegis`/`AegisReport`, `MissionResult`, and the three report generators' `generate(...)` methods are the versioned external contract going forward — changes should be additive (new overloads), not breaking. This is a different, narrower kind of "stable" than the Architecture Status section's Stable Components list above: that list is about internal reasoning components not being redesigned; this is about what an external caller can rely on. `EngineFactory`'s internal wiring stays exactly as un-promised as it always was.

6 new tests (`AegisReportTest` plus the existing suite unaffected). Full suite: 213/213 passing.

## Verification

Live-verified with a dedicated harness compiled and run against **only** the `aegis-model` and `aegis-core` build outputs — `aegis-launcher` deliberately absent from the classpath — proving a real external consumer can embed AEGIS with nothing but the library module and one call. Ran a single capstone mission exercising every major capability at once through `Aegis.run(...)` alone: `explorationStrategy: adaptive`, `interruptions`/`doubleClicks`/`raceConditions` all enabled, all three AI report features (`AEGIS_LLM_MISSION_PLANNING`/`_BUG_EXPLANATIONS`/`_RECOMMENDATIONS`) enabled against a fake OpenAI-compatible server, against a real saucedemo.com login. Result: `SUCCESS`, the AI-generated mission plan showed up correctly in the returned report content (confirming the LLM toggle wiring survived the refactor), and all three report formats had the expected shape. Bug-explanation/recommendation markers correctly did not appear — this run found zero bugs, so there was nothing for either feature to fire on; both were already proven working against a real anomaly in Sprint 32/33's own live verification.

Also bumped every module's Maven version from `0.1.0-SNAPSHOT` to `1.0.0-SNAPSHOT` — leaving it at `0.1.0-SNAPSHOT` while this document declared "AEGIS v1.0: Completed" would be a genuine inconsistency. Still a SNAPSHOT: no tag, publish, or release was performed — that's a separate, deliberate action for the user to take when ready, not something to do unilaterally as part of finishing a roadmap milestone.

**This completes Phase 10 – AEGIS v1.0, and with it every phase on this roadmap.**

---

# 🟢 Post-v1.0 — Real Screenshot Capture

## Status

Completed (2026-07-25)

## Note

Not one of the original 10 phases — this is the first item picked off README's "Next Milestone" candidate list after v1.0. Reporting v2 (Phase 7) shipped `TimelineEvent.screenshotDataUri` (then named `screenshotPath`) as a data-model-only hook, deliberately not wired to live capture at the time because that meant instrumenting the browser layer, and at the time of Reporting v2 the working assumption was that layer was frozen along with `Observer`. Phase 9 (Self-Healing) later established that `Browser` specifically is *not* on the Stable Components list — only `Observer` is — which is what makes this possible without an architecture review.

## What shipped

- `Browser` gained an additive `screenshotPng()` default method (empty array = unsupported), implemented for real in `PlaywrightBrowser` via `page.screenshot()`.
- `SelfHealingBrowser` (already the one non-frozen layer every browser call flows through, per Phase 9) now captures one screenshot after every element action and navigation call — success or failure, via try/finally — exposed via a new `capturedScreenshots()` method. A capture failure never masks the real action's own outcome.
- `EngineFactory.CreatedEngine` gained a third field, `Supplier<List<ScreenshotSample>> screenshots`, read by `Aegis.run()` after `execute()` finishes — same "populated during execution, read after" pattern already established for `experienceRepository`.
- `MissionReportData.buildTimeline` matches each EXECUTION event to its nearest captured screenshot by timestamp (1-second tolerance, so a WAIT action — which never touches the browser — doesn't silently borrow a neighboring action's capture). Only EXECUTION events get one; observation/reasoning/finding/mission-start/finish events have no specific browser action to capture against, so attaching one there would overclaim a causal link that isn't real.
- Renamed `TimelineEvent.screenshotPath` → `screenshotDataUri` and changed its content from a (never-populated) filesystem path to a real `data:image/png;base64,...` URI — necessary for the HTML report's "self-contained, no external assets" requirement to still hold once this was actually populated. Safe to rename: `TimelineEvent` was never part of the v1.0 public API promise (only `Mission`/`Aegis`/`AegisReport`/`MissionResult`/the report generators' `generate(...)` methods are).
- Text report shows a short `[screenshot captured — see HTML/JSON report]` note instead of the raw data URI — a full base64 PNG inline would make a format meant for grepping/diffing unreadable. HTML renders a real `<img>`. JSON carries the full data URI under `timeline[].screenshotDataUri`.
- 8 new unit tests (4 in `SelfHealingBrowserTest` covering capture-on-success/failure/no-support/capture-failure-doesn't-mask-outcome; 4 in new `MissionReportDataScreenshotTest` covering nearest-match, tolerance cutoff, closest-of-multiple, and that non-EXECUTION events never get one). Full suite: 221/221 passing.

## Verification

Live-verified against a real saucedemo.com run: all 3 EXECUTION events in that run's timeline got a matched real screenshot (100% match rate, zero orphans), zero non-EXECUTION events got one, and one extracted screenshot was confirmed to be a genuine, valid PNG (correct magic bytes, visually a real saucedemo.com login page) — not placeholder or corrupt data. Confirmed the JSON export carries the full data URI and the HTML report's `<img>` tag renders it inline; the text report correctly shows the short placeholder instead of a multi-hundred-KB inline blob.

---

# 🟢 Framework Adoption — Stages 1–3 of 6

## Status

This is a separate, much larger user-authored initiative beyond the original roadmap (Phases 0–10) and the post-v1.0 items — 6 stages total, tracked here as each is picked up:

1. **Framework Adoption** — public SDK, config file, sample projects. Complete (2026-07-25).
2. **Plugin Architecture** — extension points (browser/observer/finding/LLM/identity/report plugins). Complete (2026-07-25).
3. **Enterprise Readiness** — mission/environment profiles, secret management, parallel execution, scheduling, CLI. Complete (2026-07-25).
4. **Ecosystem** — full docs (user/architecture/plugin-dev guides, API reference, FAQ), a real `aegis` CLI (`init`/`report`/`validate`/`doctor` — `run` already shipped in Stage 3), IDE templates. Not started.
5. **Performance & Quality** — benchmarking, memory/thread-safety review, API stability, dependency cleanup, test coverage. No new functionality. Not started.
6. **Community Release** — README/CONTRIBUTING/CHANGELOG/license/versioning policy, GitHub Actions, issue templates. Not started.

## Stage 1 exit criteria

*"A QA engineer with no prior knowledge can run AEGIS in under 15 minutes."* Verified live — see below.

## Design

Five of six original deliverables already existed by the time this was picked up (autonomous exploration, adaptive learning, coverage intelligence, AI reasoning, production reporting — Phases 1–8); the actual gap was that **there was no public entry point to embed AEGIS at all** — `MissionRunner`, the class that ran a mission end-to-end, was package-private inside `aegis-launcher`. Confirmed with the user before starting: "implement only application-specific pieces: Login" means **declarative config** (baseUrl/credentials/success-condition, same as existing mission parameters, just a typed surface) — not a new imperative scripted-flow capability. AEGIS's autonomous exploration engine is completely untouched by this stage.

- **New `aegis-api` module** (`com.aegis.api`), depending on `aegis-model`+`aegis-core`: `MissionBuilder` (fluent wrapper over every existing mission parameter — zero new capability), `ApplicationConfig`/`MissionConfig`/`AegisConfig` (YAML-shaped config records) + `AegisConfigLoader` (SnakeYAML, parsed into a generic tree and hand-mapped — same "no reflection-binding magic" style already used for JSON in `JsonReportGenerator`, rather than SnakeYAML's POJO binding), `AegisApplication` (the interface a consumer implements — `name()` + `config()`, nothing scripted), `Launcher` (`MissionRunner`'s logic promoted up a layer and generalized — writes reports, prints console output; `Aegis` itself stays disk-free).
- **New `com.aegis.core.browser.BrowserConfig`** (`type`, `headless`) — additive, `defaults()` matches exactly what was hardcoded before (chromium, headed). `PlaywrightBrowser` gained a constructor taking it (real chromium/firefox/webkit switching, not just a schema field); `EngineFactory`/`Aegis` gained matching overloads. The only `aegis-core` touch — doesn't affect the Stable Components list.
- **3 sample projects** under a new `samples/` aggregator (`sample-saucedemo`, `sample-orangehrm`, `sample-nopcommerce`), each a standalone module depending **only on `aegis-api`** (proves the SDK boundary for real — same proof style as the v1.0 capstone verification), with `exec-maven-plugin` preconfigured so `mvn -pl samples/X exec:java` just works.
- Public API surface as of Stage 1: `Mission`/`MissionResult` (`aegis-model`), `Aegis`/`AegisReport`/`BrowserConfig` (`aegis-core`), `MissionBuilder`/`AegisApplication`/`AegisConfig`/`ApplicationConfig`/`MissionConfig`/`AegisConfigLoader`/`Launcher` (`aegis-api`), and the 3 report generators' `generate(...)` methods. Still documentation + package convention, not JPMS — no `module-info.java` exists anywhere in the project, and introducing one would be a high-risk, high-effort detour for a usability-driven exit criterion, not a security one.
- 27 new unit tests (`BrowserConfigTest`, `MissionBuilderTest`, `AegisConfigLoaderTest`). Full suite: 246/246 passing.

## Two real bugs found and fixed along the way

Building samples against sites this project had never touched surfaced genuine, narrow, well-understood gaps — fixed under the same precedent set earlier this session (Phase 4: a frozen-component bug blocking a milestone's own deliverable is in scope to fix, an unrelated architecture change is not):

- **`PlaywrightBrowser.navigate()` only waited for `DOMCONTENTLOADED`**, unlike every other action method's `settle()`. Fine for simple pages, but OrangeHRM's Vue.js login form doesn't exist in the DOM yet at that point (confirmed live: 0 inputs/buttons right after `DOMCONTENTLOADED`, 3 inputs/1 button once network activity settles) — the very first Observation of a mission could catch an effectively empty page. Now waits for `NETWORKIDLE` specifically for the initial navigation (the one moment a full page's JS bundle loads from scratch); per-action `settle()` deliberately stays at the faster `DOMCONTENTLOADED` since later actions don't re-fetch the bundle.
- **An `<input>` with no `type` attribute silently got zero candidate actions generated for it, ever.** Real HTML/every browser treats a type-less input as `type="text"`, but `PlaywrightBrowser.getInputs()` read the raw (absent) attribute as an empty string, which matched no case in `DefaultElementActionMapper`'s switch — confirmed live: OrangeHRM's username field has no `type` attribute (its password field does), and AEGIS never once selected it as a candidate across 20 iterations regardless of strategy. Fixed at the observation layer (`PlaywrightBrowser`, not frozen) by normalizing a blank/absent type to `"text"` — benefits every downstream consumer (mapper, confidence scoring, input resolver) uniformly rather than patching each independently.

## Verification

Live-verified all 3 samples end-to-end, each run from its own module directory with **nothing but `aegis-api` and its transitive dependencies on the classpath** — no `aegis-core` internals imported, no `aegis-launcher` involved:

- `sample-saucedemo` — `SUCCESS` on the first try (no site-specific issues).
- `sample-orangehrm` — initially `FAILED` (0 candidate actions from the Vue-rendering timing gap, then a real reasoning stall from the type-less-input gap); `SUCCESS` after both fixes landed, using `strategy: adaptive`.
- `sample-nopcommerce` — `demo.nopcommerce.com` itself is blocked by bot protection from this environment's network (confirmed with real Playwright traffic, not just a bare HTTP client, still blocked) — swapped to `demowebshop.tricentis.com` (same underlying nopCommerce platform, already proven reachable all session). Initial run `FAILED` (wandered into the page's 60+ other links instead of finishing the registration form); `SUCCESS` with `strategy: form-first`.
- Also smoke-tested `browser.headless: true` and `browser.type: firefox` against `sample-saucedemo` (both browser binaries already present locally) — both reached real `SUCCESS`, confirming `BrowserConfig` genuinely reaches Playwright, not just that the YAML parses.
- Confirmed `aegis-launcher`'s existing 6 `*Main` classes (the dev/regression harness used all session) still work unchanged — `MissionRunner` now delegates to `Launcher`, verified with a fresh `SauceDemoMain` run.

**This completes Stage 1 of 6.**

---

## Stage 2 exit criteria

*"New functionality can be added without changing engine code."*

## Stage 2 design

The user's spec named 6 plugin categories (Browser, Observer, Finding, LLM, Authentication, Report) and 5 example extension-point interfaces (`FindingRule`, `MissionStrategy`, `ActionProvider`, `InputResolver`, `ReportRenderer`). Investigation found 3 of the 5 already exist as frozen interfaces — `ActionScorer`≈MissionStrategy, `InputValueResolver`≈InputResolver, `CandidateActionGenerator`≈ActionProvider — each already with an established Composite/Registry wiring pattern in `EngineFactory`. Stage 2's job for these was adding *discovery*, not inventing new interfaces. Only `FindingRule` and `ReportRenderer` were genuinely new.

For "Authentication Plugin," the user rejected the original framing (a plugin running arbitrary code against the browser) as reintroducing scripting — `page.click()`/`.fill()`/`.navigate()` "belong exclusively to AEGIS" — and specified a replacement: **Identity & Credential Integration**. A `SessionProvider` returns pre-authenticated session *data* only (cookies/storage/headers/a persistent profile path); AEGIS itself is the only thing that ever touches the browser to apply it. A separate `CredentialProvider` resolves secret references (Vault/AWS/Azure/env/...) into config values.

**Mechanism: Java's built-in `ServiceLoader`/SPI.** Zero new dependency, standard idiom for exactly this problem. A plugin jar on the classpath with a `META-INF/services/<interface>` file is auto-discovered; zero `aegis-core` edits needed.

**New package `com.aegis.core.plugin`:**

| Interface | Shape | Discovery/selection |
|---|---|---|
| `FindingRule` | `List<Finding> evaluate(MissionContext)` | Composite — every discovered rule runs every iteration, merged with the built-in detector's findings via new `CompositeAnomalyDetector` |
| `ReportRenderer` | `String name(); String render(MissionReportData)` | Composite — every discovered renderer runs once per mission, collected into `AegisReport.pluginReports()` |
| `NamedActionScorer extends ActionScorer` | adds `String strategyName()` | Registry — added to the `explorationStrategy` map alongside the 10 built-ins |
| `NamedInputValueResolver extends InputValueResolver` | adds `String strategyName()` | Registry — same pattern, `inputStrategy` map |
| *(reuses existing)* `CandidateActionGenerator` | unchanged | Composite — discovered generators appended to the built-in composite's list |
| `BrowserFactory` | `String type(); Browser create(BrowserConfig)` | Registry — `browser.type` resolves to a discovered factory if it isn't chromium/firefox/webkit |
| `SessionProvider` | `Optional<AuthenticatedSession> createSession(Mission)` | First non-empty result wins, tried right after `browser.launch()`, before the mission executes |
| `CredentialProvider` | `boolean supports(String); String resolve(String)` | Applied by `AegisConfigLoader` to `application.username`/`.password` |
| `AuthenticatedSession` (record) | `cookies, localStorage, sessionStorage, headers, browserProfilePath` — pure data | Applied via new `Browser.applySession(...)` — cookies/headers immediately (context-level, pre-navigation), storage deferred to the mission's first real `navigate()` call (origin-scoped, no valid origin exists before that) then a reload, a profile path via relaunching through Playwright's `launchPersistentContext` |

**Explicitly scoped down, not built as redundant machinery:**
- **LLM Plugin** — already achievable: implement the frozen `LlmChatClient` interface, construct it directly. Any `Llm*` class already accepts one via constructor injection.
- **Observer Plugin** — `DefaultObserver` (frozen) builds every `Observation` purely from `Browser.getButtons/getInputs/getLinks/getSelects()`. A plugin wanting different/additional element discovery provides those via a custom `Browser` (`BrowserFactory`), not a separate Observer interface.

**Wiring:** `EngineFactory` gained a `create(Mission, BrowserConfig)` overload (existing 0/1-arg overloads delegate with `mission = null`, session-provider lookup simply skipped then); `Aegis.run()` now builds `MissionReportData` once and shares it across the 3 built-in generators (each gained a `generate(MissionReportData)` overload) and every discovered `ReportRenderer`, instead of each independently rebuilding it.

## Stage 2 verification

- 8 new unit tests: `AuthenticatedSessionTest` (null-collection normalization, factory methods), `CompositeAnomalyDetectorTest` (merge behavior via fakes), and 2 new `AegisConfigLoaderTest` cases that exercise a *real* discovered `CredentialProvider` via an actual `META-INF/services` test resource — not a hand-wired fake standing in for ServiceLoader. Full suite: 260/260 passing.
- New `examples/plugin-example` module, depending on `aegis-core` directly (the extension points live there) — a real, working plugin jar: `SwagLabsFindingRule` (flags saucedemo.com's real page title — a guaranteed, unambiguous live signal), `MarkdownReportRenderer`, `EnvCredentialProvider` (resolves `env:VAR_NAME`), `DemoBrowserFactory` (proves custom `browser.type` resolution).
- **Live-verified against `sample-saucedemo`, completely unmodified** — the plugin jar's compiled output added to the runtime classpath only: the `FindingRule` fired (`Finding [LOW] PLUGIN_DEMO: page title matched 'Swag Labs'`, and was later even picked up and severity-escalated by the existing frozen bug-clustering logic with zero special-casing), the `ReportRenderer` wrote a real 4th report file, the `CredentialProvider` correctly resolved `env:AEGIS_DEMO_PASSWORD` (confirmed by the mission reaching genuine `SUCCESS` — a wrong password would have failed login) and failed loudly and clearly when the env var was unset, and `DemoBrowserFactory` was genuinely invoked for `browser.type: demo-browser`. Confirmed `aegis-launcher`'s existing `SauceDemoMain` (no plugin jar on its classpath) still produces exactly 3 report formats, not 4 — plugins are purely classpath-driven, never silently always-on.
- `Browser.applySession()` — the most novel new runtime behavior — smoke-tested directly against real Playwright in 3 scenarios: cookies+headers applied pre-navigation (no throw), localStorage/sessionStorage applied post-navigation-then-reload (confirmed via a real page reading the injected value back and setting its own title to it), and a persistent browser profile relaunch (confirmed via the profile directory actually being created on disk). Full "skip login entirely on a live site" wasn't proven live — would need a cooperating real backend issuing valid session tokens — noted honestly rather than faked.

**This completes Stage 2 of 6.**

---

## Stage 3 exit criteria

*"Can be integrated into CI/CD pipelines with minimal setup."*

## Stage 3 design

The spec listed 6 features: multiple mission profiles, environment profiles, secret management, parallel execution, mission scheduling, CLI improvements, with the example `aegis run --config production.yaml`. Confirmed with the user: mission profiles and environment profiles are two orthogonal, composable axes — environment = *where* to run (baseUrl/credentials/browser per dev/staging/production), mission = *what* to run (different named missions/strategies within one config file).

**Investigation found "parallel execution" needed zero `aegis-core` changes.** Exhaustive grep found zero mutable `static` fields anywhere in `aegis-core`/`aegis-api`/`aegis-model`; every collection `EngineFactory.create()` builds is a fresh local instance; `Playwright.create()` is designed for independent concurrent instances. Concurrent `Aegis.run(...)` calls were already safe — this made "parallel execution" a thin orchestration layer, not an architecture change.

**Two-axis config, additive and backward-compatible** — the Stage 1 single-mission `application:`/`browser:`/`mission:`/`report:` shape is untouched; a second, new top-level shape is recognized alongside it:
```yaml
environments:
  dev: { application: {...}, browser: {...} }
  production: { application: {...}, browser: {...} }
missions:
  smoke-test: { mission: {...} }
  full-regression: { mission: {...}, application: {...} }   # optional override, e.g. a different successUrlContains
report:
  directory: reports
```
New `aegis-api` types: `EnvironmentProfile`, `MissionProfile`, `EnterpriseConfig` (`resolve(environment, mission)` merges environment → mission-override → produces a plain `AegisConfig` — the key design property: everything downstream, `MissionBuilder`/`AegisApplication`/`Launcher`, needs zero changes), `EnterpriseConfigLoader` (same hand-mapped-tree approach as `AegisConfigLoader`, reusing its section/field helpers directly — widened from `private` to package-private for exactly this). `ApplicationConfig.merge(base, override)` — new static helper, field-by-field "non-null override wins."

**Parallel execution**: new `ParallelMissionRunner` — `runAll(Map<String, Mission>, int maxConcurrency)`, an `ExecutorService`/`CompletableFuture` fan-out over `Aegis.run(...)`. If one mission throws, results for the others still come back (not fail-fast/cancel) — a broken site shouldn't stop the rest of a batch from reporting.

**Secret management**: promoted a real `EnvCredentialProvider` into `aegis-api` itself (own `META-INF/services` entry) — every consumer now gets `env:VAR_NAME` resolution by default, no plugin jar needed. `examples/plugin-example`'s copy stays as the Stage 2 plugin-mechanism demo, unrelated to this.

**Mission scheduling — scoped down, stated plainly**: no internal daemon/scheduler was built. A CI/CD system (GitHub Actions cron, Jenkins cron, a k8s CronJob) already does this well; Stage 3's real answer is a clean, one-shot, exit-code-driven CLI a scheduler can invoke — same judgment call already made for LLM/Observer plugins in Stage 2.

**CLI**: new `aegis-cli` module, `maven-shade-plugin` (with a `ServicesResourceTransformer` — merges every dependency's `META-INF/services/*` instead of one silently overwriting another, required for Stage 2 plugin discovery to survive shading) producing a runnable fat jar, `Main-Class: com.aegis.cli.CliMain`. Scope deliberately just `run` — Stage 4 owns `init`/`report`/`validate`/`doctor`. `Launcher.run(...)`'s return type widened from `void` to `MissionStatus` (existing callers that ignore the return value keep compiling unchanged) so the CLI can translate the real outcome into an exit code: `SUCCESS`→0, `FAILED`→1, `PARTIAL`→2 — the actual mechanism that makes "CI/CD integration" real.

## Stage 3 verification

- 14 new unit tests: `ApplicationConfigMergeTest`, `EnterpriseConfigTest` (resolve/merge precedence, unknown environment/mission errors clearly), `EnterpriseConfigLoaderTest` (parsing, `isEnterpriseShaped` detection), `EnvCredentialProviderTest`. Full suite: 274/274 passing.
- **Live-verified the CLI against a real 2-environment/2-mission config, targeting saucedemo.com, through the actual `CliMain` logic** (manual-classpath technique — no `mvn` binary in this sandbox, so the shade-jar *packaging* itself couldn't be built/run here; stated honestly rather than faked, while every line of `CliMain`'s own logic ran for real): `--env dev --mission smoke-test` resolved correctly, the built-in `EnvCredentialProvider` resolved `env:AEGIS_ENTERPRISE_TEST_PASSWORD` with no plugin jar on the classpath (confirmed via the real password value reaching the login form), `headless`/custom `report.directory` were honored, mission reached real `SUCCESS`, **exit code 0**. Confirmed the missing-`--env`/`--mission` error path (exit code 1, clear message) and a genuinely `FAILED` mission (exit code 1) separately.
- **Live-verified `ParallelMissionRunner`** running SauceDemo + OrangeHRM concurrently: both reached real `SUCCESS` in 11.4s combined (well under what two sequential runs would take, given OrangeHRM alone typically takes 10s+) — real concurrency, not simulated. A transient error surfaced mid-run under the concurrent load and was caught and recovered by the existing (Phase 9) self-healing retry logic with no special handling needed.

**This completes Stage 3 of 6.** Stages 4–6 are real, tracked, and not started.

---

# Current Sprint

## Sprint Goal

~~Complete Phase 2 – Learning Framework~~ — Done (2026-07-24), see Phase 2 above.

### Tasks

- [x] Record Experience after every executed action
- [x] Persist Experience (in-run; see Phase 2's persistence note)
- [x] Validate LearningEngine
- [x] Validate PatternAnalyzer

Next sprint target not yet chosen — per the Working Agreement, that starts with Review/Design, not implementation.

---

# Definition of Done

A milestone is considered complete when:

- All code compiles
- Unit tests pass (where applicable)
- No architecture changes required
- Documentation updated
- ROADMAP.md updated

---

# Working Agreement

For every milestone we follow this process:

1. Review
2. Design
3. Architecture validation
4. Complete implementation
5. Compile validation
6. Code review
7. Documentation update
8. Mark milestone complete

No implementation begins until the design has been reviewed.
