package com.aegis.cli;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

/**
 * {@code aegis doctor}
 *
 * No config needed — a real environment checklist: Java version, whether
 * each Playwright browser engine actually launches (a live probe, not a
 * guess from file existence), and which {@code AEGIS_LLM_*} variables
 * are set (informational only — every LLM feature is opt-in, see
 * USAGE.md §6).
 */
final class DoctorCommand {

    private static final String[] LLM_ENV_VARS = {
            "AEGIS_LLM_BASE_URL", "AEGIS_LLM_MODEL", "AEGIS_LLM_API_KEY", "AEGIS_LLM_TIMEOUT_SECONDS",
            "AEGIS_LLM_BUG_EXPLANATIONS", "AEGIS_LLM_RECOMMENDATIONS", "AEGIS_LLM_MISSION_PLANNING"
    };

    private DoctorCommand() {
    }

    static int run(String[] args) {

        System.out.println("AEGIS doctor");
        System.out.println();

        boolean javaOk = checkJavaVersion();
        boolean chromiumOk;

        try (Playwright playwright = Playwright.create()) {
            chromiumOk = checkBrowserEngine(playwright, "chromium");
            checkBrowserEngine(playwright, "firefox");
            checkBrowserEngine(playwright, "webkit");
        } catch (RuntimeException e) {
            System.out.println("✗ Playwright: failed to initialize (" + e.getMessage() + ")");
            chromiumOk = false;
        }

        System.out.println();
        System.out.println("LLM environment variables (all optional — every LLM feature is opt-in):");
        for (String var : LLM_ENV_VARS) {
            String value = System.getenv(var);
            System.out.println("  " + (value != null ? "✓" : "-") + " " + var + " : " + (value != null ? "set" : "not set"));
        }

        System.out.println();

        if (javaOk && chromiumOk) {
            System.out.println("Everything needed to run a mission is in place.");
            return 0;
        }

        System.out.println("One or more required checks failed — see USAGE.md §1 Prerequisites.");
        return 1;
    }

    private static boolean checkJavaVersion() {

        Runtime.Version version = Runtime.version();
        boolean ok = version.feature() >= 23;

        System.out.println((ok ? "✓" : "✗") + " Java version: " + version + (ok ? "" : " (need 23+)"));

        return ok;
    }

    private static boolean checkBrowserEngine(Playwright playwright, String engine) {

        try {

            BrowserType type = switch (engine) {
                case "firefox" -> playwright.firefox();
                case "webkit" -> playwright.webkit();
                default -> playwright.chromium();
            };

            try (var browser = type.launch(new BrowserType.LaunchOptions().setHeadless(true))) {
                System.out.println("✓ Playwright " + engine + ": available");
                return true;
            }

        } catch (RuntimeException e) {
            System.out.println("✗ Playwright " + engine + ": not available (" + e.getMessage() + ")");
            return false;
        }
    }
}
