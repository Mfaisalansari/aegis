package com.aegis.core.resilience;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives fallback locator candidates for a locator that AEGIS itself
 * generated (see {@code PlaywrightBrowser.buildBestLocator}) and that just
 * failed to resolve. Pure string logic, no browser/DOM access — it doesn't
 * know whether a candidate will actually match anything, only that it's a
 * plausible next thing to try.
 *
 * AEGIS only ever generates locators in one of three shapes, so healing is
 * scoped to exactly those three rather than attempting to parse arbitrary
 * CSS:
 * <ul>
 *   <li>{@code [id='VALUE']} — exact id match. A framework that appends or
 *   rotates a generated suffix onto an otherwise-stable id is a common
 *   real-world cause of this going stale, so the fallback is a substring
 *   match on the same value.</li>
 *   <li>{@code [name='VALUE']} — same reasoning, for name.</li>
 *   <li>{@code :nth-match(TAG, N)} — a purely positional fallback (used
 *   only when neither id nor name was unique). If the page gained or lost
 *   one matching element since this locator was generated, the same
 *   logical element is likely now at N-1 or N+1.</li>
 * </ul>
 */
final class LocatorHealer {

    private static final Pattern ID = Pattern.compile("^\\[id='(.*)'\\]$");
    private static final Pattern NAME = Pattern.compile("^\\[name='(.*)'\\]$");
    private static final Pattern NTH_MATCH = Pattern.compile("^:nth-match\\(([a-zA-Z0-9]+),\\s*(\\d+)\\)$");

    private LocatorHealer() {
    }

    /** Candidates to try, in preference order. Empty if the locator isn't a shape this class recognizes. */
    static List<String> candidatesFor(String locator) {

        Matcher id = ID.matcher(locator);
        if (id.matches()) {
            return attributeCandidates("id", unescape(id.group(1)));
        }

        Matcher name = NAME.matcher(locator);
        if (name.matches()) {
            return attributeCandidates("name", unescape(name.group(1)));
        }

        Matcher nthMatch = NTH_MATCH.matcher(locator);
        if (nthMatch.matches()) {
            return positionalCandidates(nthMatch.group(1), Integer.parseInt(nthMatch.group(2)));
        }

        return List.of();
    }

    private static List<String> attributeCandidates(String attribute, String value) {

        if (value.isBlank()) {
            return List.of();
        }

        String escaped = escape(value);
        String contains = "[" + attribute + "*='" + escaped + "']";

        return List.of(contains, ":nth-match(" + contains + ", 1)");
    }

    private static List<String> positionalCandidates(String tag, int index) {

        if (index <= 1) {
            return List.of(":nth-match(" + tag + ", " + (index + 1) + ")");
        }

        return List.of(
                ":nth-match(" + tag + ", " + (index - 1) + ")",
                ":nth-match(" + tag + ", " + (index + 1) + ")"
        );
    }

    /** Mirrors {@code PlaywrightBrowser.escapeAttributeValue} — kept separate since this class has no Playwright dependency. */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static String unescape(String value) {
        return value.replace("\\'", "'").replace("\\\\", "\\");
    }
}
