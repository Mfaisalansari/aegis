package com.aegis.web.handler;

import com.aegis.core.stream.MissionStreamEvent;
import com.aegis.web.mission.MissionJob;
import com.aegis.web.mission.MissionJobStore;
import com.aegis.web.view.MissionStatusView;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * {@code GET /missions/*} — the JDK's {@code HttpServer} only does
 * exact/prefix routing, no path-pattern matching, so this one handler
 * does its own manual suffix dispatch: {@code /missions/{id}} (status
 * page), {@code /missions/{id}/status} (JSON), and
 * {@code /missions/{id}/report[.json|.txt]} (view/download).
 *
 * Report bytes come from {@link MissionJob}'s own accessors, which serve
 * the live in-memory report when this session ran the mission, or the
 * frozen strings loaded from {@code MissionHistoryStore} when it was
 * reloaded after a restart — this handler doesn't need to know which.
 */
public final class MissionsHandler implements HttpHandler {

    private static final String PREFIX = "/missions/";

    private final MissionJobStore jobStore;

    public MissionsHandler(MissionJobStore jobStore) {
        this.jobStore = jobStore;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        String path = exchange.getRequestURI().getPath();

        if (!path.startsWith(PREFIX) || path.length() == PREFIX.length()) {
            HandlerSupport.sendText(exchange, 404, "Not Found");
            return;
        }

        String remainder = path.substring(PREFIX.length());
        int slash = remainder.indexOf('/');
        String id = slash >= 0 ? remainder.substring(0, slash) : remainder;
        String suffix = slash >= 0 ? remainder.substring(slash + 1) : "";

        Optional<MissionJob> maybeJob = jobStore.find(id);
        if (maybeJob.isEmpty()) {
            HandlerSupport.sendText(exchange, 404, "No such mission: " + id);
            return;
        }

        MissionJob job = maybeJob.get();
        boolean download = "1".equals(HandlerSupport.queryParam(exchange, "download"));

        switch (suffix) {
            case "" -> HandlerSupport.sendHtml(exchange, 200, MissionStatusView.render(job));
            case "status" -> handleStatusJson(exchange, job);
            case "report" -> handleReport(exchange, job, "text/html; charset=utf-8", MissionJob::htmlReport, "html", download);
            case "report.json" -> handleReport(exchange, job, "application/json; charset=utf-8", MissionJob::jsonReport, "json", download);
            case "report.txt" -> handleReport(exchange, job, "text/plain; charset=utf-8", MissionJob::textReport, "txt", download);
            case "report/redesigned" -> handleReport(exchange, job, "text/html; charset=utf-8", MissionJob::redesignedHtmlReport, "html", download);
            default -> HandlerSupport.sendText(exchange, 404, "Not Found");
        }
    }

    /**
     * Polled every ~1.5-2s by {@code LiveMissionStreamView}'s client-side
     * JS while a mission is RUNNING — {@code id}/{@code state}/{@code
     * status} keep their original shape exactly (existing consumers are
     * unaffected); {@code iteration}/{@code maxIterations}/{@code
     * elapsedSeconds}/{@code events} are new. The full event list is sent
     * every poll rather than a {@code ?since=} cursor — a whole mission
     * caps out around {@code maxIterations() * 3} events, small enough
     * that the client just tracks how many it's already rendered and
     * appends the new tail itself.
     */
    private void handleStatusJson(HttpExchange exchange, MissionJob job) throws IOException {

        List<MissionStreamEvent> events = job.events();

        StringBuilder eventsJson = new StringBuilder("[");
        for (int i = 0; i < events.size(); i++) {
            MissionStreamEvent event = events.get(i);
            if (i > 0) {
                eventsJson.append(',');
            }
            eventsJson.append("{\"kind\":\"").append(event.kind())
                    .append("\",\"headline\":\"").append(HandlerSupport.escapeJson(event.headline()))
                    .append("\",\"detail\":\"").append(HandlerSupport.escapeJson(event.detail()))
                    .append("\",\"timestamp\":\"").append(event.timestamp())
                    .append("\"}");
        }
        eventsJson.append(']');

        String json = "{\"id\":\"" + job.id() + "\",\"state\":\"" + job.state() + "\""
                + ",\"iteration\":" + job.iteration()
                + ",\"maxIterations\":" + job.maxIterations()
                + ",\"elapsedSeconds\":" + job.elapsedSeconds()
                + ",\"events\":" + eventsJson
                + (job.state() == MissionJob.State.DONE ? ",\"status\":\"" + job.summary().status() + "\"" : "")
                + "}";

        HandlerSupport.sendFile(exchange, 200, "application/json; charset=utf-8", json, null);
    }

    private void handleReport(
            HttpExchange exchange, MissionJob job, String contentType,
            Function<MissionJob, String> extractor, String extension, boolean download) throws IOException {

        if (job.state() != MissionJob.State.DONE) {
            HandlerSupport.sendText(exchange, 404, "Report not available — mission has not finished successfully.");
            return;
        }

        String content = extractor.apply(job);
        String filename = download ? "aegis-report-" + job.id() + "." + extension : null;

        HandlerSupport.sendFile(exchange, 200, contentType, content, filename);
    }
}
