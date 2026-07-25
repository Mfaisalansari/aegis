# Contributing to AEGIS

Thanks for considering a contribution. This document covers the real, expected workflow — building with Maven, running the tests, and how changes get reviewed.

## Prerequisites

- Java 23+
- Maven
- Playwright's browser binaries, for anything that actually launches a browser (unit tests don't need this — see "Running tests" below): `mvn -pl aegis-core exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install"` (or see [USAGE.md §1](USAGE.md#1-prerequisites))

## Building

```
mvn clean install
```

Builds every module in the reactor (`aegis-model`, `aegis-core`, `aegis-api`, `aegis-cli`, `aegis-launcher`, `samples/*`, `examples/*`) in dependency order.

## Running tests

```
mvn test
```

The unit test suite doesn't need real browser binaries installed — every test that needs a `Browser` uses a hand-written fake, real Playwright is only exercised via manual live verification during development (see any phase/stage entry in `AEGIS_ROADMAP.md` for how that's done). `aegis-cli`'s `DoctorCommandTest` is the one exception that touches real `Playwright.create()`, but it only asserts the checklist completes, not that every engine is installed.

To run a real mission against a real site (e.g. to manually verify a change):

```
mvn -pl samples/sample-saucedemo exec:java
```

## Code style and process

- Read `AEGIS_ROADMAP.md`'s **Architecture Status** section first — the components listed there (`MissionEngine`, `Planner`, `DecisionEngine`, `ActionScorer`, etc.) are frozen. Changes to their public contracts need an architecture review, not just a PR; additive composition around them (a new `ActionScorer` implementation, a new plugin) doesn't.
- `ARCHITECTURE.md` explains why the pipeline is shaped the way it is; `API_REFERENCE.md` is the exact signature reference for the public API surface (`aegis-model`/`aegis-core`/`aegis-api`).
- This project's own Working Agreement, followed for every milestone so far: **Review → Design → Architecture validation → Complete implementation → Compile validation → Code review → Documentation update → Mark complete.** No implementation begins until the design is reviewed — for anything non-trivial, open an issue or discussion describing the approach before sending a large PR.
- Keep changes scoped: a bug fix doesn't need surrounding refactoring, a new feature doesn't need speculative extensibility for a future that isn't here yet.
- Update the relevant docs (`USAGE.md`, `ARCHITECTURE.md`, `API_REFERENCE.md`, `AEGIS_ROADMAP.md`) in the same PR as the code change, not as a follow-up.

## Commit messages

Describe *why*, not just *what* — the diff already shows what changed. Reference the relevant `AEGIS_ROADMAP.md` phase/stage if applicable.

## Pull requests

- One logical change per PR where reasonable.
- Include what you tested and how (unit tests, live verification against a real site, etc.) — see `AEGIS_ROADMAP.md`'s own phase entries for the level of detail expected.
- CI (`.github/workflows/ci.yml`) runs `mvn -B clean verify` on every PR — make sure it's green before requesting review.

## Versioning policy

AEGIS follows [Semantic Versioning](https://semver.org/). The project is currently `1.0.0-SNAPSHOT` — every module shares one version number across the reactor. Cutting and publishing a real tagged release is a deliberate, separate action taken by a maintainer when the project is ready, not something that happens automatically as a side effect of merging changes (see `AEGIS_ROADMAP.md`'s Phase 10 notes on this same point). Breaking changes to the versioned public API (`Mission`, `Aegis`/`AegisReport`, the `aegis-api` config/builder types, the `com.aegis.core.plugin` extension-point interfaces — see `API_REFERENCE.md`) require a major version bump once a non-SNAPSHOT release exists; until then, the API is still stabilizing.

## Reporting bugs / requesting features

Use the issue templates — they ask for what's actually needed to act on a report (repro steps, expected vs. actual, environment) or a request (the problem it solves, not just the solution).
