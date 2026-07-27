package com.aegis.api;

import com.aegis.core.Aegis;
import com.aegis.core.AegisReport;
import com.aegis.core.browser.BrowserConfig;
import com.aegis.model.mission.Mission;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Stage 3 "parallel execution": runs several independent missions
 * concurrently. Pure orchestration — {@code Aegis.run(...)} is already
 * safe to call from multiple threads at once (every collection/browser
 * instance {@code EngineFactory.create} builds is fresh per call, no
 * shared mutable state anywhere in {@code aegis-core}), so this needed
 * zero changes there.
 *
 * A mission throwing doesn't fail the whole batch — every future is
 * waited on regardless of whether an earlier one failed, and the
 * returned {@link BatchResult} carries every successful report AND
 * every failure, both keyed the same way as the input map. A broken
 * site shouldn't cost the caller the other 9 real reports out of a
 * batch of 10.
 */
public final class ParallelMissionRunner {

    private ParallelMissionRunner() {
    }

    public static BatchResult runAll(Map<String, Mission> missions, int maxConcurrency) {
        return runAll(missions, BrowserConfig.defaults(), maxConcurrency);
    }

    /** Same as {@link #runAll(Map, int)}, with one {@link BrowserConfig} shared by every mission in the batch. */
    public static BatchResult runAll(
            Map<String, Mission> missions, BrowserConfig browserConfig, int maxConcurrency) {

        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, maxConcurrency));

        try {

            Map<String, CompletableFuture<AegisReport>> futures = new LinkedHashMap<>();

            for (Map.Entry<String, Mission> entry : missions.entrySet()) {
                Mission mission = entry.getValue();
                futures.put(entry.getKey(), CompletableFuture.supplyAsync(() -> Aegis.run(mission, browserConfig), executor));
            }

            // Wait for every future to complete — success or failure —
            // before reading any of them, so a slower mission always gets
            // to finish even if an earlier-ordered one fails fast. allOf()
            // itself throws once any constituent future fails, but that's
            // only used here for its "block until everyone is done" side
            // effect — the real per-mission outcome is read below, so a
            // failure here is deliberately swallowed rather than
            // propagated.
            try {
                CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0])).join();
            } catch (CompletionException ignored) {
                // handled per-mission below
            }

            Map<String, AegisReport> reports = new LinkedHashMap<>();
            Map<String, Throwable> failures = new LinkedHashMap<>();

            for (Map.Entry<String, CompletableFuture<AegisReport>> entry : futures.entrySet()) {
                try {
                    reports.put(entry.getKey(), entry.getValue().join());
                } catch (CompletionException e) {
                    failures.put(entry.getKey(), e.getCause() != null ? e.getCause() : e);
                }
            }

            return new BatchResult(reports, failures);

        } finally {
            executor.shutdown();
        }
    }
}
