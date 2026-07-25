# API Reference

This is a lookup-oriented reference to AEGIS's public API surface — exact signatures, organized by module. For task-oriented "how do I..." guides with worked examples, see `USAGE.md` (referenced from each section below); this document deliberately doesn't repeat those examples.

The versioned public contract, as established in `AEGIS_ROADMAP.md`'s Phase 10 and extended by Stages 1–3: everything listed here is meant to be depended on by external code, and changes should be additive (new overloads/types), not breaking. Anything not listed here — `EngineFactory`'s internal wiring, any class outside the packages below — is not part of that promise, even if it happens to be `public`.

---

## `aegis-model`

The domain types every other module builds on. No dependencies on `aegis-core`/`aegis-api`.

| Type | Kind | Shape |
|---|---|---|
| `com.aegis.model.mission.Mission` | record | `Mission(UUID id, String name, String description, Map<String, String> parameters)` — `parameter(String key)` reads one value, `null` if absent. |
| `com.aegis.model.mission.MissionResult` | record | `MissionResult(MissionContext context, MissionStatus status)` |
| `com.aegis.model.mission.MissionStatus` | enum | `SUCCESS`, `FAILED`, `PARTIAL` |

See USAGE.md §4 for the full list of recognized `Mission.parameters` keys.

---

## `aegis-core`

The reusable exploration engine — depend on this directly if you don't need `aegis-api`'s config-file/CLI conveniences.

### Entry point

| Member | Signature | Notes |
|---|---|---|
| `com.aegis.core.Aegis` | `static AegisReport run(Mission mission)` | Uses `BrowserConfig.defaults()`. |
| | `static AegisReport run(Mission mission, BrowserConfig browserConfig)` | Nothing is written to disk — see USAGE.md §8a. |

### Result types

| Type | Kind | Shape |
|---|---|---|
| `com.aegis.core.AegisReport` | record | `AegisReport(MissionResult missionResult, MissionPlan plan, String textReport, String htmlReport, String jsonReport, Map<String, String> pluginReports)` — `status()` reads `missionResult.status()`. `pluginReports` is keyed by each discovered `ReportRenderer.name()`, empty if none are on the classpath. |
| `com.aegis.core.browser.BrowserConfig` | record | `BrowserConfig(String type, boolean headless)` — `type` is `"chromium"`/`"firefox"`/`"webkit"` (case-insensitive) or a discovered `BrowserFactory.type()`. `defaults()` = chromium, headed. |

### Report generators

Each has a canonical, stable overload; the others are internal convenience delegating to it.

| Type | Canonical method |
|---|---|
| `com.aegis.core.report.ExplainabilityReportGenerator` | `String generate(MissionReportData data)` — plain text |
| `com.aegis.core.report.HtmlExplainabilityReportGenerator` | `String generate(MissionReportData data)` — self-contained HTML |
| `com.aegis.core.report.JsonReportGenerator` | `String generate(MissionReportData data)` — see the shape reference in `ReportCommand`'s source, or just run a mission and inspect the output |

### Plugin extension points (`com.aegis.core.plugin`)

Full narrative + a "what's it for" table is in USAGE.md §8b — this is the exact signature reference.

| Interface | Signature | Selection |
|---|---|---|
| `FindingRule` | `List<Finding> evaluate(MissionContext context)` | Every discovered rule runs every iteration; merged into `CompositeAnomalyDetector`'s findings. |
| `ReportRenderer` | `String name();` / `String render(MissionReportData data)` | Every discovered renderer runs once per mission; output lands in `AegisReport.pluginReports()`. |
| `NamedActionScorer extends ActionScorer` | adds `String strategyName()` | Set `mission.strategy` to the returned name. |
| `NamedInputValueResolver extends InputValueResolver` | adds `String strategyName()` | Set `mission.inputStrategy` to the returned name. |
| `BrowserFactory` | `String type();` / `Browser create(BrowserConfig config)` | Set `browser.type` to the returned name. |
| `SessionProvider` | `Optional<AuthenticatedSession> createSession(Mission mission)` | Tried right after browser launch; first non-empty result wins. |
| `CredentialProvider` | `boolean supports(String rawValue);` / `String resolve(String rawValue)` | Applied automatically to `application.username`/`.password`. |
| `AuthenticatedSession` (record) | `AuthenticatedSession(List<SessionCookie> cookies, Map<String,String> localStorage, Map<String,String> sessionStorage, Map<String,String> headers, String browserProfilePath)` | Pure data — see the architectural note in USAGE.md §8b on why. Factory methods: `ofCookies(...)`, `ofBrowserProfile(...)`. |

All discovered via `META-INF/services/<fully-qualified-interface-name>` (`ServiceLoader`) — no registration call needed.

---

## `aegis-api`

Config-file loading, the `MissionBuilder` convenience, enterprise profiles, parallel execution, and built-in secret resolution. Depends on `aegis-model` + `aegis-core`.

### Building a `Mission`

| Type | Key members |
|---|---|
| `com.aegis.api.MissionBuilder` | `static MissionBuilder create(String name, String description)`; `static MissionBuilder from(AegisConfig config)`; fluent setters — `baseUrl`, `credentials(username, password)`, `successWhenUrlContains`, `strategy`, `inputStrategy`, `maxIterations`, `interruptions`, `doubleClicks`, `raceConditions`, `parameter(key, value)`; `Mission build()` |
| `com.aegis.api.AegisApplication` | interface — `String name()`; `AegisConfig config()`; `default Mission mission()` (built via `MissionBuilder.from(config())`) |

### Config records (single-mission shape, Stage 1)

| Type | Shape |
|---|---|
| `com.aegis.api.AegisConfig` | `AegisConfig(ApplicationConfig application, BrowserConfig browser, MissionConfig mission, ReportConfig report)` — `defaults()` available |
| `com.aegis.api.ApplicationConfig` | `ApplicationConfig(String baseUrl, String username, String password, String successUrlContains)` — `static merge(base, override)` (non-null override wins per field) |
| `com.aegis.api.MissionConfig` | `MissionConfig(String name, String description, String strategy, Integer maxIterations, String inputStrategy, boolean interruptions, boolean doubleClicks, boolean raceConditions)` |
| `com.aegis.api.ReportConfig` | `ReportConfig(String directory)` — blank/null normalizes to `"reports"` |
| `com.aegis.api.AegisConfigLoader` | `static AegisConfig load(Path path)` / `load(InputStream in)` — throws `AegisConfigException` on bad/missing YAML |

See USAGE.md §0/§4 for the YAML shape and field reference.

### Enterprise config (Stage 3, `environments:`/`missions:` shape)

| Type | Shape |
|---|---|
| `com.aegis.api.EnvironmentProfile` | `EnvironmentProfile(ApplicationConfig application, BrowserConfig browser)` — *where* to run |
| `com.aegis.api.MissionProfile` | `MissionProfile(MissionConfig mission, ApplicationConfig applicationOverrides)` — *what* to run; `applicationOverrides` may be `null` |
| `com.aegis.api.EnterpriseConfig` | `EnterpriseConfig(Map<String,EnvironmentProfile> environments, Map<String,MissionProfile> missions, ReportConfig report)` — `AegisConfig resolve(String environmentName, String missionName)` |
| `com.aegis.api.EnterpriseConfigLoader` | `static boolean isEnterpriseShaped(Path path)`; `static EnterpriseConfig load(Path path)` / `load(InputStream in)` |

See USAGE.md §8c for the YAML shape and the two-axis explanation.

### Running

| Type | Key members |
|---|---|
| `com.aegis.api.Launcher` | `static MissionStatus run(AegisApplication application)`; `run(Mission mission)`; `run(Mission mission, BrowserConfig browserConfig)`; `run(Mission mission, BrowserConfig browserConfig, String reportsDirectory)` — writes all 3 report formats + any plugin reports to disk, prints console output, returns the real `MissionStatus` |
| `com.aegis.api.ParallelMissionRunner` | `static Map<String,AegisReport> runAll(Map<String,Mission> missions, int maxConcurrency)`; overload taking an explicit `BrowserConfig` — concurrent `Aegis.run(...)` fan-out; a thrown mission doesn't stop the others from reporting |

### Secrets & errors

| Type | Notes |
|---|---|
| `com.aegis.api.EnvCredentialProvider` | Built-in `CredentialProvider` — resolves `env:VAR_NAME`. Shipped and auto-registered (`META-INF/services`), no plugin jar needed. |
| `com.aegis.api.AegisConfigException` | `RuntimeException` thrown by every loader above on bad input — messages are meant to be shown directly to a user (CLI error output, CI logs). |

---

## `aegis-cli`

Not a library — a runnable jar (`com.aegis.cli.CliMain`), documented in full in USAGE.md §8d. Listed here only because its exit-code contract is part of what makes AEGIS CI/CD-usable: every subcommand that resolves a mission outcome maps `SUCCESS`→0, `FAILED`→1, `PARTIAL`→2; subcommands that only validate/scaffold/inspect map success→0, failure→1.
