# Changelog

All notable changes to this project are documented here, in [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) style. This is a condensed, terse summary — see `AEGIS_ROADMAP.md` and `README.MD` for the full narrative detail behind every entry.

The project follows [Semantic Versioning](https://semver.org/); see `CONTRIBUTING.md`'s Versioning Policy. Currently `1.0.0-SNAPSHOT` — nothing below has been tagged/published yet.

## [Unreleased]

### Added — Knowledge Enrichment Layer v1
- New `com.aegis.core.knowledge` package: a permanent, fully additive architectural layer between `WorldModel`'s raw facts and future consumers (reports, test planning, impact analysis). `KnowledgeBase` is a type-safe catalog registry (not a fixed-field record) fed by `KnowledgeProvider`s — `ServiceLoader`-discoverable, same mechanism as the existing plugin architecture — so future catalogs (risk/business metadata, requirements, defects, historical learning) slot in without redesign.
- Built-in catalogs: `StateCatalog` (pure discovery facts), `NodeCatalog` (hybrid auto-naming — real page title, else humanized URL, never unnamed — with organization overrides always winning via a new `knowledge.yml`), `FlowCatalog` (declared business groupings), `JourneyCatalog` (declared reference paths plus the real observed path each run took, compared as a subsequence match).
- Governing rule: AEGIS never invents business meaning — every node's name is tagged with its `NameSource` (`CONFIGURED`/`AUTO_TITLE`/`AUTO_URL`), and flows/journeys only ever come from `knowledge.yml`, never inferred from graph structure.
- New `KnowledgeBaseTextRenderer` producing a "World Model Summary" text artifact.
- 29 new unit tests. Live-verified against a real saucedemo.com run.

### Fixed — code review follow-ups (post Stage 6)
- CI's Playwright-install step now uses `-am` so it can actually resolve `aegis-core`'s dependency on `aegis-model` on a fresh runner — the workflow would have failed on every single run.
- `EngineFactory.create()`'s SessionProvider leak fix no longer masks the real failure if `browser.close()` itself throws during cleanup; the close failure is now attached via `addSuppressed` instead of replacing the original exception.
- `aegis init --base-url <value>` now escapes the value before writing it into the generated `application.yml` — a value containing a colon, quote, or newline no longer produces broken YAML.

### Changed — code review follow-ups (post Stage 6)
- **Breaking (pre-release):** `ParallelMissionRunner.runAll(...)` now returns a new `BatchResult` record (`reports`, `failures`, `hasFailures()`) instead of `Map<String, AegisReport>`. The Stage 5 fix made it wait for every mission to finish before failing, but still discarded every successful report on any single failure — contradicting its own documented intent that "a broken site shouldn't stop the rest of a batch from reporting back." `BatchResult` is what actually delivers on that: a caller now gets every successful report *and* every failure, never nothing.

### Added — Framework Adoption Stage 6: Community Release
- `LICENSE` (Apache 2.0), `CONTRIBUTING.md`, this changelog, GitHub Actions CI (`mvn -B clean verify` on push/PR), issue and pull request templates, a front-door summary at the top of `README.MD`.

### Added — Framework Adoption Stage 5: Performance & Quality
- Fixed two real resource leaks: `EngineFactory.create()` could leak an already-launched browser if a `SessionProvider` plugin threw; `DefaultMissionEngine.execute()` could leak the browser if the initial navigation hit an unreachable host.
- Fixed `ParallelMissionRunner` to actually wait for every mission to finish before propagating a failure, matching its own documented contract.
- Defensive-copy hardening on `Mission`, `AegisReport`, `MissionPlan`, `EnterpriseConfig`; `ExecutionState`'s list getters now return unmodifiable views.
- Centralized Playwright's version into the root `pom.xml`'s dependency management.
- `aegis-model` test coverage (previously zero), regression tests for every fix above, a new JaCoCo config, and a `BenchmarkMain` dev harness.

### Added — Framework Adoption Stage 4: Ecosystem
- `aegis-cli` rounded out to 5 subcommands: `run`, `init` (scaffolds a starter project — also the "IDE templates" deliverable), `validate`, `report`, `doctor`.
- `ARCHITECTURE.md` rewritten with real content; new `API_REFERENCE.md` and `FAQ.md`.

### Added — Framework Adoption Stage 3: Enterprise Readiness
- Two-axis `environments:`/`missions:` config profiles, `ParallelMissionRunner`, a built-in `env:` secret provider, and the first `aegis-cli` module (`run`, with CI/CD-ready exit codes).

### Added — Framework Adoption Stage 2: Plugin Architecture
- `ServiceLoader`-based extension points (`com.aegis.core.plugin`): `FindingRule`, `ReportRenderer`, `NamedActionScorer`, `NamedInputValueResolver`, `BrowserFactory`, and Identity Integration (`SessionProvider`/`AuthenticatedSession`/`CredentialProvider` — deliberately data-only, plugins never drive the browser directly).

### Added — Framework Adoption Stage 1: Framework Adoption
- Public SDK (`aegis-api`): `MissionBuilder`, YAML config (`AegisConfig`/`AegisConfigLoader`), `AegisApplication`, `Launcher`. Three working sample projects under `samples/`.

### Added — Post-v1.0: Real Screenshot Capture
- `SelfHealingBrowser` captures a real screenshot after every action; timeline events in the report link to the nearest matching capture.

### Added — v1.0.0 (Phases 0–10)
- **Phase 10** — stable public API: `com.aegis.core.Aegis.run(Mission)` → `AegisReport`.
- **Phase 9** — self-healing execution: retry-once, locator healing, navigation recovery, type-aware dialog handling.
- **Phase 8** — five independent opt-in LLM-backed decision points (candidate scoring, bug explanation, recommendation, natural-language mission parsing, mission planning), each with a deterministic rule-based fallback.
- **Phase 7** — production reporting: text/HTML/JSON formats, Mission Timeline, executive summary, findings dashboard, learning summary.
- **Phase 6** — bug intelligence: fingerprint-based clustering, severity escalation on recurrence, cross-page root-cause flagging.
- **Phase 5** — coverage intelligence: per-page coverage, navigation heat maps.
- **Phase 4** — exploration intelligence: 8 pluggable strategies, coverage-aware scoring, automatic adaptive strategy switching.
- **Phase 3** — adaptive decision making: learned confidence adjustment, exploration bonus for untried candidates.
- **Phase 2** — learning framework: `Experience`/`ExperienceRepository`/`LearningEngine`, in-run pattern analysis.
- **Phase 1** — core autonomous engine: the Observe → Reason → Decide → Execute loop.
- **Phase 0** — project foundation: domain model, configuration, logging, build system.

[Unreleased]: https://github.com/Mfaisalansari/aegis/compare/main...HEAD
