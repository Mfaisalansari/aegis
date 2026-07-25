package com.aegis.core.plugin;

import com.aegis.core.reasoning.scorer.ActionScorer;

/**
 * Stage 2 "MissionStrategy" extension point: a custom exploration
 * strategy discovered via {@link java.util.ServiceLoader} and registered
 * into the same {@code explorationStrategy} mission-parameter map as the
 * 10 built-in strategies (greedy, adaptive, ...), keyed by
 * {@link #strategyName()}. Extends the frozen {@code ActionScorer}
 * interface without modifying it — this is purely additive, a new
 * sub-interface a plugin implements, not a change to ActionScorer itself
 * or to any built-in scorer.
 */
public interface NamedActionScorer extends ActionScorer {

    /** The key a mission sets {@code explorationStrategy} to, to select this scorer. Must not collide with a built-in name. */
    String strategyName();
}
