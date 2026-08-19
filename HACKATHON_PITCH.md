# AEGIS — Hackathon Pitch Script (Management, Non-Technical, 5-10 min)

A speaker script, not a technical walkthrough. Audience is entirely
non-technical management — every sentence here is written to be said out
loud, in plain language, with no jargon. For the engineer-facing version of
a demo (reasoning steps, strategy internals, self-healing, CLI), see
`DEMO_SCRIPT.md` instead — that is a different document for a different
audience, not something this one replaces.

**Everything claimed below is real and was live-verified this session** —
no invented numbers, no aspirational claims stated as fact.

---

## Before you walk in (do this backstage, 5 minutes before your slot)

1. Start the AEGIS web server. Confirm `http://localhost:8080/` loads and
   shows the dashboard.
2. Confirm `https://www.saucedemo.com/` (the site you'll demo against)
   loads normally in a browser tab.
3. **Run the exact demo mission once, backstage, before you go on.** This
   gives you a finished, real report already sitting on the dashboard as
   your **Plan B** — if the live run is slow or the venue's network hiccups
   during the real pitch, you open the backstage one instead and nobody in
   the room can tell the difference. This is the single most important risk
   mitigation in this whole script.
4. Have that Plan B report's "View HTML" page open in a second browser tab,
   scrolled to the Findings section, ready to alt-tab to.
5. Do **not** touch the natural-language panel, self-healing, or the CLI
   live — they're mentioned as a teaser in the "what's next" section only.
   They're real, but they carry timing risk this pitch doesn't need.

---

## 1. Hook — the problem (~40 seconds)

**Say:**

> "Every team here knows this problem two ways. Either QA is manual — a
> person clicking through the app before every release, which is slow and
> doesn't scale. Or it's automated with scripts — but those scripts are
> brittle. The moment someone renames a button or moves a field, the script
> breaks, and someone has to go fix the test instead of building the
> product. Either way, it costs real time, and things still slip through."

---

## 2. The idea (~50 seconds)

**Say:**

> "So we built something different: an agent that tests a website the way
> a real person would. You don't write it a script. You point it at a page,
> tell it what 'done' looks like, and it looks at what's actually on the
> screen, decides what to try next, and adapts if something's different
> than it expected — the same way you'd figure out a new website yourself.
> No script to write. No script to maintain."

---

## 3. Live proof (~2-3 minutes)

**Say, before you start:**

> "Let me just show you, live, against a real website."

**Do, on screen:**

1. Open `http://localhost:8080/` — the dashboard. Point at it for one
   second: "this is the whole tool — no terminal, no code."
2. Click **+ New Run**.
3. Fill in exactly these four fields (leave everything else at its default
   — don't touch "More settings," that's a deliberate choice for
   reliability, not a limitation):
   - **Base URL**: `https://www.saucedemo.com/`
   - **Username**: `standard_user`
   - **Password**: `secret_sauce`
   - **Success When URL Contains**: `inventory.html`
4. Click **Start run**.

**Say while it runs** (verified this session: consistently ~10-15 seconds,
page auto-refreshes on its own every 3 seconds, nothing to click):

> "It's looking at the login page right now, deciding the username field is
> the right first move, typing in a real value, moving to the password
> field, then the login button — the same three things you'd do — and it's
> about to tell us whether it got where it needed to go."

**When it finishes, click "View HTML"** (or alt-tab to your Plan B tab if
anything felt slow) and scroll to the findings:

> "And look — without anyone asking it to, it already flagged something:
> a real error happening on this page in the background that a person
> quickly clicking through probably wouldn't even notice."

*(This is the real, default finding every plain run against this site
produced throughout this session — a background resource failing to load,
shown as a plain "console error" — no special configuration needed to see
it; it's on by default.)*

---

## 4. What this actually gets you (~2 minutes)

**Say:**

> "Three things came out of that ten-second run. First: speed — that's a
> real test finishing in seconds, not a QA cycle taking days. Second: it
> found something real on its own — nobody told it to look for that error,
> it just noticed. And third — this is the part that matters long-term —
> because it isn't following a script, a redesign or a UI change doesn't
> break it the way it would break a traditional automated test. Someone
> doesn't have to keep going back and rewriting it every release."

**Then, without re-running anything live** — show the pre-captured slide/
screenshot of the deeper results already gathered this session:

> "And that's the simple version. When we turn on its deeper inspection
> mode, on this same ordinary demo site, it independently found 43 real
> issues — including a broken link, and text on the page that's genuinely
> too hard to read for some users to see clearly. All of that, on a site
> most people would assume was already fine."

---

## 5. What's next / the ask (~1-2 minutes)

**Say:**

> "What you saw is the simplest version of this. It can already do more
> that we're not demoing live today for time: you can describe a test in
> plain English instead of filling in a form, and it writes the test
> itself. If something it's about to click has been renamed or moved, it
> notices and adjusts instead of failing. And it can write up what it found
> in plain English too, the same way I just did for you, instead of a
> technical report.
>
> What we're asking for is a real pilot — point this at one of our actual
> internal applications, not a demo site, and see what it finds. Ten
> minutes of setup, and we'll know within the hour whether it's worth
> rolling out further."

---

## 6. Thank you / buffer

Leave the dashboard open on screen during Q&A — it's a better visual than a
closing slide, and it's the thing people will remember.

---

## Glossary — say this, not that

Keep this consistent no matter who delivers the pitch or what question
comes up in Q&A:

| If you're tempted to say... | Say instead |
|---|---|
| Exploration strategy / confidence score | "How it decides what to try next" |
| Reasoning step / candidate action | (don't show this live in this pitch at all) |
| Self-healing | "Keeps working even when the page changes" |
| Coverage percentage | "How much of the site it actually checked" |
| Finding / bug cluster | "A real problem it caught" |
| WCAG contrast failure | "Text some users couldn't read" |
| Knowledge Enrichment Layer | "Understands the site's actual screens and flows" |

## If something goes wrong live

- **Run is slow / stuck**: alt-tab to the Plan B tab from backstage setup
  step 3. Say: "let me show you one we ran just before coming up here" —
  completely true, not a cover story.
- **Someone asks "does it replace QA people?"**: "No — it replaces the
  repetitive, brittle part. It doesn't decide what 'good' means for your
  product, a person still does that."
- **Someone asks how it works technically**: "Happy to go deep after —
  there's a full engineering walkthrough for exactly that" (this is
  `DEMO_SCRIPT.md` — don't derail this slot into mechanics).
