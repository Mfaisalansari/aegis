package com.aegis.core.plugin;

import com.aegis.core.reasoning.value.InputValueResolver;

/**
 * Stage 2 "InputResolver" extension point: a custom input-fill strategy
 * discovered via {@link java.util.ServiceLoader} and registered into the
 * same {@code inputStrategy} mission-parameter map as the 2 built-ins
 * (realistic, edge-case), keyed by {@link #strategyName()}. Extends the
 * frozen {@code InputValueResolver} interface without modifying it —
 * same additive pattern as {@link NamedActionScorer}.
 */
public interface NamedInputValueResolver extends InputValueResolver {

    /** The key a mission sets {@code inputStrategy} to, to select this resolver. Must not collide with a built-in name. */
    String strategyName();
}
