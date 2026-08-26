package com.aegis.observation;

import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.MissionStatus;

/**
 * What {@link ObservationSession#finish} produces — a populated {@link
 * MissionContext} plus the caller-supplied outcome. Deliberately not a
 * report or a {@code KnowledgeBase} itself: this module depends only on
 * {@code aegis-model}, so turning this into either is the caller's job via
 * {@code aegis-core}'s own {@code KnowledgeBaseBuilder.build(context.getExecutionState().getObservations(), ...)}
 * / {@code MissionReportData.from(context, status, ...)} — the exact same
 * calls an AEGIS-driven mission's own report already goes through.
 */
public record ObservationResult(MissionContext context, MissionStatus status) {
}
