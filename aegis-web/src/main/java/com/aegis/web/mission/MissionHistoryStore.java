package com.aegis.web.mission;

import com.aegis.core.report.FindingCategory;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Persists each finished mission as one JSON file, named by job id — the
 * only durable record of run history {@code aegis-web} has, since {@link
 * MissionJobStore} itself is a plain in-memory map cleared on restart.
 *
 * Deliberately separate from {@code ReportWriter}'s per-request report
 * files: those are named by timestamp only (collision-prone under
 * concurrent missions), land in a directory a user can change per
 * submission, carry no job id or mission parameters, and are never
 * written at all for a failed mission — none of which is safe to rely on
 * for reconstructing dashboard history. This store's file format and
 * location are internal to {@code aegis-web} and never user-facing.
 *
 * Same hand-built-JSON-tree convention as {@code JsonReportGenerator}
 * (write) and {@code aegis-cli}'s {@code ReportCommand} (read) — no
 * POJO/reflection binding, so a field added in a future version can't
 * break reading an older file (every read uses {@code .path(...).asX(default)}).
 *
 * Only ever called with a DONE or ERROR job — a mission still RUNNING
 * when the process stops was never written in the first place (there is
 * no checkpointing anywhere in {@code Aegis.run}, so there is nothing
 * honest to reload it into) and is simply absent from history after a
 * restart, exactly as if it had never been submitted.
 */
public final class MissionHistoryStore {

    private static final Logger log = LoggerFactory.getLogger(MissionHistoryStore.class);

    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    public MissionHistoryStore(Path directory) {
        this.directory = directory;
    }

    public void save(MissionJob job) {

        if (job.state() == MissionJob.State.RUNNING) {
            throw new IllegalArgumentException("Refusing to persist a still-RUNNING job: " + job.id());
        }

        try {
            Files.createDirectories(directory);

            ObjectNode root = mapper.createObjectNode();
            root.put("id", job.id());
            root.put("missionId", job.mission().id().toString());
            root.put("missionName", job.mission().name());
            root.put("missionDescription", job.mission().description());

            ObjectNode parameters = root.putObject("missionParameters");
            for (Map.Entry<String, String> entry : job.mission().parameters().entrySet()) {
                parameters.put(entry.getKey(), entry.getValue());
            }

            root.put("browserType", job.browserType());
            root.put("submittedAtEpochMs", job.submittedAt().toEpochMilli());
            root.put("finishedAtEpochMs", job.finishedAt().toEpochMilli());
            root.put("state", job.state().name());

            if (job.state() == MissionJob.State.ERROR) {
                root.put("errorMessage", job.errorMessage());
            } else {
                MissionSummary summary = job.summary();
                root.put("status", summary.status().name());

                ObjectNode summaryNode = root.putObject("summary");
                summaryNode.put("coveragePercent", summary.coveragePercent());
                summaryNode.put("elementsDiscovered", summary.elementsDiscovered());
                summaryNode.put("elementsInteracted", summary.elementsInteracted());
                summaryNode.put("actionsExecuted", summary.actionsExecuted());

                ArrayNode pages = summaryNode.putArray("pageCoverage");
                for (MissionSummary.PageCoverageEntry page : summary.pageCoverage()) {
                    ObjectNode pageNode = pages.addObject();
                    pageNode.put("url", page.url());
                    pageNode.put("elementsDiscovered", page.elementsDiscovered());
                    pageNode.put("elementsInteracted", page.elementsInteracted());
                    pageNode.put("coveragePercent", page.coveragePercent());
                }

                ObjectNode findingsByCategory = summaryNode.putObject("findingsByCategory");
                for (Map.Entry<FindingCategory, Integer> entry : summary.findingsByCategoryCounts().entrySet()) {
                    findingsByCategory.put(entry.getKey().name(), entry.getValue());
                }

                ArrayNode planSteps = summaryNode.putArray("planSteps");
                summary.planSteps().forEach(planSteps::add);

                root.put("htmlReport", job.htmlReport());
                root.put("jsonReport", job.jsonReport());
                root.put("textReport", job.textReport());
                root.put("redesignedHtmlReport", job.redesignedHtmlReport());
            }

            Files.writeString(directory.resolve(job.id() + ".json"), mapper.writeValueAsString(root));

        } catch (IOException e) {
            log.warn("Failed to persist mission history for {}: {}", job.id(), e.getMessage());
        }
    }

    public List<MissionJob> loadAll() {

        List<MissionJob> jobs = new ArrayList<>();

        if (!Files.isDirectory(directory)) {
            return jobs;
        }

        try (Stream<Path> files = Files.list(directory)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                try {
                    jobs.add(load(path));
                } catch (Exception e) {
                    log.warn("Skipping unreadable mission history file {}: {}", path, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to list mission history directory {}: {}", directory, e.getMessage());
        }

        return jobs;
    }

    private MissionJob load(Path path) throws IOException {

        JsonNode root = mapper.readTree(Files.readString(path));

        Map<String, String> parameters = new LinkedHashMap<>();
        root.path("missionParameters").fields()
                .forEachRemaining(entry -> parameters.put(entry.getKey(), entry.getValue().asText()));

        UUID missionId = UUID.fromString(root.path("missionId").asText(UUID.randomUUID().toString()));
        Mission mission = new Mission(
                missionId, root.path("missionName").asText(""), root.path("missionDescription").asText(""), parameters);

        String id = root.path("id").asText();
        String browserType = root.path("browserType").asText("chromium");
        Instant submittedAt = Instant.ofEpochMilli(root.path("submittedAtEpochMs").asLong());
        Instant finishedAt = Instant.ofEpochMilli(root.path("finishedAtEpochMs").asLong());
        MissionJob.State state = MissionJob.State.valueOf(root.path("state").asText());

        if (state == MissionJob.State.ERROR) {
            return MissionJob.reloaded(
                    id, mission, browserType, submittedAt, finishedAt, state,
                    root.path("errorMessage").asText(""), null, null, null, null, null);
        }

        JsonNode summaryNode = root.path("summary");

        List<MissionSummary.PageCoverageEntry> pageCoverage = new ArrayList<>();
        for (JsonNode pageNode : summaryNode.path("pageCoverage")) {
            pageCoverage.add(new MissionSummary.PageCoverageEntry(
                    pageNode.path("url").asText(""),
                    pageNode.path("elementsDiscovered").asInt(0),
                    pageNode.path("elementsInteracted").asInt(0),
                    pageNode.path("coveragePercent").asDouble(0)));
        }

        Map<FindingCategory, Integer> findingsByCategoryCounts = new LinkedHashMap<>();
        summaryNode.path("findingsByCategory").fields().forEachRemaining(entry -> {
            try {
                findingsByCategoryCounts.put(FindingCategory.valueOf(entry.getKey()), entry.getValue().asInt());
            } catch (IllegalArgumentException e) {
                log.warn("Ignoring unknown finding category '{}' in {}", entry.getKey(), path);
            }
        });

        List<String> planSteps = new ArrayList<>();
        summaryNode.path("planSteps").forEach(step -> planSteps.add(step.asText()));

        MissionSummary summary = new MissionSummary(
                MissionStatus.valueOf(root.path("status").asText()),
                summaryNode.path("coveragePercent").asDouble(0),
                summaryNode.path("elementsDiscovered").asInt(0),
                summaryNode.path("elementsInteracted").asInt(0),
                pageCoverage,
                summaryNode.path("actionsExecuted").asInt(0),
                findingsByCategoryCounts,
                planSteps);

        return MissionJob.reloaded(
                id, mission, browserType, submittedAt, finishedAt, state, null, summary,
                root.path("htmlReport").asText(null),
                root.path("jsonReport").asText(null),
                root.path("textReport").asText(null),
                root.path("redesignedHtmlReport").asText(null));
    }
}
