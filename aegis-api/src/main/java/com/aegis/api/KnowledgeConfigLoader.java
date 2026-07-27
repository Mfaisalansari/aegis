package com.aegis.api;

import com.aegis.core.knowledge.InspectionConfig;
import com.aegis.core.knowledge.KnowledgeConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses {@code knowledge.yml} into a {@link KnowledgeConfig} — same
 * hand-mapped-tree approach as {@link AegisConfigLoader} (SnakeYAML into
 * a generic {@code Map}/{@code List}, manually walked, no reflection
 * binding). Deliberately a separate file/loader from {@code
 * application.yml}: this is organization knowledge about the app under
 * test, not mission-run configuration.
 */
public final class KnowledgeConfigLoader {

    private static final int SUPPORTED_VERSION = 1;

    private KnowledgeConfigLoader() {
    }

    public static KnowledgeConfig load(Path path) {

        try (InputStream in = Files.newInputStream(path)) {
            return load(in);
        } catch (IOException e) {
            throw new AegisConfigException("Failed to read knowledge config file: " + path, e);
        }
    }

    public static KnowledgeConfig load(InputStream in) {

        Object parsed;

        try {
            parsed = new Yaml().load(in);
        } catch (RuntimeException e) {
            throw new AegisConfigException("Failed to parse YAML knowledge config", e);
        }

        if (parsed == null) {
            return KnowledgeConfig.empty();
        }

        if (!(parsed instanceof Map<?, ?> root)) {
            throw new AegisConfigException("Knowledge config file must contain a YAML mapping at the top level");
        }

        int version = intOr(root, "version", SUPPORTED_VERSION);

        if (version != SUPPORTED_VERSION) {
            throw new AegisConfigException(
                    "Unsupported knowledge config version: " + version + " (this build only understands version " + SUPPORTED_VERSION + ")");
        }

        return new KnowledgeConfig(
                version,
                readNodes(listOfMaps(root, "nodes")),
                readFlows(listOfMaps(root, "flows")),
                readJourneys(listOfMaps(root, "journeys")),
                readInspection(root)
        );
    }

    /** {@code inspection:} — Page Inspection Layer tuning, a {@code knowledge.yml} sibling of nodes/flows/journeys. Absent entirely falls back to {@link InspectionConfig#disabled()}. */
    private static InspectionConfig readInspection(Map<?, ?> root) {

        Object value = root.get("inspection");

        if (!(value instanceof Map<?, ?> section)) {
            return InspectionConfig.disabled();
        }

        return new InspectionConfig(
                AegisConfigLoader.boolOr(section, "captureDom", false),
                AegisConfigLoader.boolOr(section, "consoleWarnings", false),
                doubleOr(section, "contrastThreshold", 4.5),
                AegisConfigLoader.boolOr(section, "probeLinks", false),
                stringList(section, "noiseDenyPatterns")
        );
    }

    private static double doubleOr(Map<?, ?> section, String key, double fallback) {
        Object value = section.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static List<KnowledgeConfig.NodeConfig> readNodes(List<Map<?, ?>> entries) {

        List<KnowledgeConfig.NodeConfig> nodes = new ArrayList<>();

        for (Map<?, ?> entry : entries) {
            nodes.add(new KnowledgeConfig.NodeConfig(
                    string(entry, "urlPattern"),
                    string(entry, "key"),
                    string(entry, "displayName"),
                    string(entry, "technicalName"),
                    stringList(entry, "aliases"),
                    stringMap(entry, "metadata")
            ));
        }

        return nodes;
    }

    private static List<KnowledgeConfig.FlowConfig> readFlows(List<Map<?, ?>> entries) {

        List<KnowledgeConfig.FlowConfig> flows = new ArrayList<>();

        for (Map<?, ?> entry : entries) {
            flows.add(new KnowledgeConfig.FlowConfig(
                    string(entry, "key"),
                    string(entry, "name"),
                    stringList(entry, "nodes"),
                    string(entry, "description"),
                    stringMap(entry, "metadata")
            ));
        }

        return flows;
    }

    private static List<KnowledgeConfig.JourneyDefinitionConfig> readJourneys(List<Map<?, ?>> entries) {

        List<KnowledgeConfig.JourneyDefinitionConfig> journeys = new ArrayList<>();

        for (Map<?, ?> entry : entries) {
            journeys.add(new KnowledgeConfig.JourneyDefinitionConfig(
                    string(entry, "key"),
                    string(entry, "name"),
                    stringList(entry, "nodes"),
                    string(entry, "description"),
                    stringMap(entry, "metadata"),
                    integerOrNull(entry, "expectedMaxSteps")
            ));
        }

        return journeys;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<?, ?>> listOfMaps(Map<?, ?> root, String key) {

        Object value = root.get(key);

        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<Map<?, ?>> entries = new ArrayList<>();

        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                entries.add(map);
            }
        }

        return entries;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Map<?, ?> section, String key) {

        Object value = section.get(key);

        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<String> strings = new ArrayList<>();

        for (Object item : list) {
            if (item != null) {
                strings.add(String.valueOf(item));
            }
        }

        return strings;
    }

    private static Map<String, String> stringMap(Map<?, ?> section, String key) {

        Object value = section.get(key);

        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }

        Map<String, String> result = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }

        return result;
    }

    private static String string(Map<?, ?> section, String key) {
        Object value = section.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static int intOr(Map<?, ?> section, String key, int fallback) {
        Object value = section.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    /** Unlike {@link #intOr}, distinguishes "absent" (null, the check never fires) from "explicitly 0". */
    private static Integer integerOrNull(Map<?, ?> section, String key) {
        Object value = section.get(key);
        return value instanceof Number number ? number.intValue() : null;
    }
}
