package com.aegis.core.bug;

import com.aegis.model.finding.Finding;

import java.util.regex.Pattern;

/**
 * A normalized identity for a Finding, coarser than exact string equality
 * — two findings with the same signal type and a structurally similar
 * message (same shape, different embedded numbers/ids/urls) fingerprint
 * the same, so "add to cart failed for item 4" and "...for item 7" are
 * recognized as the same underlying bug instead of two unrelated ones.
 *
 * Deliberately simple regex normalization, not semantic understanding —
 * it collapses digits, UUIDs, and URLs, nothing more. Two findings that
 * happen to differ only in wording but not in any of those will still
 * fingerprint differently; that's a real limitation, not a hidden one.
 */
public final class BugFingerprint {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    private BugFingerprint() {
    }

    public static String of(Finding finding) {

        String summary = finding.summary();

        int colon = summary.indexOf(':');

        String kind = colon >= 0 ? summary.substring(0, colon).strip() : summary.strip();
        String detail = colon >= 0 ? summary.substring(colon + 1) : "";

        String normalized = UUID_PATTERN.matcher(detail).replaceAll("<uuid>");
        normalized = URL_PATTERN.matcher(normalized).replaceAll("<url>");
        normalized = NUMBER_PATTERN.matcher(normalized).replaceAll("#");
        normalized = normalized.strip();

        return kind + "|" + normalized;
    }
}
