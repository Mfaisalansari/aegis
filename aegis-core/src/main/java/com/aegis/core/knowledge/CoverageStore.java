package com.aegis.core.knowledge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AEGIS 2.0 Phase 5 — persisted, cross-run coverage, keyed by application
 * name. This is the "real consumer" ARCHITECTURE.md's Memory Scope
 * section said cross-run persistence was waiting for; it also resolves
 * the identity problem that section raised — {@code Mission.id()} is a
 * random UUID per launch and genuinely unusable as a key, but {@code
 * Mission.name()} already IS the stable identity {@code
 * KnowledgeBaseBuilder.build(applicationName, ...)} has used as {@code
 * applicationName} everywhere in this codebase since the Knowledge
 * Enrichment Layer was built — this store just reuses that existing
 * convention rather than inventing a new one.
 *
 * Deliberately opt-in, not wired into any live mission's own completion
 * path or into {@code aegis-observation-api}'s {@code ObservationSession}
 * (which depends only on {@code aegis-model}, never {@code aegis-core},
 * by design) — a caller that has already built a {@link KnowledgeBase}
 * (a live mission's own reporting code, or an ingested session's caller)
 * invokes {@link #merge} explicitly. Automatic end-of-mission wiring is a
 * natural follow-up, deliberately not done here to avoid touching the
 * live mission engine.
 *
 * Storage format is deliberately plain text (one node key per line), not
 * JSON — this store's only job is a flat set of strings, and {@code
 * aegis-core} has no JSON dependency of its own to reach for (that lives
 * in {@code aegis-api}).
 */
public final class CoverageStore {

    /** JVM-wide mutual exclusion per coverage file — {@link FileLock} alone doesn't protect against concurrent threads inside this same process. */
    private static final ConcurrentHashMap<Path, Object> IN_PROCESS_LOCKS = new ConcurrentHashMap<>();

    private final Path directory;

    public CoverageStore(Path directory) {
        this.directory = directory;
    }

    public static CoverageStore defaultLocation() {
        return new CoverageStore(Path.of(System.getProperty("user.home"), ".aegis", "coverage"));
    }

    public CoverageRecord load(String applicationName) {

        Path file = fileFor(applicationName);

        if (!Files.exists(file)) {
            return CoverageRecord.empty(applicationName);
        }

        try {
            Set<String> keys = new LinkedHashSet<>(Files.readAllLines(file));
            keys.remove("");
            return new CoverageRecord(applicationName, keys, Files.getLastModifiedTime(file).toInstant());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read coverage store for '" + applicationName + "'", e);
        }
    }

    /**
     * Every node key in {@code knowledgeBase}'s {@link NodeCatalog}, unioned into what's already persisted for
     * {@code applicationName}. Also persists each node's raw {@link NodeNaming#slug} alongside its (possibly
     * {@code knowledge.yml}-configured) {@code key} — {@link KnowledgeAwareActionScorer} has no access to
     * whatever {@link KnowledgeConfig} produced these keys and can only recompute the raw slug from a URL, so
     * without this a node with a configured key override would never register as covered from its perspective.
     */
    public CoverageRecord merge(String applicationName, KnowledgeBase knowledgeBase) {

        NodeCatalog nodeCatalog = knowledgeBase.require(NodeCatalog.class);
        StateCatalog stateCatalog = knowledgeBase.require(StateCatalog.class);

        Set<String> newKeys = new LinkedHashSet<>();

        for (Node node : nodeCatalog.nodes()) {
            newKeys.add(node.key());
            stateCatalog.byId(node.stateId())
                    .ifPresent(state -> newKeys.add(NodeNaming.slug(state.url())));
        }

        return merge(applicationName, newKeys);
    }

    /**
     * A union with whatever's already persisted, never a replacement — coverage only ever grows across runs.
     * The read-modify-write is done under an exclusive {@link FileLock} (cross-process, e.g. concurrent CI
     * jobs both merging the same application's coverage) plus an in-process lock (a {@link FileLock} alone
     * doesn't arbitrate between threads of this same JVM), so two concurrent merges can't clobber each other.
     */
    public CoverageRecord merge(String applicationName, Set<String> newlyVisitedNodeKeys) {

        Path file = fileFor(applicationName);

        synchronized (IN_PROCESS_LOCKS.computeIfAbsent(file, unused -> new Object())) {

            try {
                Files.createDirectories(directory);

                try (FileChannel channel = FileChannel.open(file,
                        StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
                     FileLock lock = channel.lock()) {

                    Set<String> combined = new LinkedHashSet<>(readKeys(channel));
                    combined.addAll(newlyVisitedNodeKeys);

                    String content = combined.isEmpty() ? "" : String.join("\n", combined) + "\n";
                    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

                    channel.position(0);
                    channel.write(ByteBuffer.wrap(bytes));
                    channel.truncate(bytes.length);

                    return new CoverageRecord(applicationName, combined, Instant.now());
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to write coverage store for '" + applicationName + "'", e);
            }
        }
    }

    private Set<String> readKeys(FileChannel channel) throws IOException {

        channel.position(0);
        ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
        channel.read(buffer);
        String content = new String(buffer.array(), StandardCharsets.UTF_8);

        Set<String> keys = new LinkedHashSet<>(List.of(content.split("\\R", -1)));
        keys.remove("");
        return keys;
    }

    private Path fileFor(String applicationName) {
        return directory.resolve(slugify(applicationName) + ".txt");
    }

    private String slugify(String applicationName) {

        String slug = applicationName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        return slug.isEmpty() ? "unnamed" : slug;
    }
}
