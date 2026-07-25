package com.aegis.launcher;

import com.aegis.core.Aegis;
import com.aegis.core.AegisReport;
import com.aegis.core.browser.BrowserConfig;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stage 5 "Performance & Quality" benchmarking. A dev-only diagnostic
 * harness — same category as this module's other {@code *Main} classes,
 * not shipped product surface — not a JMH microbenchmark: AEGIS's real
 * cost is Playwright-driven page interaction, not JIT-sensitive
 * hot-loop code, so what's actually useful here is real, repeated,
 * real-browser mission timing rather than nanosecond-precision
 * method-level measurement.
 *
 * Runs a fixed real mission (SauceDemo login, headless, {@code greedy}
 * strategy) {@link #RUNS} times and reports min/max/mean/median mission
 * duration plus aggregate actions/sec.
 */
public class BenchmarkMain {

    private static final int RUNS = 5;

    public static void main(String[] args) {

        List<Long> durationsMs = new ArrayList<>();
        int totalActions = 0;
        long totalElapsedMs = 0;

        for (int i = 1; i <= RUNS; i++) {

            Mission mission = new Mission(
                    UUID.randomUUID(),
                    "Benchmark Run " + i,
                    "Login to saucedemo.com with valid credentials",
                    Map.of(
                            "baseUrl", "https://www.saucedemo.com/",
                            "username", "standard_user",
                            "password", "secret_sauce",
                            "successUrlContains", "inventory.html",
                            "explorationStrategy", "greedy"
                    )
            );

            long start = System.currentTimeMillis();
            AegisReport report = Aegis.run(mission, new BrowserConfig("chromium", true));
            long elapsed = System.currentTimeMillis() - start;

            int actions = report.missionResult().context().getExecutionState().getActions().size();

            durationsMs.add(elapsed);
            totalActions += actions;
            totalElapsedMs += elapsed;

            String note = report.status() == MissionStatus.SUCCESS ? "" : " (non-SUCCESS — timing still recorded)";
            System.out.printf("Run %d: %s in %dms, %d action(s)%s%n", i, report.status(), elapsed, actions, note);
        }

        printSummary(durationsMs, totalActions, totalElapsedMs);
    }

    private static void printSummary(List<Long> durationsMs, int totalActions, long totalElapsedMs) {

        List<Long> sorted = new ArrayList<>(durationsMs);
        sorted.sort(Long::compareTo);

        long min = sorted.get(0);
        long max = sorted.get(sorted.size() - 1);
        double mean = sorted.stream().mapToLong(Long::longValue).average().orElse(0);
        long median = sorted.get(sorted.size() / 2);
        double actionsPerSec = totalActions / (totalElapsedMs / 1000.0);

        System.out.println();
        System.out.println("=================================");
        System.out.println("Benchmark results (" + durationsMs.size() + " runs)");
        System.out.printf("  min mission duration:    %dms%n", min);
        System.out.printf("  max mission duration:    %dms%n", max);
        System.out.printf("  mean mission duration:   %.0fms%n", mean);
        System.out.printf("  median mission duration: %dms%n", median);
        System.out.printf("  aggregate throughput:    %.2f actions/sec (%d actions / %dms)%n",
                actionsPerSec, totalActions, totalElapsedMs);
        System.out.println("=================================");
    }
}
