# AEGIS Live Demo Script

A presenter's script for showing AEGIS to external stakeholders — low to
high, each beat proves one capability using only what's already proven by
the beat before it. Not a replacement for `ARCHITECTURE.md`/`USAGE.md`/
`AEGIS_ROADMAP.md` (those remain the reference docs); this is a narrow,
practical run sheet for the room.

Every beat here was live-run against the real target it names — a real
browser, a real external site, and (Beats 4 and 6 specifically) a real
local LLM — during the verification pass that produced this script. Where something
didn't reproduce cleanly, that's called out explicitly rather than glossed
over, because an honest surprise in front of stakeholders is recoverable and
a fabricated claim that fails live is not.

## One-time setup (do this before the room fills up)

1. **Local LLM.** Beats 4, 6, and 7 need a running local Ollama with
   `llama3.1` pulled: `ollama serve` (with `OLLAMA_MODELS` pointed wherever
   your models live) and confirm `curl -s http://localhost:11434/api/tags`
   returns it. Send one throwaway prompt first — the first real request
   after a cold start pays a one-time ~30s model-load cost that would
   otherwise eat into Beat 4's live moment.
2. **Bump the LLM timeout.** `export AEGIS_LLM_TIMEOUT_SECONDS=60` in the
   shell you'll run every AI beat from. AEGIS's own default is 30s, tuned
   for a fast hosted API — a local 8B model on ordinary hardware generating
   a multi-sentence answer measured consistently at ~32s during
   verification, i.e. *just* past the default. Without this bump, Beats 4/6/7
   will silently (with a log line) fall back to their rule-based/non-AI
   path instead of showing the real thing.
3. **Never run two AI beats at once.** Ollama serializes requests on one
   local machine; overlapping two LLM-backed runs was enough during
   verification to push both past even the 60s timeout. Finish one AI beat
   before starting the next.
4. **`aegis-launcher` exec plugin.** Already wired (`aegis-launcher/pom.xml`)
   — every command below is `mvn -pl aegis-launcher exec:java
   -Dexec.mainClass=com.aegis.launcher.<Class>`, no manual classpath typing
   needed live.
5. Have `reports/` cleared or a fresh terminal tab per beat so each HTML
   report is easy to find and open immediately after its run.

---

## Beat 1 — It's not a script, it's a reasoning agent

**Say:** "There's no recorded sequence of clicks anywhere in this codebase.
Watch it look at a real login page it's never seen configured for this run,
and decide for itself what to do."

**Run:**
```
mvn -pl aegis-launcher exec:java -Dexec.mainClass=com.aegis.launcher.SauceDemoMain
```

**Show:** Open the generated HTML report, click the **Reasoning & Learning** tab.
Point at Step 1: it considered three candidates (username field, password
field, login button), scored each with a real confidence number and a
plain-English reason ("Credential-like field (USERNAME)"), and picked the
username field first because the button scored low ("visible inputs are not
filled yet"). Point at the **strategy badge** ("greedy") and the **typed
value** ("standard_user") next to each step — this is real test data it
chose to type, not a placeholder.

**Payoff:** "It didn't just click three things in a row — it looked, scored
its options, and explained why, every single step, against the actual live
page."

*(~10s to run.)*

---

## Beat 2 — The strategy is swappable, live, with zero code changes

**Say:** "How it decides is itself a setting, not a hard-coded rule. Same
real HR site, same login, two different decision policies — watch both
reach the goal."

**Run** (the `sample-orangehrm` module — a real OrangeHRM demo instance,
`application.yml` already set to `strategy: adaptive`):
```
mvn -pl samples/sample-orangehrm exec:java
```
Open `samples/sample-orangehrm/application.yml` on screen and point at the
single line — `mission.strategy: adaptive` — as the whole knob; change it to
`greedy` and rerun to show the same site solved either way. Ten built-in
strategies are selectable the same way, no code touched.

**Honest note for the presenter, not the audience:** an older internal note
claimed `greedy` specifically got stuck fixating on OrangeHRM's password
field and needed `adaptive` to recover. Re-verified live this pass: it no
longer reproduces — both strategies now solve OrangeHRM cleanly in 3 actions,
almost certainly because scoring heuristics matured in later work after that
note was written. That's a good thing (the simple policy got smarter), but
don't claim the stuck-vs-recovers contrast live since it isn't true today.
Keep this beat's claim to what's actually demonstrated: **the policy is a
one-line, swappable setting**, not "some sites need a smarter one." If you
want a visible behavioral difference instead, Beat 4's registration-form
run is a better source (greedy visibly wanders into other links on a page
with many of them; a form-biased strategy doesn't).

**Payoff:** "Whatever policy fits how *your* app is shaped — thorough,
fast, form-focused — is a config change, not an engineering request."

**If asked "what do these actually mean?"** — each strategy models a
different kind of real tester or user, not just an abstract algorithm:

| Strategy | Behaves like... |
|---|---|
| `greedy` | A task-focused user who always takes the most obvious next step, without exploring. |
| `random` | An erratic user clicking without a plan — occasionally stumbles into a bug a careful user never would. |
| `risk-based` | A QA engineer deliberately going straight for delete/checkout/pay/submit — the actions most likely to break something. |
| `breadth-first` | A thorough user who finishes everything on the current screen before moving on. |
| `depth-first` | A user who dives into the next page as soon as something looks promising. |
| `form-first` | A user mid-task, focused on finishing the form in front of them. |
| `navigation-first` | A user getting oriented — clicking through menus/links before committing to a task. |
| `coverage-aware` | A completionist tester deliberately trying to visit every screen at least once. |
| `adaptive` | A pragmatic user: obvious path first, explores more broadly only once stuck. |
| `llm` | An experienced human tester weighing the whole page in context. |

(Same table now lives in `USAGE.md` §5 and next to the strategy picker in
the web UI — this is the one explanation to keep consistent everywhere.)

---

## Beat 3 — It survives a broken locator

**Say:** "Real apps rename things. Watch what happens when the exact element
it's about to click isn't there anymore."

**Run:**
```
mvn -pl aegis-launcher exec:java -Dexec.mainClass=com.aegis.launcher.SelfHealingDemoMain
```

**Show:** The console log: it tries the original locator, times out after
Playwright's real ~30s wait, then a line reading `Healed locator:
'[id='login-button']' -> '[id*='login-button']'`, then `RESULT: SELF-HEALING
WORKED`.

**Presenter note:** this beat has a real ~30-40 second pause built in — the
first attempt pays Playwright's full default wait before healing kicks in.
Narrate through it ("this is the part where a brittle test script would
already be red") rather than standing in silence.

**Payoff:** "A traditional script breaks the moment an id changes. This
didn't — it noticed, tried a sensible alternative, and kept going."

---

## Beat 4 — It finds and explains real defects, not just clicks

**Say:** "It's not only trying to succeed — it's watching for what's broken
along the way, on a real production-grade demo site, injecting deliberately
bad input the way a hostile or careless user would."

**Run:**
```
mvn -pl aegis-launcher exec:java -Dexec.mainClass=com.aegis.launcher.EdgeCaseInjectionMain
```

**Presenter note:** this mission's own **`FAILED` status is the expected,
healthy outcome** — it means the site's validation correctly rejected the
garbage data (empty fields, oversized strings, XSS/SQLi-shaped payloads,
malformed emails). Say this out loud before the status prints, or `FAILED`
reads as a bug in AEGIS itself. Verified live this pass: the deliberately
malformed data genuinely gets typed into the real Email/Password/
ConfirmPassword fields — open the report, click the **Reasoning & Learning**
tab, and point at one to prove it isn't a placeholder. One real caveat worth knowing going in:
this mission has also been observed wandering off into the page's other 60+
links instead of reaching a submit attempt at all (the same greedy-distraction
behavior Beat 2 mentions) — if that happens live, don't hide it: "and this
is a live example of exactly why the strategy setting from Beat 2 matters."

**Payoff:** "It doesn't just find the happy path — it probes the edges the
way a real attacker or a careless user would, and tells you in plain terms
what it found."

*(Optional, if time and a second Ollama-free moment allow: `AEGIS_LLM_BUG_EXPLANATIONS=enabled`
before this run makes each finding's explanation LLM-written instead of
templated — remember the "one AI beat at a time" rule from setup.)*

---

## Beat 5 — It understands your business flows, not just raw clicks

**Say:** "Beyond individual actions, it builds a map of every distinct
screen it found, and — separately — a real defect inspection of each one:
broken links, accessibility problems, console errors, all against the live
site, not a mock."

**Run:**
```
mvn -pl aegis-launcher exec:java -Dexec.mainClass=com.aegis.launcher.WorldModelDemoMain
```

**Show:** The printed World Model — states discovered, named from the
page's own real title (say plainly that these are auto-named today because
no `knowledge.yml` was supplied; a real deployment would declare "Login
Screen" / "Checkout" itself). Optionally follow with the same mission run
via the `Aegis.run(mission, browserConfig, knowledgeConfig)` API with
`InspectionConfig` fully enabled (`captureDom`, `consoleWarnings`,
`probeLinks` all true) — verified live this pass against saucedemo.com
itself to surface **real** findings: two real console 401 errors, two real
failed background network calls to a third-party telemetry endpoint, one
real broken in-app link, and real WCAG contrast failures (measured
1.00:1 against a 4.5:1 threshold) on two real buttons — 43 total page
defects on a site most people would assume was clean.

**Payoff:** "This isn't a synthetic test fixture — it's real problems on a
real, live, actively-maintained demo site, the kind of thing that's easy to
miss scrolling through the page yourself."

---

## Beat 6 — Talk to it in plain English

**Say:** "You don't need to know AEGIS's config format to point it at
something. Describe the mission the way you'd describe it to a colleague."

**Run:** Start the web UI. `aegis-web` already packages a runnable shade jar
(`com.aegis.web.WebMain`, same pattern as `aegis-cli`) — build it once with
`mvn -pl aegis-web package` and run `java -jar
aegis-web/target/aegis-web-1.0.0-SNAPSHOT.jar` (or, without Maven available,
the manual classpath: `java -cp
aegis-model-classes:aegis-core-classes:aegis-api-classes:aegis-web-classes:<deps>
com.aegis.web.WebMain`, the same way this was actually run during
verification). Open `http://localhost:8080/run`, expand **"Describe in
plain English instead,"** and type: *"Log into https://www.saucedemo.com/
using username "standard_user" and password "secret_sauce", and confirm you
reach the inventory page."*

**Show:** Click **Parse into form** — the progress overlay locks the whole
page (dimmed, blurred, every field disabled) while it thinks, matching the
30-60s LLM budget from setup. It then re-renders the structured form
pre-filled — base URL, credentials, success condition — for you to review
and edit before anything runs. Click **Start run**.

**Payoff:** "Nothing runs until you've seen exactly what it understood and
approved it — this is a review step, not a black box."

---

## Beat 7 — Runs end to end from a browser, no terminal at all

**Say:** "And the whole thing — submit, watch, understand what happened —
never needs a terminal."

**Show:** The dashboard (`http://localhost:8080/`) — stat tiles (total
runs, pass rate, average duration, active count) updating live as the Beat 6
mission finishes; click into it for the same reasoning-step timeline from
Beat 1, now in the browser.

**Payoff:** "This is the same engine from Beat 1, now something a
non-engineer on the team could point at their own staging environment."

---

## Optional closer — Beat 8: this is production-usable, not a toy

**Say:** "And it's not just a demo harness — it's a real CLI meant for a CI
pipeline."

**Run** (build once with `mvn -pl aegis-cli package`; no `finalName` is
configured, so the jar is `aegis-cli-1.0.0-SNAPSHOT.jar`, not a bare
`aegis-cli.jar`):
```
java -jar aegis-cli/target/aegis-cli-1.0.0-SNAPSHOT.jar doctor
java -jar aegis-cli/target/aegis-cli-1.0.0-SNAPSHOT.jar validate --config samples/sample-saucedemo/application.yml
java -jar aegis-cli/target/aegis-cli-1.0.0-SNAPSHOT.jar run --config samples/sample-saucedemo/application.yml
```

**Honest note for the presenter:** the CLI's *logic* (config resolution,
credential handling, exit codes 0/1/2 per mission status) was re-verified
live this pass via a manual classpath run, not the packaged jar — this
sandbox has no `mvn` binary, so the actual `mvn package` shade-jar build has
never been executed here. If presenting from a machine with real Maven,
run `mvn -pl aegis-cli package` once beforehand and confirm the `java -jar`
form above actually works before relying on it live; otherwise substitute
the manual-classpath form and don't claim the packaged jar specifically.

**Payoff:** "Exit code 0 or 1 is all a CI pipeline needs to gate a deploy on
this the same way it gates on any other test suite."
