package com.aegis.web;

import com.aegis.web.handler.CoverageMapHandler;
import com.aegis.web.handler.DashboardHandler;
import com.aegis.web.handler.MissionsHandler;
import com.aegis.web.handler.NaturalLanguageHandler;
import com.aegis.web.handler.RunHandler;
import com.aegis.web.handler.RunsHandler;
import com.aegis.core.reasoning.experience.ExperienceStore;
import com.aegis.core.reasoning.experience.FileExperienceStore;
import com.aegis.web.mission.MissionExecutor;
import com.aegis.web.mission.MissionHistoryStore;
import com.aegis.web.mission.MissionJobStore;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * Wires the JDK's built-in {@link HttpServer} (no external dependency
 * needed) to the request contexts. Deliberately runs on two separate
 * thread pools: a cached pool handles HTTP requests (fast — page
 * renders, redirects), while a dedicated fixed-size pool inside
 * {@link MissionExecutor} runs actual missions (slow — real Playwright
 * automation, tens of seconds to minutes). Without this split, one
 * running mission could starve the server from serving anyone else's
 * status page.
 */
public final class WebServer {

    private final HttpServer server;
    private final MissionExecutor missionExecutor;

    public WebServer(int port, int missionConcurrency, String historyDirectory, String experienceDirectory) {

        MissionJobStore jobStore = new MissionJobStore();
        MissionHistoryStore historyStore = new MissionHistoryStore(Path.of(historyDirectory));
        historyStore.loadAll().forEach(jobStore::put);
        ExperienceStore experienceStore = new FileExperienceStore(Path.of(experienceDirectory));
        this.missionExecutor = new MissionExecutor(missionConcurrency, historyStore, experienceStore);

        try {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            throw new RuntimeException("Failed to bind AEGIS Web server to port " + port, e);
        }

        server.createContext("/", new DashboardHandler(jobStore, missionExecutor));
        server.createContext("/runs", new RunsHandler(jobStore));
        server.createContext("/coverage-map", new CoverageMapHandler(jobStore));
        server.createContext("/run", new RunHandler(jobStore, missionExecutor));
        server.createContext("/run/parse", new NaturalLanguageHandler());
        server.createContext("/missions/", new MissionsHandler(jobStore));

        server.setExecutor(Executors.newCachedThreadPool());
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
        missionExecutor.shutdown();
    }

    public int port() {
        return server.getAddress().getPort();
    }
}
