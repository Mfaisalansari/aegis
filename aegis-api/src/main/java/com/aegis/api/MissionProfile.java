package com.aegis.api;

/**
 * Stage 3 "mission profile": *what* to run — one named entry under a
 * {@code missions:} section (smoke-test/full-regression/...), each with
 * its own {@code mission} settings (strategy, iteration budget, ...) and
 * an optional {@code application} override (e.g. a different
 * {@code successUrlContains} for this specific mission — {@code null}
 * means "use the environment's application config unchanged"). Paired
 * with an {@link EnvironmentProfile} (*where* to run) by
 * {@link EnterpriseConfig#resolve}.
 */
public record MissionProfile(MissionConfig mission, ApplicationConfig applicationOverrides) {
}
