package com.aegis.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DoctorCommandTest {

    // Real browser availability is environment-dependent (same honest
    // constraint every other Playwright-touching test in this project
    // already accepts) — this only asserts the checklist runs to
    // completion without throwing and reports the (deterministic) Java
    // version line, not that every engine launches.
    @Test
    void runsTheFullChecklistWithoutThrowingAndReportsJavaVersion() {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream original = System.out;

        int exitCode;

        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            exitCode = DoctorCommand.run(new String[0]);
        } finally {
            System.setOut(original);
        }

        String printed = out.toString(StandardCharsets.UTF_8);

        assertTrue(printed.contains("Java version: " + Runtime.version()));
        assertTrue(printed.contains("Playwright chromium"));
        assertTrue(printed.contains("Playwright firefox"));
        assertTrue(printed.contains("Playwright webkit"));
        assertTrue(printed.contains("AEGIS_LLM_BASE_URL"));
        assertTrue(exitCode == 0 || exitCode == 1);
    }
}
