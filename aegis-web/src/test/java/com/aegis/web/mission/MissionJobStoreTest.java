package com.aegis.web.mission;

import com.aegis.model.mission.Mission;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionJobStoreTest {

    @Test
    void putThenFindReturnsTheSameJob() {

        MissionJobStore store = new MissionJobStore();
        MissionJob job = newJob("job-1");

        store.put(job);

        assertTrue(store.find("job-1").isPresent());
        assertEquals(job, store.find("job-1").get());
    }

    @Test
    void findingAnUnknownIdReturnsEmpty() {
        assertTrue(new MissionJobStore().find("does-not-exist").isEmpty());
    }

    @Test
    void listOnAnEmptyStoreIsEmpty() {
        assertTrue(new MissionJobStore().list().isEmpty());
    }

    @Test
    void listReturnsMostRecentlySubmittedFirst() throws InterruptedException {

        MissionJobStore store = new MissionJobStore();

        MissionJob first = newJob("job-1");
        store.put(first);
        Thread.sleep(5);
        MissionJob second = newJob("job-2");
        store.put(second);
        Thread.sleep(5);
        MissionJob third = newJob("job-3");
        store.put(third);

        assertEquals(java.util.List.of(third, second, first), store.list());
    }

    @Test
    void concurrentPutsAreAllReadableAfterward() throws InterruptedException {

        MissionJobStore store = new MissionJobStore();
        int count = 50;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch latch = new CountDownLatch(count);

        for (int i = 0; i < count; i++) {
            String id = "job-" + i;
            executor.execute(() -> {
                store.put(newJob(id));
                latch.countDown();
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        for (int i = 0; i < count; i++) {
            assertTrue(store.find("job-" + i).isPresent());
        }
    }

    private static MissionJob newJob(String id) {
        Mission mission = new Mission(UUID.randomUUID(), "Test", "Test", Map.of("baseUrl", "https://example.com"));
        return new MissionJob(id, mission, "chromium");
    }
}
