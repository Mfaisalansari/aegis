# FAQ

### How is AEGIS different from a Selenium/Playwright test script?

A script encodes a fixed sequence of steps — click this id, fill that field — and breaks when the page changes in a way the author didn't anticipate. AEGIS encodes a *goal* and reasons about whatever the page actually shows right now, so it adapts to incidental UI changes a script wouldn't survive. It's slower and less deterministic in exchange. See `ARCHITECTURE.md`'s "Why This Architecture?" section for the full tradeoff.

### Does AEGIS remember what it learned across separate runs?

No — `ExecutionMemory`/`WorldModel`/the learning pipeline are all scoped to a single mission run, not persisted between runs. Two reasons: missions get a random UUID per launch today, so there's no stable identity to key persisted state on; and persistence should be designed for a real consumer (e.g. an LLM reasoner using cross-run history) rather than built speculatively ahead of one. See `ARCHITECTURE.md`'s "Memory Scope" section.

### Can I use a different LLM provider?

Yes — any OpenAI-compatible `/chat/completions` endpoint (Ollama, LM Studio, vLLM, OpenAI itself, ...) via `AEGIS_LLM_BASE_URL`/`AEGIS_LLM_MODEL`/`AEGIS_LLM_API_KEY`. Every LLM feature (`explorationStrategy: llm`, bug explanations, recommendations, natural-language missions, mission planning) is independently opt-in and falls back to a deterministic rule-based default if the call fails. See USAGE.md §6.

### Can a plugin drive the browser directly — click buttons, fill forms, navigate?

No, deliberately. `page.click()`/`.fill()`/`.navigate()` belong exclusively to AEGIS itself. `SessionProvider` (the plugin that comes closest to touching browser state) only ever hands back *data* — cookies, localStorage/sessionStorage, headers, or a persistent profile path; AEGIS is what applies it. Letting a plugin drive the UI would reintroduce scripted automation, which this project's architecture exists specifically to avoid. See USAGE.md §8b and `ARCHITECTURE.md`'s Extension Points section.

### Why is there no PDF export?

A bespoke PDF renderer needs a real new dependency (iText's current versions are AGPL/commercial; OpenHTMLtoPDF is LGPL) for something a browser's own "Print to PDF" already does for free against the self-contained HTML report AEGIS already generates. That didn't clear the bar against this project's own "no unnecessary external dependencies" stance. See `AEGIS_ROADMAP.md`'s Phase 7 Stage 3 entry.

### Is AEGIS published to Maven Central?

Not yet — every module is still `1.0.0-SNAPSHOT`, resolved from your local `~/.m2` after running `mvn install` from the project root. Publishing is Stage 6 ("Community Release") territory and hasn't happened.

### What does `aegis init` actually generate, and can I open it in my IDE?

A standard Maven module — `pom.xml`, `application.yml`, and two small Java classes copying `samples/sample-saucedemo`'s shape — depending on nothing but `aegis-api`. It needs no IDE-specific plugin or archetype, so open the generated directory directly in IntelliJ, VS Code (with the Java extension), Eclipse, or anything else with Maven support. This *is* AEGIS's "IDE templates" deliverable — a separate IDE-specific project generator wasn't built on top of it, since it wouldn't add anything the plain Maven project doesn't already give you. See USAGE.md §8d.

### What's the difference between `aegis validate` and `aegis run`?

`validate` parses and resolves a config file (single-mission or enterprise-shaped) and prints a summary — no browser is launched, no mission runs. It's meant as a fast CI pre-flight check before the real `run`, and shares its exact config-resolution logic with `run` so "validate passed" genuinely means "run would have started cleanly."

### What does `aegis report` do that opening the HTML report doesn't?

It prints a compact, terminal-friendly summary (status, coverage, findings, top bug clusters, recommendation) of an existing JSON report, with an exit code mirroring the mission's own outcome — useful for a separate CI step gating on last night's scheduled run without opening a browser. It only reads an existing report; it can't regenerate the text/HTML formats from JSON (`Launcher` already writes all three on every run, so there was no real need to build that).

### Does AEGIS have a built-in scheduler — can I tell it to run nightly?

No, deliberately — a CI/CD system's own cron trigger (GitHub Actions `schedule:`, Jenkins cron, a k8s `CronJob`) already does that well. AEGIS's job is being a clean, one-shot, exit-code-driven command such a scheduler invokes (`aegis run` — exit `0`/`1`/`2` for `SUCCESS`/`FAILED`/`PARTIAL`). See USAGE.md §8c for a worked GitHub Actions example.

### Where do I go to write my own plugin, or look up an exact method signature?

USAGE.md §8b is the task-oriented "how do I write a plugin" guide with a worked example (`examples/plugin-example`); `API_REFERENCE.md` is the systematic signature-level lookup for the same interfaces plus every other public type.
