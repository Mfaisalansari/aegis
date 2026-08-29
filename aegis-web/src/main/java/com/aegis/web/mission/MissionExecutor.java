package com.aegis.web.mission;

import com.aegis.api.ReportWriter;
import com.aegis.core.Aegis;
import com.aegis.core.AegisReport;
import com.aegis.core.browser.BrowserConfig;
import com.aegis.core.knowledge.KnowledgeConfig;
import com.aegis.core.reasoning.experience.ExperienceStore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

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
    private final MissionHistoryStore historyStore;
    private final ExperienceStore experienceStore;

    public MissionExecutor(int concurrency, MissionHistoryStore historyStore, ExperienceStore experienceStore) {
        this.executor = Executors.newFixedThreadPool(Math.max(1, concurrency));
        this.historyStore = historyStore;
        this.experienceStore = experienceStore;
    }

    public void submit(MissionJob job, BrowserConfig browserConfig, KnowledgeConfig knowledgeConfig, String reportsDirectory) {
        executor.execute(() -> {
            try {
                AegisReport report = Aegis.run(job.mission(), browserConfig, knowledgeConfig, job, experienceStore);
                ReportWriter.writeAll(report, reportsDirectory);
                job.complete(report);
                historyStore.save(job);
            } catch (Exception e) {
                job.fail(e.toString());
                historyStore.save(job);
            }
        });
    }

    /** Real, currently-executing mission count — backs the dashboard's "worker pool" widget. */
    public int activeCount() {
        return ((ThreadPoolExecutor) executor).getActiveCount();
    }

    /**
     * The real configured pool size (see {@link #MissionExecutor(int, MissionHistoryStore, ExperienceStore)}) —
     * {@code getMaximumPoolSize()}, not {@code getPoolSize()}: a fixed
     * thread pool only spins threads up as tasks arrive, so the latter
     * would under-report "4" as "0" or "1" on an idle server instead of
     * the actual configured capacity.
     */
    public int poolSize() {
        return ((ThreadPoolExecutor) executor).getMaximumPoolSize();
    }

    public void shutdown() {
        executor.shutdown();
    }
}
