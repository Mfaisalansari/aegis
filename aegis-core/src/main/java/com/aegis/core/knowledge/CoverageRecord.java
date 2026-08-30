package com.aegis.core.knowledge;

import java.time.Instant;
import java.util.Set;

/**
 * Cross-run coverage for one application: every node key any previously
 * persisted run (a live AEGIS mission, or an ingested Selenium/Playwright/
 * annotation-driven session) is known to have reached. {@code
 * applicationName} is the same identity {@code
 * KnowledgeBaseBuilder.build(applicationName, ...)} already uses
 * everywhere else in this codebase — see {@link CoverageStore} for why
 * that, not {@code Mission.id()}, is the stable key ARCHITECTURE.md's
 * Memory Scope section says persistence was waiting for.
 */
public record CoverageRecord(String applicationName, Set<String> visitedNodeKeys, Instant lastUpdated) {

    public CoverageRecord {
        visitedNodeKeys = visitedNodeKeys == null ? Set.of() : Set.copyOf(visitedNodeKeys);
    }

    public static CoverageRecord empty(String applicationName) {
        return new CoverageRecord(applicationName, Set.of(), Instant.EPOCH);
    }
}
