package com.aegis.web.mission;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory job registry — no persistence, cleared on server restart. */
public final class MissionJobStore {

    private final ConcurrentHashMap<String, MissionJob> jobs = new ConcurrentHashMap<>();

    public void put(MissionJob job) {
        jobs.put(job.id(), job);
    }

    public Optional<MissionJob> find(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    /** Every submitted job, most recently submitted first. */
    public List<MissionJob> list() {
        return jobs.values().stream()
                .sorted(Comparator.comparing(MissionJob::submittedAt).reversed())
                .toList();
    }
}
