package com.aegis.web.mission;

import com.aegis.model.mission.MissionStatus;

import java.time.Duration;
import java.util.List;

/**
 * Summary tiles for the dashboard — pure computation over a snapshot of
 * jobs, kept separate from {@code DashboardView} so it's unit-testable
 * without a live server, mirroring how {@code MissionRequestMapper} is
 * kept separate from its handler.
 */
public record DashboardStats(int total, String passRateLabel, long avgDurationSeconds, int active) {

    public static DashboardStats compute(List<MissionJob> jobs) {

        int active = 0;
        int finished = 0;
        int succeeded = 0;
        long totalDurationSeconds = 0;

        for (MissionJob job : jobs) {
            switch (job.state()) {
                case RUNNING -> active++;
                case DONE -> {
                    finished++;
                    totalDurationSeconds += Duration.between(job.submittedAt(), job.finishedAt()).toSeconds();
                    if (job.report().status() == MissionStatus.SUCCESS) {
                        succeeded++;
                    }
                }
                case ERROR -> {
                    finished++;
                    totalDurationSeconds += Duration.between(job.submittedAt(), job.finishedAt()).toSeconds();
                }
            }
        }

        String passRateLabel = finished == 0 ? "—" : Math.round((succeeded * 100.0) / finished) + "%";
        long avgDurationSeconds = finished == 0 ? 0 : totalDurationSeconds / finished;

        return new DashboardStats(jobs.size(), passRateLabel, avgDurationSeconds, active);
    }
}
