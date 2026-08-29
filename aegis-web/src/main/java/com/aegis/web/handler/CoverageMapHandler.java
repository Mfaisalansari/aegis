package com.aegis.web.handler;

import com.aegis.web.mission.CoverageMapData;
import com.aegis.web.mission.MissionJob;
import com.aegis.web.mission.MissionJobStore;
import com.aegis.web.view.CoverageMapView;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.List;

/** {@code GET /coverage-map} — every page touched this session, grouped by app, by element coverage. */
public final class CoverageMapHandler implements HttpHandler {

    private final MissionJobStore jobStore;

    public CoverageMapHandler(MissionJobStore jobStore) {
        this.jobStore = jobStore;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        List<MissionJob> jobs = jobStore.list();
        CoverageMapData data = CoverageMapData.compute(jobs);

        HandlerSupport.sendHtml(exchange, 200, CoverageMapView.render(data));
    }
}
