package com.aegis.core.plugin;

import com.aegis.model.context.MissionContext;
import com.aegis.model.finding.Finding;

import java.util.List;

/**
 * Stage 2 "Finding Plugin": custom bug/problem detection, discovered via
 * {@link java.util.ServiceLoader} and merged with the built-in
 * {@code BrowserSignalAnomalyDetector} by {@code CompositeAnomalyDetector}
 * — see that class for exactly how. Same shape as the frozen
 * {@code AnomalyDetector} interface deliberately (so a rule "feels" the
 * same to write), but declared independently rather than extending it,
 * so nothing about this plugin point touches the frozen interface itself.
 *
 * Called once per mission iteration, same cadence as the built-in
 * detector. A rule that finds nothing returns an empty list, not null.
 */
public interface FindingRule {

    List<Finding> evaluate(MissionContext context);
}
