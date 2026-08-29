package com.aegis.web.handler;

import com.aegis.web.mission.MissionExecutor;
import com.aegis.web.mission.MissionJob;
import com.aegis.web.mission.MissionJobStore;
import com.aegis.web.view.DashboardView;
import com.aegis.web.view.Layout;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.List;

/** {@code GET /} — the Overview bento: a live tile for whatever's currently running, plus recent-run summaries. */
public final class DashboardHandler implements HttpHandler {

    private final MissionJobStore jobStore;
    private final MissionExecutor missionExecutor;

    public DashboardHandler(MissionJobStore jobStore, MissionExecutor missionExecutor) {
        this.jobStore = jobStore;
        this.missionExecutor = missionExecutor;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        List<MissionJob> jobs = jobStore.list();
        Layout.WorkerPoolStatus workerPool = new Layout.WorkerPoolStatus(missionExecutor.activeCount(), missionExecutor.poolSize());

        HandlerSupport.sendHtml(exchange, 200, DashboardView.render(jobs, workerPool));
    }
}
