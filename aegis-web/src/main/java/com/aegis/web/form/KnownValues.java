package com.aegis.web.form;

import java.util.List;
import java.util.Locale;

/** Valid values for the form's enum-shaped fields — mirrors USAGE.md §4/§5 exactly. */
final class KnownValues {

    private KnownValues() {
    }

    static final List<String> STRATEGIES = List.of(
            "greedy", "random", "risk-based", "breadth-first", "depth-first",
            "form-first", "navigation-first", "coverage-aware", "adaptive", "llm");

    static final List<String> INPUT_STRATEGIES = List.of("realistic", "edge-case");

    static final List<String> BROWSER_TYPES = List.of("chromium", "firefox", "webkit");

    static boolean isStrategy(String value) {
        return STRATEGIES.contains(value);
    }

    static boolean isInputStrategy(String value) {
        return INPUT_STRATEGIES.contains(value);
    }

    static boolean isBrowserType(String value) {
        return BROWSER_TYPES.contains(value.toLowerCase(Locale.ROOT));
    }
}
