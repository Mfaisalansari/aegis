# AEGIS Roadmap

> Version: 1.0 (Architecture Freeze)
>
> Status: In Development
>
> Last Updated: 2026-07-24

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

# 🟡 Phase 7 – Reporting

## Status

Substantially Complete — corrected from "Not Started" (audit, 2026-07-24)

## Completed

- Mission Report (`ExplainabilityReportGenerator`, text)
- Reasoning Report (per-step candidate/selection detail, confidence margin over runner-up)
- HTML Report (`HtmlExplainabilityReportGenerator`) — self-contained, interactive SVG navigation graph, executive summary (goal + plain-English outcome), severity-ranked findings with plain-English explanations, collapsible reasoning steps

## Remaining

- [ ] Coverage Report (dedicated artifact — depends on Phase 5)
- [ ] PDF Report

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

# 🔴 Phase 9 – Self-Healing

## Status

Not Started

## Note

Adjacent groundwork exists and should inform this phase's design: `DefaultMissionEngine` already recovers from a thrown exception mid-mission (records a Finding, advances the iteration, keeps going instead of crashing) — a primitive form of retry/resilience, not locator healing. Dialog handling already avoids hanging on a native dialog (auto-dismiss + capture as a Finding) — a primitive form of dialog recovery, not an intelligent accept/dismiss decision.

## Goals

- Locator healing
- Retry engine
- Dialog recovery
- Navigation recovery

---

# 🔴 Phase 10 – AEGIS v1.0

## Status

Not Started

## Deliverables

- Autonomous exploratory testing
- Adaptive learning
- Coverage intelligence
- AI reasoning
- Production reporting
- Stable public API

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
