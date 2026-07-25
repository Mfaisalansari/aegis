package com.aegis.api;

import com.aegis.core.browser.BrowserConfig;

/**
 * Stage 3 "environment profile": *where* to run — one named entry under
 * an {@code environments:} section (dev/staging/production/...), each
 * with its own {@code application}/{@code browser} settings. Paired with
 * a {@link MissionProfile} (*what* to run) by {@link EnterpriseConfig#resolve}.
 */
public record EnvironmentProfile(ApplicationConfig application, BrowserConfig browser) {
}
