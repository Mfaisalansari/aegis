package com.aegis.web.handler;

import com.aegis.web.mission.DashboardStats;
import com.aegis.web.mission.MissionJob;
import com.aegis.web.mission.MissionJobStore;
import com.aegis.web.view.DashboardView;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.List;

/** {@code GET /} — the dashboard: every submitted mission plus summary stat tiles. */
public final class DashboardHandler implements HttpHandler {

    private final MissionJobStore jobStore;

    public DashboardHandler(MissionJobStore jobStore) {
        this.jobStore = jobStore;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        List<MissionJob> jobs = jobStore.list();
        DashboardStats stats = DashboardStats.compute(jobs);

        HandlerSupport.sendHtml(exchange, 200, DashboardView.render(jobs, stats));
    }
}
