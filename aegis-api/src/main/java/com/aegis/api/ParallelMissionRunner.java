package com.aegis.api;

import com.aegis.core.Aegis;
import com.aegis.core.AegisReport;
import com.aegis.core.browser.BrowserConfig;
import com.aegis.model.mission.Mission;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
 * If any mission throws, {@code runAll} propagates that failure after
 * all missions have finished (not fail-fast/cancel-the-others) — a
 * broken site shouldn't stop the rest of a batch from reporting back,
 * which is the point of running a batch in the first place.
 */
public final class ParallelMissionRunner {

    private ParallelMissionRunner() {
    }

    public static Map<String, AegisReport> runAll(Map<String, Mission> missions, int maxConcurrency) {
        return runAll(missions, BrowserConfig.defaults(), maxConcurrency);
    }

    /** Same as {@link #runAll(Map, int)}, with one {@link BrowserConfig} shared by every mission in the batch. */
    public static Map<String, AegisReport> runAll(
            Map<String, Mission> missions, BrowserConfig browserConfig, int maxConcurrency) {

        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, maxConcurrency));

        try {

            Map<String, CompletableFuture<AegisReport>> futures = new LinkedHashMap<>();

            for (Map.Entry<String, Mission> entry : missions.entrySet()) {
                Mission mission = entry.getValue();
                futures.put(entry.getKey(), CompletableFuture.supplyAsync(() -> Aegis.run(mission, browserConfig), executor));
            }

            // Stage 5 hardening: wait for every future to complete — success
            // or failure — before reading any of them. Joining one at a time
            // below would otherwise throw on the first failing future in
            // map-iteration order while later missions are still running
            // unawaited in the background, contradicting this class's own
            // documented "propagates after all missions have finished".
            CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0])).join();

            Map<String, AegisReport> results = new LinkedHashMap<>();

            for (Map.Entry<String, CompletableFuture<AegisReport>> entry : futures.entrySet()) {
                results.put(entry.getKey(), entry.getValue().join());
            }

            return results;

        } finally {
            executor.shutdown();
        }
    }
}
