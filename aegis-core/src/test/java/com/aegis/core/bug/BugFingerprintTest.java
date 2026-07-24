package com.aegis.core.bug;

import com.aegis.model.finding.Finding;
import com.aegis.model.finding.FindingSeverity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BugFingerprintTest {

    @Test
    void findingsThatOnlyDifferByEmbeddedNumbersFingerprintTheSame() {

        Finding a = finding("REQUEST_FAILED: /api/item/4/add-to-cart returned 500");
        Finding b = finding("REQUEST_FAILED: /api/item/7/add-to-cart returned 500");

        assertEquals(BugFingerprint.of(a), BugFingerprint.of(b));
    }

    @Test
    void findingsThatOnlyDifferByEmbeddedUrlsFingerprintTheSame() {

        Finding a = finding("REQUEST_FAILED: https://cdn.example.com/tracking/abc failed");
        Finding b = finding("REQUEST_FAILED: https://cdn.example.com/tracking/xyz failed");

        assertEquals(BugFingerprint.of(a), BugFingerprint.of(b));
    }

    @Test
    void findingsOfDifferentKindsFingerprintDifferently() {

        Finding crash = finding("CRASH: tab crashed");
        Finding pageError = finding("PAGE_ERROR: tab crashed");

        assertNotEquals(BugFingerprint.of(crash), BugFingerprint.of(pageError));
    }

    @Test
    void genuinelyDifferentDetailsFingerprintDifferently() {

        Finding a = finding("CONSOLE_ERROR: TypeError: cannot read property of undefined");
        Finding b = finding("CONSOLE_ERROR: ReferenceError: foo is not defined");

        assertNotEquals(BugFingerprint.of(a), BugFingerprint.of(b));
    }

    private Finding finding(String summary) {
        return new Finding(FindingSeverity.MEDIUM, summary, "https://example.com", Instant.now());
    }
}
