package com.aegis.web.handler;

import com.aegis.core.AegisReport;
import com.aegis.web.mission.MissionJob;
import com.aegis.web.mission.MissionJobStore;
import com.aegis.web.view.MissionStatusView;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Optional;

/**
 * {@code GET /missions/*} — the JDK's {@code HttpServer} only does
 * exact/prefix routing, no path-pattern matching, so this one handler
 * does its own manual suffix dispatch: {@code /missions/{id}} (status
 * page), {@code /missions/{id}/status} (JSON), and
 * {@code /missions/{id}/report[.json|.txt]} (view/download, straight
 * from the in-memory {@link AegisReport} — never re-read from disk by a
 * request-supplied path, so there's no path-traversal surface).
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
            case "report" -> handleReport(exchange, job, "text/html; charset=utf-8", AegisReport::htmlReport, "html", download);
            case "report.json" -> handleReport(exchange, job, "application/json; charset=utf-8", AegisReport::jsonReport, "json", download);
            case "report.txt" -> handleReport(exchange, job, "text/plain; charset=utf-8", AegisReport::textReport, "txt", download);
            default -> HandlerSupport.sendText(exchange, 404, "Not Found");
        }
    }

    private void handleStatusJson(HttpExchange exchange, MissionJob job) throws IOException {

        String json = "{\"id\":\"" + job.id() + "\",\"state\":\"" + job.state()
                + (job.state() == MissionJob.State.DONE ? "\",\"status\":\"" + job.report().status() + "\"" : "\"")
                + "}";

        HandlerSupport.sendFile(exchange, 200, "application/json; charset=utf-8", json, null);
    }

    private void handleReport(
            HttpExchange exchange, MissionJob job, String contentType,
            java.util.function.Function<AegisReport, String> extractor, String extension, boolean download) throws IOException {

        if (job.state() != MissionJob.State.DONE) {
            HandlerSupport.sendText(exchange, 404, "Report not available — mission has not finished successfully.");
            return;
        }

        String content = extractor.apply(job.report());
        String filename = download ? "aegis-report-" + job.id() + "." + extension : null;

        HandlerSupport.sendFile(exchange, 200, contentType, content, filename);
    }
}
