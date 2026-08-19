package com.aegis.web.handler;

import com.aegis.web.form.FormBodyParser;
import com.aegis.web.form.MappingResult;
import com.aegis.web.form.MissionFormRequest;
import com.aegis.web.form.MissionRequestMapper;
import com.aegis.web.mission.MissionExecutor;
import com.aegis.web.mission.MissionJob;
import com.aegis.web.mission.MissionJobStore;
import com.aegis.web.view.MissionFormView;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@code /run} — GET renders the blank mission form, POST validates the submission and either starts the mission or re-renders the form with errors. */
public final class RunHandler implements HttpHandler {

    private final MissionJobStore jobStore;
    private final MissionExecutor missionExecutor;

    public RunHandler(MissionJobStore jobStore, MissionExecutor missionExecutor) {
        this.jobStore = jobStore;
        this.missionExecutor = missionExecutor;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        String method = exchange.getRequestMethod();

        if ("GET".equalsIgnoreCase(method)) {
            String html = MissionFormView.render(MissionFormRequest.defaults(), List.of());
            HandlerSupport.sendHtml(exchange, 200, html);
            return;
        }

        if (!"POST".equalsIgnoreCase(method)) {
            HandlerSupport.sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        Map<String, String> values = FormBodyParser.parse(HandlerSupport.readBody(exchange));
        MissionFormRequest form = MissionFormRequest.fromSubmittedValues(values);

        MappingResult result = MissionRequestMapper.map(form);

        if (!result.isValid()) {
            String html = MissionFormView.render(form, result.errors());
            HandlerSupport.sendHtml(exchange, 200, html);
            return;
        }

        String id = UUID.randomUUID().toString();
        MissionJob job = new MissionJob(id, result.mission(), result.browserConfig().type());
        jobStore.put(job);

        missionExecutor.submit(job, result.browserConfig(), result.knowledgeConfig(), result.reportDirectory());

        HandlerSupport.redirect(exchange, "/missions/" + id);
    }
}
