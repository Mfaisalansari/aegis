package com.aegis.core.resilience;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocatorHealerTest {

    @Test
    void healsAnExactIdLocatorToAContainsMatchAndAFirstOfManyFallback() {

        List<String> candidates = LocatorHealer.candidatesFor("[id='submit']");

        assertEquals(List.of("[id*='submit']", ":nth-match([id*='submit'], 1)"), candidates);
    }

    @Test
    void healsAnExactNameLocatorTheSameWayAsId() {

        List<String> candidates = LocatorHealer.candidatesFor("[name='email']");

        assertEquals(List.of("[name*='email']", ":nth-match([name*='email'], 1)"), candidates);
    }

    @Test
    void healsADataTestIdLocatorTheSameWayAsIdAndName() {

        List<String> candidates = LocatorHealer.candidatesFor("[data-testid='login-button']");

        assertEquals(List.of(
                "[data-testid*='login-button']", ":nth-match([data-testid*='login-button'], 1)",
                "[data-testid*='button']", ":nth-match([data-testid*='button'], 1)",
                "[data-testid*='login']", ":nth-match([data-testid*='login'], 1)"
        ), candidates);
    }

    @Test
    void healsAnAriaLabelLocatorTheSameWayAsIdAndName() {

        // "Close dialog" tokenizes on the space too, same as a hyphen/underscore separator elsewhere.
        List<String> candidates = LocatorHealer.candidatesFor("[aria-label='Close dialog']");

        assertEquals(List.of(
                "[aria-label*='Close dialog']", ":nth-match([aria-label*='Close dialog'], 1)",
                "[aria-label*='dialog']", ":nth-match([aria-label*='dialog'], 1)",
                "[aria-label*='Close']", ":nth-match([aria-label*='Close'], 1)"
        ), candidates);
    }

    @Test
    void healsAPlaceholderLocatorTheSameWayAsIdAndName() {

        List<String> candidates = LocatorHealer.candidatesFor("[placeholder='Email address']");

        assertEquals(List.of(
                "[placeholder*='Email address']", ":nth-match([placeholder*='Email address'], 1)",
                "[placeholder*='address']", ":nth-match([placeholder*='address'], 1)",
                "[placeholder*='Email']", ":nth-match([placeholder*='Email'], 1)"
        ), candidates);
    }

    @Test
    void doesNotHealAnAttributeLocatorOutsideTheFiveBuildBestLocatorEverGenerates() {

        assertTrue(LocatorHealer.candidatesFor("[class='btn-primary']").isEmpty());
    }

    @Test
    void healsANthMatchLocatorToTheNeighboringIndices() {

        List<String> candidates = LocatorHealer.candidatesFor(":nth-match(button, 3)");

        assertEquals(List.of(":nth-match(button, 2)", ":nth-match(button, 4)"), candidates);
    }

    @Test
    void healsTheFirstNthMatchIndexToOnlyTheNextOne() {

        List<String> candidates = LocatorHealer.candidatesFor(":nth-match(button, 1)");

        assertEquals(List.of(":nth-match(button, 2)"), candidates);
    }

    @Test
    void unescapesAndReEscapesQuotesAndBackslashesInIdValues() {

        List<String> candidates = LocatorHealer.candidatesFor("[id='a\\'b']");

        assertEquals(List.of("[id*='a\\'b']", ":nth-match([id*='a\\'b'], 1)"), candidates);
    }

    @Test
    void healsAnIdWithASeparatorSplitValueByAlsoTryingEachStableTokenAfterTheWholeValue() {

        List<String> candidates = LocatorHealer.candidatesFor("[id='user-name-field']");

        assertEquals(List.of(
                "[id*='user-name-field']", ":nth-match([id*='user-name-field'], 1)",
                "[id*='field']", ":nth-match([id*='field'], 1)",
                "[id*='user']", ":nth-match([id*='user'], 1)",
                "[id*='name']", ":nth-match([id*='name'], 1)"
        ), candidates);
    }

    @Test
    void aPerTokenFallbackMatchesAnActualValueWithSomethingInsertedInTheMiddle() {

        // The whole-value contains match `[id*='user-name-field']` can't match this — "user-name-field" isn't a
        // contiguous substring of "user-form-name-field" — but the per-token fallback for "field" does.
        assertTrue(LocatorHealer.candidatesFor("[id='user-name-field']").contains("[id*='field']"));
    }

    @Test
    void dropsPureDigitTokensAndTokensBelowTheMinimumStableLength() {

        List<String> candidates = LocatorHealer.candidatesFor("[id='mui-48213-textfield']");

        assertEquals(List.of(
                "[id*='mui-48213-textfield']", ":nth-match([id*='mui-48213-textfield'], 1)",
                "[id*='textfield']", ":nth-match([id*='textfield'], 1)",
                "[id*='mui']", ":nth-match([id*='mui'], 1)"
        ), candidates);
    }

    @Test
    void returnsNoCandidatesForALocatorShapeItDoesNotRecognize() {

        assertTrue(LocatorHealer.candidatesFor("text=Sign in").isEmpty());
        assertTrue(LocatorHealer.candidatesFor("#some-css-id").isEmpty());
    }

    @Test
    void returnsNoCandidatesForABlankAttributeValue() {

        assertTrue(LocatorHealer.candidatesFor("[id='']").isEmpty());
    }

    @Test
    void healsAFrameQualifiedIdLocatorAndReattachesTheFramePrefixToEachCandidate() {

        List<String> candidates = LocatorHealer.candidatesFor("frame:1>[id='submit']");

        assertEquals(List.of("frame:1>[id*='submit']", "frame:1>:nth-match([id*='submit'], 1)"), candidates);
    }

    @Test
    void healsAFrameQualifiedNthMatchLocatorAndReattachesTheFramePrefix() {

        List<String> candidates = LocatorHealer.candidatesFor("frame:2>:nth-match(button, 3)");

        assertEquals(List.of("frame:2>:nth-match(button, 2)", "frame:2>:nth-match(button, 4)"), candidates);
    }

    @Test
    void returnsNoCandidatesForAFrameQualifiedLocatorOfAnUnrecognizedShape() {

        assertTrue(LocatorHealer.candidatesFor("frame:1>text=Sign in").isEmpty());
    }
}
