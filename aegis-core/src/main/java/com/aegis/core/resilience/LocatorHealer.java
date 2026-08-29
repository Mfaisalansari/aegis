package com.aegis.core.resilience;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Derives fallback locator candidates for a locator that AEGIS itself
 * generated (see {@code PlaywrightBrowser.buildBestLocator}) and that just
 * failed to resolve. Pure string logic, no browser/DOM access — it doesn't
 * know whether a candidate will actually match anything, only that it's a
 * plausible next thing to try.
 *
 * AEGIS only ever generates locators in one of two shapes, so healing is
 * scoped to exactly those two rather than attempting to parse arbitrary CSS:
 * <ul>
 *   <li>{@code [ATTR='VALUE']}, for whichever of the {@link
 *   #HEALABLE_ATTRIBUTES} {@code buildBestLocator} used — id, name,
 *   data-testid, aria-label, or placeholder; all five follow the exact same
 *   staleness pattern and heal the exact same way. A framework that appends
 *   or rotates a generated suffix onto an otherwise-stable value is a
 *   common real-world cause of this going stale, so the first fallback is
 *   a substring match on the whole stale value, followed by one substring
 *   match per stable token within it (see {@link #stableTokens}) — the
 *   whole-value match alone only covers a suffix/prefix being added around
 *   the stale value; it can't survive something being inserted in the
 *   middle of it, which the per-token fallbacks can.</li>
 *   <li>{@code :nth-match(TAG, N)} — a purely positional fallback (used
 *   only when none of the five attributes was unique). If the page gained
 *   or lost one matching element since this locator was generated, the
 *   same logical element is likely now at N-1 or N+1.</li>
 * </ul>
 */
final class LocatorHealer {

    /** Mirrors {@code PlaywrightBrowser.buildBestLocator}'s own preference order — the only attributes it ever embeds this way. */
    private static final Set<String> HEALABLE_ATTRIBUTES = Set.of("id", "name", "data-testid", "aria-label", "placeholder");

    private static final Pattern ATTRIBUTE = Pattern.compile("^\\[([a-zA-Z0-9_-]+)='(.*)'\\]$");
    private static final Pattern NTH_MATCH = Pattern.compile("^:nth-match\\(([a-zA-Z0-9]+),\\s*(\\d+)\\)$");

    /** Mirrors {@code PlaywrightBrowser.FRAME_PREFIX} — kept separate since this class has no Playwright dependency. */
    private static final Pattern FRAME_PREFIX = Pattern.compile("^(frame:\\d+>)(.*)$", Pattern.DOTALL);

    /** Below this length a token is too generic to usefully narrow anything down (e.g. "id", "el", a single digit). */
    private static final int MIN_STABLE_TOKEN_LENGTH = 3;

    private LocatorHealer() {
    }

    /**
     * Candidates to try, in preference order. Empty if the locator isn't a
     * shape this class recognizes. A {@code frame:N>} prefix (see {@code
     * PlaywrightBrowser}'s iframe support) is stripped before healing and
     * reattached to every candidate returned — the shapes below never need
     * to know frames exist at all.
     */
    static List<String> candidatesFor(String locator) {

        Matcher framePrefix = FRAME_PREFIX.matcher(locator);
        String prefix = framePrefix.matches() ? framePrefix.group(1) : "";
        String bare = framePrefix.matches() ? framePrefix.group(2) : locator;

        List<String> bareCandidates = candidatesForBare(bare);

        if (prefix.isEmpty()) {
            return bareCandidates;
        }

        return bareCandidates.stream().map(candidate -> prefix + candidate).toList();
    }

    private static List<String> candidatesForBare(String locator) {

        Matcher attribute = ATTRIBUTE.matcher(locator);
        if (attribute.matches() && HEALABLE_ATTRIBUTES.contains(attribute.group(1))) {
            return attributeCandidates(attribute.group(1), unescape(attribute.group(2)));
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

        List<String> candidates = new ArrayList<>();
        addContainsCandidates(candidates, attribute, value);

        for (String token : stableTokens(value)) {
            addContainsCandidates(candidates, attribute, token);
        }

        return List.copyOf(candidates);
    }

    private static void addContainsCandidates(List<String> candidates, String attribute, String value) {

        String contains = "[" + attribute + "*='" + escape(value) + "']";
        if (candidates.contains(contains)) {
            return;
        }

        candidates.add(contains);
        candidates.add(":nth-match(" + contains + ", 1)");
    }

    /**
     * The stale value's alphanumeric segments, split on whatever separator
     * a framework used to splice in the volatile part (an id/counter,
     * usually flanked by {@code -}, {@code _}, {@code :}, or similar) —
     * e.g. {@code "user-name-field"} against an actual DOM value of
     * {@code "user-form-name-field"} isn't a whole-value substring match
     * (a real string was inserted in the middle, not just appended around
     * it), but {@code "field"} still is. Pure-digit tokens are dropped —
     * a rotating numeric id/counter is the single most common volatile
     * fragment, so keeping it as a candidate would just reintroduce the
     * same fragility. Longest token first: the more of the original value
     * survives intact, the less likely that candidate is to also match
     * some unrelated element on the page.
     */
    private static List<String> stableTokens(String value) {
        return Arrays.stream(value.split("[^a-zA-Z0-9]+"))
                .filter(token -> token.length() >= MIN_STABLE_TOKEN_LENGTH)
                .filter(token -> !token.chars().allMatch(Character::isDigit))
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
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
