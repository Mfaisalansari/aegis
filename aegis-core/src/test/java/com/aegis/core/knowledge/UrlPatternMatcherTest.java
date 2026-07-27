package com.aegis.core.knowledge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlPatternMatcherTest {

    @Test
    void starMatchesAnyRunOfCharacters() {
        assertTrue(UrlPatternMatcher.matches("*/customers/search*", "https://app.example.com/customers/search?x=1"));
    }

    @Test
    void exactStringWithNoWildcardRequiresAnExactMatch() {
        assertTrue(UrlPatternMatcher.matches("https://app.example.com/login", "https://app.example.com/login"));
        assertFalse(UrlPatternMatcher.matches("https://app.example.com/login", "https://app.example.com/login/"));
    }

    @Test
    void nonMatchingPatternReturnsFalse() {
        assertFalse(UrlPatternMatcher.matches("*/orders/*", "https://app.example.com/customers/search"));
    }

    @Test
    void regexSpecialCharactersInThePatternAreTreatedLiterally() {
        assertTrue(UrlPatternMatcher.matches("*/search?x=1*", "https://app.example.com/customers/search?x=1&y=2"));
    }

    @Test
    void nullInputsNeverMatch() {
        assertFalse(UrlPatternMatcher.matches(null, "https://app.example.com/"));
        assertFalse(UrlPatternMatcher.matches("*", null));
    }
}
