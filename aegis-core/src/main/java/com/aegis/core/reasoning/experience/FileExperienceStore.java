package com.aegis.core.reasoning.experience;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.experience.Experience;
import com.aegis.model.experience.ExperienceOutcome;
import com.aegis.model.mission.Mission;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists aggregated per-action success/failure counts to one JSON file
 * per mission-configuration fingerprint (see {@link #fingerprintOf}), so a
 * mission run can benefit from — and a report can honestly show — every
 * prior run of the same configuration, not just repeats within a single
 * run. Same hand-built-JSON-tree convention as {@code MissionHistoryStore}
 * (write via {@code ObjectNode}/{@code .put()}/{@code .putArray()}; read
 * via {@code mapper.readTree(...)} + {@code .path(...).asX(default)});
 * corrupt/unreadable files are logged and skipped, never crash a mission.
 *
 * Deliberately stores aggregated counts, not raw per-experience records —
 * {@code DefaultPatternAnalyzer}/{@code DefaultLearningEngine} only ever
 * need (type, target, total, successful) per action to reproduce the exact
 * same {@code PatternStatistics}/adjustment a full experience history
 * would, and this keeps the file bounded by the number of distinct actions
 * a configuration's target page has, not by how many times it's ever been
 * run.
 */
public final class FileExperienceStore implements ExperienceStore {

    private static final Logger log = LoggerFactory.getLogger(FileExperienceStore.class);

    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    /** One lock per fingerprint, not a single global lock — unrelated configurations never contend. */
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public FileExperienceStore(Path directory) {
        this.directory = directory;
    }

    @Override
    public List<Experience> loadPriorExperiences(Mission mission) {

        synchronized (lockFor(mission)) {

            Map<ActionKey, Counts> counts = readCounts(fileFor(mission));
            if (counts.isEmpty()) {
                return List.of();
            }

            List<Experience> experiences = new ArrayList<>();
            MissionContext missionContext = new MissionContext(mission);

            for (Map.Entry<ActionKey, Counts> entry : counts.entrySet()) {

                ActionKey key = entry.getKey();
                long total = entry.getValue().total;
                long successful = entry.getValue().successful;

                for (long i = 0; i < total; i++) {
                    ExperienceOutcome outcome = i < successful ? ExperienceOutcome.SUCCESS : ExperienceOutcome.ERROR;
                    experiences.add(historicalExperience(missionContext, key.type(), key.target(), outcome));
                }
            }

            return experiences;
        }
    }

    @Override
    public void recordOutcome(Mission mission, List<Experience> newExperiencesThisRun) {

        if (newExperiencesThisRun.isEmpty()) {
            return;
        }

        synchronized (lockFor(mission)) {
            try {
                Map<ActionKey, Counts> merged = readCounts(fileFor(mission));

                for (Experience experience : newExperiencesThisRun) {
                    Action action = experience.candidateAction().action();
                    Counts counts = merged.computeIfAbsent(new ActionKey(action.type(), action.target()), k -> new Counts(0, 0));
                    counts.total++;
                    if (experience.outcome() == ExperienceOutcome.SUCCESS) {
                        counts.successful++;
                    }
                }

                writeCounts(mission, merged);

            } catch (IOException e) {
                log.warn("Failed to persist experience history for mission {}: {}", mission.id(), e.getMessage());
            }
        }
    }

    private Map<ActionKey, Counts> readCounts(Path file) {

        Map<ActionKey, Counts> counts = new LinkedHashMap<>();

        if (!Files.isRegularFile(file)) {
            return counts;
        }

        try {
            JsonNode root = mapper.readTree(Files.readString(file));
            for (JsonNode entry : root.path("actions")) {

                ActionType type;
                try {
                    type = ActionType.valueOf(entry.path("actionType").asText(""));
                } catch (IllegalArgumentException e) {
                    log.warn("Ignoring unknown action type '{}' in {}", entry.path("actionType").asText(""), file);
                    continue;
                }

                String target = entry.path("actionTarget").asText("");
                counts.put(new ActionKey(type, target),
                        new Counts(entry.path("total").asLong(0), entry.path("successful").asLong(0)));
            }
        } catch (Exception e) {
            log.warn("Skipping unreadable experience history file {}: {}", file, e.getMessage());
        }

        return counts;
    }

    private void writeCounts(Mission mission, Map<ActionKey, Counts> counts) throws IOException {

        Files.createDirectories(directory);

        ObjectNode root = mapper.createObjectNode();
        ArrayNode actions = root.putArray("actions");

        for (Map.Entry<ActionKey, Counts> entry : counts.entrySet()) {
            ObjectNode actionNode = actions.addObject();
            actionNode.put("actionType", entry.getKey().type().name());
            actionNode.put("actionTarget", entry.getKey().target());
            actionNode.put("total", entry.getValue().total);
            actionNode.put("successful", entry.getValue().successful);
        }

        Files.writeString(fileFor(mission), mapper.writeValueAsString(root));
    }

    /**
     * A minimal-but-valid {@link Experience} standing in for a real one
     * from a prior run — only {@code type}/{@code target}/{@code outcome}
     * carry real information; every other field is filler a caller never
     * reads for this purpose ({@code RedesignedMissionReportGenerator}'s
     * Learning tab only ever displays a {@code PatternStatistics}'s
     * representative action's type/target, never its reasoning/confidence/
     * elementTag, and {@code buildTimeline} never sees these at all — see
     * {@code Aegis.run}, which keeps historical and this-run experiences
     * separated before either reaches {@code MissionReportData.from}).
     * Built via {@link Experience#create} (never a hand-assigned id) so a
     * fresh random UUID always distinguishes it from any real experience.
     */
    private Experience historicalExperience(MissionContext missionContext, ActionType type, String target, ExperienceOutcome outcome) {

        Action action = new Action(
                UUID.randomUUID(), type, target, "", "historical",
                0.5, "historical", Duration.ZERO, Instant.now(), "");

        CandidateAction candidateAction = new CandidateAction(action, 0.5, "historical");

        Observation observation = new Observation(
                "", "", List.of(), List.of(), List.of(), List.of(), List.of(), Instant.now());

        return Experience.create(missionContext, observation, candidateAction, outcome, Duration.ZERO);
    }

    private Path fileFor(Mission mission) {
        return directory.resolve(fingerprintOf(mission) + ".json");
    }

    private Object lockFor(Mission mission) {
        return locks.computeIfAbsent(fingerprintOf(mission), k -> new Object());
    }

    /**
     * A stable identity for "the same mission configuration" across
     * separate submissions, despite {@link Mission#id()} being a fresh
     * random UUID every time. Hashes {@code parameters()} (sorted by key,
     * so map iteration order never matters) together with a normalized
     * {@code description()} — {@code description} must be included, not
     * treated as cosmetic: the natural-language mission path ({@code
     * RuleBasedMissionParser}/{@code LlmMissionParser}) puts little beyond
     * {@code baseUrl} into {@code parameters()}, so two genuinely different
     * instructions against the same site (e.g. "test login" vs "test
     * checkout") would otherwise collide onto the same fingerprint and
     * silently merge unrelated learning data. {@code id()}/{@code name()}
     * stay excluded — id is always fresh, name is just a label.
     */
    private static String fingerprintOf(Mission mission) {

        StringBuilder input = new StringBuilder();

        for (Map.Entry<String, String> entry : new TreeMap<>(mission.parameters()).entrySet()) {
            input.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }

        input.append("description=").append(mission.description().strip().toLowerCase()).append('\n');

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is a required JDK algorithm", e);
        }
    }

    private record ActionKey(ActionType type, String target) {
    }

    private static final class Counts {

        long total;
        long successful;

        Counts(long total, long successful) {
            this.total = total;
            this.successful = successful;
        }
    }
}
