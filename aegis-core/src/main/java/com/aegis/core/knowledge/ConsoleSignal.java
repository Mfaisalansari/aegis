package com.aegis.core.knowledge;

import com.aegis.model.finding.FindingSeverity;

import java.time.Instant;

/**
 * One console message or uncaught exception captured live during the
 * mission by {@code SignalRecorder} — ephemeral browser output that no
 * post-hoc catalog could otherwise recover. {@code pageUrl} is the raw
 * page URL at fire time ({@code page.url()}) — the browser/listener layer
 * has no access to the Knowledge Enrichment Layer's node keys (those are
 * computed later, post-hoc, and can depend on organization config), so
 * correlating this to a node is left to whichever {@link KnowledgeProvider}
 * consumes it, same as every other provider resolves its own correlation.
 */
public record ConsoleSignal(FindingSeverity level, String text, String location, String pageUrl, Instant capturedAt) {
}
