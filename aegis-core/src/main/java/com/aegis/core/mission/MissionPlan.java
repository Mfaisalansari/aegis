package com.aegis.core.mission;

import java.util.List;

/**
 * Phase 8 "AI mission planning": an ordered list of short, high-level
 * steps a planner expects a mission to require, generated before the
 * mission runs. Deliberately advisory only — nothing in the live
 * reasoning pipeline (GoalReasoner, ActionScorer, CandidateFilter) reads
 * this. It exists to preview intent in the report, not to control
 * execution, which is exactly what keeps it safe: a bad or nonsensical
 * plan step is a quality problem, never something that gets executed
 * against a real page.
 */
public record MissionPlan(List<String> steps) {
}
