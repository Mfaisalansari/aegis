package com.aegis.web.view;

/** Inline field explanations, transcribed from USAGE.md §4 (mission parameters), §5 (strategies), §6 (LLM), §8e/§8f (Knowledge/Inspection). */
public final class HelpText {

    private HelpText() {
    }

    public static final String BASE_URL = "Where the mission starts navigating. Required.";
    public static final String CREDENTIALS = "Filled into login-shaped fields. Without these, generic placeholder values are used instead — fine for exploration, but a real login almost certainly needs the actual credentials.";
    public static final String SUCCESS_URL_CONTAINS = "The mission succeeds the moment the current URL contains this substring. Leave unset and the mission always runs out its Max Iterations without resolving.";
    public static final String BROWSER_TYPE = "chromium, firefox, or webkit.";
    public static final String HEADLESS = "Run without a visible browser window.";
    public static final String MAX_ITERATIONS = "Step cap before the mission ends. Default 10.";
    public static final String INPUT_STRATEGY = "\"realistic\" fills valid-looking data. \"edge-case\" deliberately stresses input validation — a FAILED result is often the expected, healthy outcome.";
    public static final String INTERRUPTIONS = "Adds page refresh / back-navigation as candidate actions.";
    public static final String DOUBLE_CLICKS = "Adds double-click stress-test candidates.";
    public static final String RACE_CONDITIONS = "Adds same-tick double-fire race-condition candidates.";
    public static final String REPORT_DIRECTORY = "Where the .txt/.html/.json report files are written on the server. Default \"reports\".";

    public static final String KNOWLEDGE_YAML = "Optional: paste the nodes:/flows:/journeys: portion of a knowledge.yml to declare real page names, business flows, and reference journeys — always wins over AEGIS's auto-generated names. Note: the Inspection fields above always win over any inspection: block pasted here.";
    public static final String CAPTURE_DOM = "Required for every UI check (contrast, accessible name, size, overflow) — the only setting here with an added per-state cost.";
    public static final String CONSOLE_WARNINGS = "Also report console warnings, not just errors and uncaught exceptions.";
    public static final String CONTRAST_THRESHOLD = "WCAG AA normal-text default is 4.5. Use 3.0 for large text.";
    public static final String PROBE_LINKS = "Opt-in active HEAD/GET probe of same-origin links, run once after exploration finishes — never during the mission, and rate-limited.";
    public static final String NOISE_DENY_PATTERNS = "One regex per line, to suppress known third-party console noise (e.g. an analytics widget's own errors).";

    public static final String LLM_NOTE = "These reflect environment variables set when the AEGIS Web server was started. "
            + "To change them, stop the server, export new values, and restart — per-request overrides aren't supported. "
            + "\"llm\" is still selectable as an Exploration Strategy above; it only works if these are already configured.";

    /** One line per {@code explorationStrategy} key, from USAGE.md §5's table, in the exact order shown there. */
    public static final String[][] STRATEGIES = {
            {"greedy", "Behaves like a task-focused user who always takes the most obvious next step, without exploring. Default. Always picks the highest-confidence candidate."},
            {"random", "Behaves like an erratic user clicking without a plan — occasionally stumbles into a bug a careful user never would. Picks uniformly at random among candidates."},
            {"risk-based", "Behaves like a QA engineer deliberately going straight for the actions most likely to break something — delete, checkout, pay, submit — instead of the safe path. Weights toward/away from destructive-looking actions."},
            {"breadth-first", "Behaves like a thorough user who finishes everything on the current screen before moving to the next one. Proxy strategy (element-tag based, not a real graph search yet)."},
            {"depth-first", "Behaves like a user who dives into the next page as soon as something looks promising, rather than lingering on this one. Same caveat as breadth-first."},
            {"form-first", "Behaves like a user mid-task, focused on finishing the form in front of them instead of getting distracted by other links. Prefers filling in forms over navigating away."},
            {"navigation-first", "Behaves like a user getting oriented — clicking through menus/links to see what's there before committing to a task. Prefers following links over local page actions."},
            {"coverage-aware", "Behaves like a completionist tester deliberately trying to visit every screen at least once, not just the ones on the way to the goal. Graph-aware: rewards a candidate confirmed by history to lead somewhere not yet visited."},
            {"adaptive", "Behaves like a pragmatic user: takes the obvious path first, and only starts exploring more broadly after getting stuck for a while. Uses greedy normally, switches to coverage-aware after 3 stagnant iterations, switches back automatically. The only strategy that changes mid-mission on its own."},
            {"llm", "Behaves like an experienced human tester weighing the whole page in context, rather than following one fixed rule. A real language model picks among the already-validated candidates. Falls back to greedy on any failure."}
    };

    /** The 8 AEGIS_LLM_* env vars — never render AEGIS_LLM_API_KEY's actual value, only whether it's set (same discipline as `aegis doctor`). */
    public static final String[] LLM_ENV_VARS = {
            "AEGIS_LLM_BASE_URL", "AEGIS_LLM_MODEL", "AEGIS_LLM_API_KEY", "AEGIS_LLM_TIMEOUT_SECONDS",
            "AEGIS_LLM_BUG_EXPLANATIONS", "AEGIS_LLM_RECOMMENDATIONS", "AEGIS_LLM_MISSION_PLANNING", "AEGIS_LLM_REPORT_SUMMARY"
    };
}
