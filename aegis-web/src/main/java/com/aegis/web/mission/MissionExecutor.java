package com.aegis.web.mission;

import com.aegis.api.ReportWriter;
import com.aegis.core.Aegis;
import com.aegis.core.AegisReport;
import com.aegis.core.browser.BrowserConfig;
import com.aegis.core.knowledge.KnowledgeConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs missions on a dedicated background thread pool, deliberately
 * separate from the HTTP server's own request-handling threads — a
 * mission is real Playwright browser automation and can take tens of
 * seconds to minutes, and must never occupy the thread that's supposed
 * to be serving the next page load or status poll. Mirrors the
 * concurrency pattern already proven safe in {@code ParallelMissionRunner}
 * ({@code Aegis.run} has no shared mutable state, so concurrent calls
 * from a fixed thread pool are safe).
 */
public final class MissionExecutor {

    private final ExecutorService executor;

    public MissionExecutor(int concurrency) {
        this.executor = Executors.newFixedThreadPool(Math.max(1, concurrency));
    }

    public void submit(MissionJob job, BrowserConfig browserConfig, KnowledgeConfig knowledgeConfig, String reportsDirectory) {
        executor.execute(() -> {
            try {
                AegisReport report = Aegis.run(job.mission(), browserConfig, knowledgeConfig);
                ReportWriter.writeAll(report, reportsDirectory);
                job.complete(report);
            } catch (Exception e) {
                job.fail(e.toString());
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}
