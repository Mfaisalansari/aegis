package com.aegis.api;

import com.aegis.core.browser.BrowserConfig;

/**
 * The root shape of an {@code application.yml}:
 * <pre>
 * application:
 *   baseUrl: https://example.com
 * browser:
 *   type: chromium
 *   headless: false
 * mission:
 *   strategy: adaptive
 * report:
 *   directory: reports
 * </pre>
 * Any section, or any field within a section, may be omitted — see
 * {@link AegisConfigLoader} for exactly what each omission falls back to.
 * {@code browser} reuses {@code aegis-core}'s own {@link BrowserConfig}
 * rather than a duplicate type — same "re-export, don't wrap" pattern
 * already used for {@code Mission}/{@code MissionResult}.
 */
public record AegisConfig(ApplicationConfig application, BrowserConfig browser, MissionConfig mission, ReportConfig report) {

    public static AegisConfig defaults() {
        return new AegisConfig(
                ApplicationConfig.defaults(), BrowserConfig.defaults(), MissionConfig.defaults(), ReportConfig.defaults());
    }
}
