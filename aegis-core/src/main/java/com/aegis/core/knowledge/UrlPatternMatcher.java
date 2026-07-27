package com.aegis.core.knowledge;

import java.util.regex.Pattern;

/**
 * Hand-rolled {@code *}-wildcard glob matching for {@code
 * knowledge.yml}'s {@code urlPattern} — no new dependency, same "no
 * unnecessary complexity" discipline as {@code successUrlContains}'s
 * plain substring match elsewhere in this codebase. {@code *} matches
 * any run of characters (including none); everything else is literal.
 */
final class UrlPatternMatcher {

    private UrlPatternMatcher() {
    }

    static boolean matches(String pattern, String url) {

        if (pattern == null || url == null) {
            return false;
        }

        StringBuilder regex = new StringBuilder();

        for (char c : pattern.toCharArray()) {
            if (c == '*') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }

        return Pattern.matches(regex.toString(), url);
    }
}
