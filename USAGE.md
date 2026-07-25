# AEGIS — Usage Guide

How to build, configure, run, and extend AEGIS. For architecture and design rationale see `ARCHITECTURE.md`; for what's been built and what's still open see `AEGIS_ROADMAP.md` (authoritative process/status) and `README.MD` (detailed sprint log).

---

## 0. SDK Quick Start (recommended starting point)

The fastest way to run AEGIS against your own application, with no `aegis-core` internals knowledge needed. This is what `samples/` demonstrates end-to-end — see `samples/README.md` for a concrete under-15-minutes walkthrough with 3 working examples (SauceDemo, OrangeHRM, nopCommerce/DemoWebShop).

**1. Depend on `aegis-api` only:**

```xml
<dependency>
    <groupId>com.aegis</groupId>
    <artifactId>aegis-api</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**2. Write an `application.yml`** (every field optional — see the table below for what each falls back to):

```yaml
application:
  baseUrl: https://example.com
  username: demo_user
  password: demo_pass
  successUrlContains: dashboard

browser:
  type: chromium   # chromium | firefox | webkit
  headless: false

mission:
  strategy: adaptive   # see §5 for every option; omit for "greedy"
  maxIterations: 20

report:
  directory: reports   # where the 3 report files get written
```

**3. Implement `AegisApplication`** (the "application-specific pieces" — declarative facts about your app, not scripted steps; AEGIS still discovers and drives the actual UI itself through normal autonomous exploration):

```java
public class MyApplication implements AegisApplication {
    public String name() { return "My App"; }
    public AegisConfig config() { return AegisConfigLoader.load(Path.of("application.yml")); }
}
```

**4. Run it:**

```java
public static void main(String[] args) {
    Launcher.run(new MyApplication());
}
```

That's the whole surface: `Aegis`/`AegisReport` (also usable directly, see §3 below, if you'd rather build a `Mission` by hand or via `MissionBuilder` and skip the config file), `MissionBuilder`, `AegisConfig`/`ApplicationConfig`/`MissionConfig`/`AegisConfigLoader`, `AegisApplication`, `Launcher` — all in `com.aegis.api`, plus `Mission`/`MissionResult` (from `aegis-model`) and `BrowserConfig` (from `aegis-core`). This is the versioned public contract as of v1.0/Stage 1 — see `AEGIS_ROADMAP.md`'s Phase 10 and "Framework Adoption" sections for exactly what that promise covers.

**`application.yml` field reference:**

| Key | Falls back to when omitted |
|---|---|
| `application.baseUrl` | — (needed for a real mission, but the config loader itself doesn't require it) |
| `application.username` / `.password` | unset — generic placeholder values get filled instead (see §4) |
| `application.successUrlContains` | unset — mission runs out its `maxIterations` every time without ever declaring SUCCESS |
| `browser.type` | `chromium` |
| `browser.headless` | `false` |
| `mission.name` / `.description` | `"AEGIS Mission"` / `"Autonomous exploration"` |
| `mission.strategy` | `greedy` (see §5) |
| `mission.maxIterations` | `10` |
| `mission.inputStrategy` | `realistic` (see §4) |
| `mission.interruptions` / `.doubleClicks` / `.raceConditions` | `false` |
| `report.directory` | `reports` — resolved relative to the JVM's working directory, same as `mvn exec:java`'s default (the sample's own module directory) |

Everything below (§1 onward) documents the same capabilities from the `aegis-core`/`aegis-launcher` side — useful for understanding how it works internally, or if you're contributing to AEGIS itself rather than just embedding it.

---

## 1. Prerequisites

- **Java 23** — all modules (`aegis-model`, `aegis-core`, `aegis-api`, `aegis-launcher`, `samples/*`) target Java 23 (`maven.compiler.source`/`target` in the root `pom.xml`).
- **Maven** — a standard multi-module reactor build (root `pom.xml` lists modules in order: `aegis-model`, `aegis-core`, `aegis-api`, `aegis-launcher`, `samples`).
- **Playwright browsers** — `aegis-core` depends on `com.microsoft.playwright:playwright:1.54.0`. The first run downloads Chromium automatically; if it doesn't, install manually:
  ```
  mvn -pl aegis-core exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install"
  ```
  or run `npx playwright install` if you have Node available. AEGIS launches **headed** (a visible browser window) by default — see `PlaywrightBrowser.java` if you want to switch to headless for CI.

---

## 2. Building

From the repo root:

```
mvn clean install
```

This compiles all three modules in dependency order and runs the test suite (JUnit 5.13.4). There's no assembly/shade plugin configured, so this does **not** produce a single runnable fat jar — see §3 for how to actually run something.

To compile without running tests:

```
mvn clean install -DskipTests
```

---

## 3. Running a mission

This section covers `aegis-launcher`'s internal dev/regression `Main` classes specifically. If you just want to run AEGIS against a real app, see §0 and `samples/README.md` instead — each sample module already has `exec-maven-plugin` preconfigured (`mvn -pl samples/sample-saucedemo exec:java`, no flags needed).

`aegis-launcher` itself still has no CLI entry point or `exec-maven-plugin` configured. Each "mission" is a `Main` class in `aegis-launcher/src/main/java/com/aegis/launcher/`, so the straightforward way to run one is **from an IDE** (IntelliJ, VS Code with the Java extension, etc.) with the module's dependencies on the classpath — right-click the `Main` class → Run.

If you want a command-line path, add this to `aegis-launcher/pom.xml` and then run `mvn -pl aegis-launcher exec:java -Dexec.mainClass=com.aegis.launcher.SauceDemoMain`:

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.5.0</version>
</plugin>
```

### Existing launcher entry points

Every one of these calls the shared `MissionRunner.run(mission)`, which executes the mission, prints a summary to the console, and writes all three report formats (see §7). As of v1.0, `MissionRunner` is itself just a thin CLI wrapper around the public `Aegis.run(mission)` API in `aegis-core` — see §8a if you want to embed AEGIS in your own code instead of using one of these launcher classes.

| Class | Target | What it demonstrates |
|---|---|---|
| `Main.java` | `the-internet.herokuapp.com/login` | Baseline login mission |
| `SauceDemoMain.java` | `saucedemo.com` | Baseline login mission, default `greedy` strategy |
| `LlmSauceDemoMain.java` | `saucedemo.com` | Same mission with `explorationStrategy: llm` |
| `DemoWebShopMain.java` | `demowebshop.tricentis.com/register` | Registration flow, realistic input generation |
| `EdgeCaseInjectionMain.java` | Same registration form | `inputStrategy: edge-case` — deliberately malformed input; `FAILED` is the *expected*, healthy outcome (validation rejecting bad data), `SUCCESS` would mean garbage got accepted |
| `NaturalLanguageMissionMain.java` | `saucedemo.com` | Builds the `Mission` from a plain-English instruction string via `LlmMissionParser`, instead of a hand-written parameter map |

---

## 4. Mission parameters

A `Mission` (`com.aegis.model.mission.Mission`) is `id`, `name`, `description`, and a `Map<String, String> parameters`. These are the parameter keys AEGIS actually reads:

| Key | Values | Default | Read by |
|---|---|---|---|
| `baseUrl` | any URL | — | `DefaultMissionEngine` (navigation start), mission planners |
| `username` | any string | — | `DefaultInputValueResolver` (fills credential-shaped fields); presence also toggles a "login" step in the mission plan preview |
| `password` | any string | — | `DefaultInputValueResolver` |
| `successUrlContains` | any substring | unset = never resolves via this evaluator | `UrlContainsGoalEvaluator` — SUCCESS is declared the moment the current URL contains this substring |
| `maxIterations` | integer string | `10` | `DefaultMissionController` — unparseable/blank values silently fall back to the default |
| `inputStrategy` | `realistic` \| `edge-case` | `realistic` | `InputValueResolverRegistry` — anything else throws `IllegalArgumentException` |
| `explorationStrategy` | one of the 10 keys in §5 | `greedy` | `ActionScorerRegistry` — anything else throws `IllegalArgumentException` |
| `interruptions` | `enabled` (anything else = off) | off | `PageLevelCandidateActionGenerator` — adds REFRESH/BACK as candidate actions |
| `doubleClicks` | `enabled` | off | `DoubleClickCandidateActionGenerator` — adds double-click stress-test candidates |
| `raceConditions` | `enabled` | off | `RaceClickCandidateActionGenerator` — adds same-tick double-fire race-condition candidates |

If `username`/`password` aren't set, `DefaultInputValueResolver` falls back to generic placeholder values — fine for exploration, but a real login almost certainly needs the actual credentials.

---

## 5. Exploration strategies (`explorationStrategy`)

Registered in `EngineFactory.java`. All 10 keys:

| Key | Class | Notes |
|---|---|---|
| `greedy` | `HighestConfidenceActionScorer` | **Default.** Always picks the highest-confidence candidate. |
| `random` | `RandomActionScorer` | Picks uniformly at random among candidates. |
| `risk-based` | `RiskBasedActionScorer` | Weights toward/away from destructive-looking actions. |
| `breadth-first` | `BreadthFirstActionScorer` | Proxy strategy (element-tag based, not a real graph search yet). |
| `depth-first` | `DepthFirstActionScorer` | Same caveat as above. |
| `form-first` | `FormFirstActionScorer` | Prefers filling in forms over navigating away. |
| `navigation-first` | `NavigationFirstActionScorer` | Prefers following links over local page actions. |
| `coverage-aware` | `CoverageAwareActionScorer` | Graph-aware (uses `WorldModel`): rewards a candidate *confirmed* by history to lead somewhere not yet visited. |
| `adaptive` | `AdaptiveActionScorer` | Uses `greedy` normally, switches to `coverage-aware` once 3 iterations pass with no newly discovered state, switches back automatically once one turns up. The only strategy that changes mid-mission on its own. |
| `llm` | `LlmActionScorer` | A real language model picks among the already-validated candidates. See §6 for configuration. Falls back to `greedy` on any failure. |

Set via mission parameter, e.g.:
```java
Map.of(
    "baseUrl", "https://www.saucedemo.com/",
    "explorationStrategy", "coverage-aware"
)
```

---

## 6. LLM configuration

AEGIS talks to any server exposing an OpenAI-compatible `/chat/completions` endpoint (`OpenAiCompatibleChatClient`) — Ollama, LM Studio, vLLM, llama.cpp server, or OpenAI itself all work with **no code changes**, only environment variables:

| Env var | Default | Purpose |
|---|---|---|
| `AEGIS_LLM_BASE_URL` | `http://localhost:11434/v1` (Ollama) | The `/chat/completions` endpoint base |
| `AEGIS_LLM_MODEL` | `llama3.1` | Model name sent in the request body |
| `AEGIS_LLM_API_KEY` | `""` (empty) | Bearer token; most local servers ignore it. Set this for a real hosted provider. |
| `AEGIS_LLM_TIMEOUT_SECONDS` | `30` | HTTP request timeout |

**Deliberately environment variables, not mission parameters** — mission parameters get serialized into every generated report, and an API key must never end up there.

### Switching providers — examples

**Ollama (default, zero config)**: `ollama serve` + `ollama pull llama3.1`, then just run any mission with `explorationStrategy: llm`.

**LM Studio**:
```
export AEGIS_LLM_BASE_URL=http://localhost:1234/v1
export AEGIS_LLM_MODEL=<model name loaded in LM Studio>
```

**OpenAI**:
```
export AEGIS_LLM_BASE_URL=https://api.openai.com/v1
export AEGIS_LLM_MODEL=gpt-4o-mini
export AEGIS_LLM_API_KEY=sk-...
```

**vLLM / llama.cpp server**: same pattern — point `AEGIS_LLM_BASE_URL` at whatever host:port it's serving on.

### Where the LLM actually gets used

There are five independent AI features. Every one of them falls back to a deterministic default on any failure (network error, timeout, malformed response) — a bad model response degrades behavior, it never crashes a mission or executes something unvalidated.

| Feature | How to enable | Class | Falls back to |
|---|---|---|---|
| Action scoring | mission parameter `explorationStrategy: llm` | `LlmActionScorer` | `HighestConfidenceActionScorer` (greedy) |
| Bug explanation | env var `AEGIS_LLM_BUG_EXPLANATIONS=enabled` | `LlmBugExplainer` | `RuleBasedBugExplainer` |
| Recommendation | env var `AEGIS_LLM_RECOMMENDATIONS=enabled` | `LlmRecommendationEngine` | `RuleBasedRecommendationEngine` |
| Mission parsing | code-level — construct `new LlmMissionParser(...)` explicitly (see `NaturalLanguageMissionMain`) | `LlmMissionParser` | `RuleBasedMissionParser` |
| Mission planning | env var `AEGIS_LLM_MISSION_PLANNING=enabled` | `LlmMissionPlanner` | `RuleBasedMissionPlanner` |

Note the inconsistency between the first entry (a mission parameter) and the rest (environment variables) — action scoring affects *what the mission does*, so it's a per-mission choice; the other four only affect *reporting/parsing* and are opt-in globally per run, independent of each other. You can enable any combination.

**The mission plan is purely advisory** — even with `AEGIS_LLM_MISSION_PLANNING=enabled`, nothing in the live reasoning pipeline (`GoalReasoner`, `ActionScorer`, `CandidateFilter`) ever reads the generated plan. It's a preview shown in the console and the report, generated before the mission runs; it cannot influence what the mission actually does.

### Verifying your LLM setup

Run any mission with `explorationStrategy: llm` and check the console log:
- No `"LLM scorer failed"` warnings → it's working.
- If you see fallback warnings, check `AEGIS_LLM_BASE_URL` is reachable and `AEGIS_LLM_MODEL` matches a model actually loaded on that server.

---

## 7. Reports

Every mission run writes three report files to a `reports/` directory **relative to the JVM's working directory** (created automatically if missing):

```
reports/aegis-report-<epoch-millis>.txt
reports/aegis-report-<epoch-millis>.html
reports/aegis-report-<epoch-millis>.json
```

All three files from the same run share the identical timestamp. Console output prints all three paths at the end of the run. If you're calling `Aegis.run(mission)` directly instead of going through a launcher `*Main` class, you get all three as in-memory `String`s (`AegisReport.textReport()`/`.htmlReport()`/`.jsonReport()`) and decide yourself whether/where to write them — see §8a.

**Text report** (`ExplainabilityReportGenerator`) — plain text, good for grepping/diffing. Contains, in order: executive summary and statistics, mission plan, recommendation, Mission Timeline (chronological Observed/Reasoning/Execution/Findings reconstruction), pages visited, world model (states/transitions), exploration coverage (with a per-page checklist), learning summary (best/worst-performing actions), a findings dashboard grouped by category, bug clusters (with AI explanations if enabled), flat findings list, and the full reasoning trace (every candidate considered at every step, not just what was chosen).

**HTML report** (`HtmlExplainabilityReportGenerator`) — self-contained (inline CSS/JS, no external assets, works fully offline). A sticky table-of-contents nav links every section. Same data as the text report, rendered visually: a filterable Mission Timeline (toggle by event kind) with an inline screenshot under every Execution event that has one, an interactive SVG navigation graph with a heat map (node size/fill-opacity and edge thickness scale with how many times that state/transition was actually visited — hover a node to highlight its edges), stat tiles, per-page coverage bars, a learning performance table, a findings dashboard, and collapsible reasoning steps.

**Screenshots** — `SelfHealingBrowser` (Phase 9's resilience layer) captures a real PNG screenshot after every executed action automatically; no configuration needed. Each is matched to its nearest Execution timeline event by timestamp (within a 1-second tolerance) and embedded as a `data:image/png;base64,...` URI on `TimelineEvent.screenshotDataUri()` — a real inline `<img>` in the HTML report, the full data URI in the JSON export's `timeline[].screenshotDataUri` field, and just a `[screenshot captured — see HTML/JSON report]` note in the text report (the full base64 would make a text report meant for grepping/diffing unreadable). Only Execution events get one — observations/reasoning/findings/mission-start/finish have no specific browser action to capture against.

**JSON report** (`JsonReportGenerator`) — the same data as a stable, intentional export schema (top-level keys: `mission`, `coverage`, `worldModelEdges`, `recommendation`, `plan`, `timeline`, `learning`, `bugClusters`, `findingsByCategory`, `findings`), for feeding into another tool rather than reading directly.

---

## 8. Writing a new mission

Copy the shape of `SauceDemoMain.java`:

```java
package com.aegis.launcher;

import com.aegis.model.mission.Mission;

import java.util.Map;
import java.util.UUID;

public class MyMain {

    public static void main(String[] args) {

        Mission mission = new Mission(
                UUID.randomUUID(),
                "My Mission",
                "Short description of the goal",
                Map.of(
                        "baseUrl", "https://example.com/",
                        "username", "...",
                        "password", "...",
                        "successUrlContains", "..."
                )
        );

        MissionRunner.run(mission);
    }
}
```

Add whichever opt-in parameters from §4 you want (`inputStrategy`, `explorationStrategy`, `interruptions`, `doubleClicks`, `raceConditions`), and run it the same way as any other `*Main` class (§3).

### Natural language, instead of hand-writing the parameter map

```java
MissionParser parser = new LlmMissionParser(OpenAiCompatibleChatClient.fromEnvironment());
Mission mission = parser.parse("Log into https://example.com/ with the demo credentials and confirm you reach the dashboard.");
MissionRunner.run(mission);
```

The model extracts `baseUrl`, `goal`, `successUrlContains`, `username`, `password` as JSON; `baseUrl` is validated as a real `http(s)` URL before use, so a malformed or missing URL falls back to `RuleBasedMissionParser` (a bare URL regex against the instruction text) rather than producing a broken mission.

---

## 8a. Embedding AEGIS as a library

If you're depending on `aegis-core` from your own project (rather than copying one of `aegis-launcher`'s `*Main` classes), the public entry point is `com.aegis.core.Aegis.run(Mission)`:

```java
import com.aegis.core.Aegis;
import com.aegis.core.AegisReport;
import com.aegis.model.mission.Mission;

import java.util.Map;
import java.util.UUID;

Mission mission = new Mission(
        UUID.randomUUID(),
        "My Mission",
        "Short description of the goal",
        Map.of("baseUrl", "https://example.com/", "successUrlContains", "...")
);

AegisReport report = Aegis.run(mission);

report.status();       // MissionStatus
report.plan();          // MissionPlan — the pre-execution plan preview
report.textReport();    // String
report.htmlReport();    // String
report.jsonReport();    // String
```

Nothing is written to disk — that's a decision for your own code, not the library's. `aegis-core` alone is enough; you don't need `aegis-launcher` at all. `Mission`, `Aegis`/`AegisReport`, `MissionResult`, and the three report generators' `generate(...)` methods are the versioned public contract as of v1.0 — see `AEGIS_ROADMAP.md`'s Phase 10 section for exactly what that promise covers.

Self-healing (retry + locator healing + navigation recovery, Phase 9) requires no configuration — it's always on, wrapping the browser layer transparently for every mission run.

---

## 8b. Writing a Plugin

Stage 2 "Plugin Architecture": add capabilities without touching `aegis-core`. A plugin is a jar on the classpath implementing one or more interfaces from `com.aegis.core.plugin`, registered via a `META-INF/services/<interface>` file listing your implementation's fully-qualified class name (Java's standard `ServiceLoader` mechanism — no framework-specific registration needed). Depend on `aegis-core` directly (that's where these interfaces live), not `aegis-api`.

`examples/plugin-example` is a complete, real, working example — copy its shape. It implements all 4 of the interfaces below and is verified live (see `AEGIS_ROADMAP.md`'s Stage 2 section) by adding its build output to `sample-saucedemo`'s classpath with zero edits to either.

| Interface | What it's for | Selection |
|---|---|---|
| `FindingRule` — `List<Finding> evaluate(MissionContext)` | Custom bug/problem detection, alongside the built-in console-error/page-error/crash/dialog signals | Every discovered rule runs every mission iteration; findings merge with the built-in detector's |
| `ReportRenderer` — `String name(); String render(MissionReportData)` | A custom output format alongside text/HTML/JSON | Every discovered renderer runs once per mission; output appears in `AegisReport.pluginReports()` (keyed by `name()`) and gets written to disk by `Launcher` as `aegis-report-<ts>.<name>` |
| `NamedActionScorer extends ActionScorer` — adds `String strategyName()` | A custom exploration strategy | Set `mission.strategy` to your `strategyName()` in `application.yml`, same as any built-in strategy (§5) |
| `NamedInputValueResolver extends InputValueResolver` — adds `String strategyName()` | A custom input-fill strategy | Set `mission.inputStrategy` to your `strategyName()`, same as any built-in (§4) |
| `BrowserFactory` — `String type(); Browser create(BrowserConfig)` | An alternative `Browser` implementation (Selenium-backed, a remote grid, ...) | Set `browser.type` to your `type()` in `application.yml` |
| `CredentialProvider` — `boolean supports(String); String resolve(String)` | Resolving a secret *reference* (e.g. `vault:...`, `env:...`) into a real value, so plaintext secrets never sit in `application.yml` | Applied automatically to `application.username`/`.password` if some discovered provider's `supports()` returns true for the raw value |
| `SessionProvider` — `Optional<AuthenticatedSession> createSession(Mission)` | Establishing a pre-authenticated session before exploration starts (log in via an API, restore cookies, reuse a browser profile, complete SSO) | Tried right after the browser launches; first non-empty result wins |

**On `SessionProvider`/`AuthenticatedSession` specifically**: this is deliberately data-only — `AuthenticatedSession` carries cookies, localStorage/sessionStorage, headers, or a persistent browser profile path, never behavior. AEGIS itself is the only thing that ever calls `page.click()`/`.fill()`/`.navigate()`; a plugin that tried to perform UI actions would be reintroducing scripted automation, which this project deliberately doesn't do anywhere. Once a session is applied, the normal autonomous engine takes over exactly as if the site had been visited fresh and was already logged in.

Plugins are purely classpath-driven — nothing is "installed" or configured to enable them beyond being on the classpath, and nothing breaks if none are present (every discovered-plugin loop above is empty by default, same behavior as before Stage 2 existed).

---

## 9. Troubleshooting

- **Mission always ends `FAILED` with 0 findings**: check `successUrlContains` is actually set and matches a real URL substring the site reaches — without it, `UrlContainsGoalEvaluator` never resolves and the mission runs out its `maxIterations` (default 10) every time.
- **Login never succeeds**: confirm `username`/`password` are set as mission parameters — without them, `DefaultInputValueResolver` fills generic placeholder values, not real credentials.
- **`IllegalArgumentException: Unknown exploration strategy` / `Unknown input strategy`**: the value doesn't match one of the exact keys in §5 or §4 — these are case-sensitive exact string matches, not fuzzy.
- **LLM features silently doing nothing**: the three report-level env vars (`AEGIS_LLM_BUG_EXPLANATIONS`, `AEGIS_LLM_RECOMMENDATIONS`, `AEGIS_LLM_MISSION_PLANNING`) require the value to be exactly `enabled` (case-insensitive) — anything else, including unset, is off.
- **Playwright fails to launch**: confirm Chromium is installed (see §1); check for a stale lock/profile directory if a previous run crashed mid-launch.
- **A plugin doesn't seem to be discovered**: confirm its jar (or, when running from compiled classes, its output directory) is actually on the runtime classpath, and that `META-INF/services/<fully-qualified-interface-name>` exists and contains your implementation's fully-qualified class name on its own line — a typo there means `ServiceLoader` silently finds nothing, no error.
- **`IllegalArgumentException: Unknown browser type`**: `browser.type` doesn't match chromium/firefox/webkit and no discovered `BrowserFactory` plugin's `type()` matches it either — check the plugin is on the classpath (see above) and the name matches exactly (case-insensitive).

---

## 10. Where to look next

- `ARCHITECTURE.md` — the pipeline shape and design rationale (Observe → Reason → Explore → Learn loop).
- `AEGIS_ROADMAP.md` — authoritative phase-by-phase status, what's done, what's explicitly out of scope, and why.
- `README.MD` — the detailed sprint-by-sprint build log, including honest limitations for every feature.
