package com.aegis.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportConfigTest {

    @Test
    void defaultsToReportsDirectory() {
        assertEquals("reports", ReportConfig.defaults().directory());
    }

    @Test
    void nullDirectoryNormalizesToTheDefault() {
        assertEquals("reports", new ReportConfig(null).directory());
    }

    @Test
    void blankDirectoryNormalizesToTheDefault() {
        assertEquals("reports", new ReportConfig("   ").directory());
    }

    @Test
    void anExplicitDirectoryIsPreservedAsGiven() {
        assertEquals("custom-reports", new ReportConfig("custom-reports").directory());
    }
}
