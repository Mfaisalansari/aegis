package com.aegis.api;

import com.aegis.core.AegisReport;

import java.util.Map;

/**
 * The outcome of a {@link ParallelMissionRunner#runAll} batch: every
 * mission that completed, and every mission that threw, each keyed the
 * same way as the input map. Partial success is a first-class outcome
 * here, not something an exception forces the caller to give up on — a
 * broken site in a batch of 10 shouldn't cost the caller the other 9
 * real reports.
 *
 * Code-review follow-up (post Framework Adoption Stage 5/6): the Stage 5
 * fix made {@code runAll} genuinely wait for every mission to finish
 * before failing, but it still failed the whole batch on any single
 * mission's exception, discarding every successful report along with
 * it — contradicting this class's own original intent ("a broken site
 * shouldn't stop the rest of a batch from reporting back"). This type is
 * what actually delivers on that.
 */
public record BatchResult(Map<String, AegisReport> reports, Map<String, Throwable> failures) {

    public BatchResult {
        reports = reports == null ? Map.of() : Map.copyOf(reports);
        failures = failures == null ? Map.of() : Map.copyOf(failures);
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }
}
