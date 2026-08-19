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
