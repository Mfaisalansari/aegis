package com.aegis.core.knowledge;

import java.util.List;
import java.util.Map;

/**
 * Organization-declared knowledge about the application under test —
 * node naming overrides, business flows, journey definitions. Lives in
 * {@code aegis-core} (like {@code com.aegis.core.browser.BrowserConfig})
 * because the built-in {@link KnowledgeProvider}s here need to consume
 * it directly; YAML loading itself ({@code knowledge.yml} →
 * this type) lives in {@code aegis-api}'s {@code KnowledgeConfigLoader},
 * the same split already used for {@code BrowserConfig}.
 *
 * Deliberately data-only: AEGIS never infers business meaning from
 * this, it only applies exactly what's declared here on top of what it
 * discovered on its own.
 */
public record KnowledgeConfig(
        int version,
        List<NodeConfig> nodes,
        List<FlowConfig> flows,
        List<JourneyDefinitionConfig> journeys,
        InspectionConfig inspection
) {

    public KnowledgeConfig {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        flows = flows == null ? List.of() : List.copyOf(flows);
        journeys = journeys == null ? List.of() : List.copyOf(journeys);
        inspection = inspection == null ? InspectionConfig.disabled() : inspection;
    }

    public static KnowledgeConfig empty() {
        return new KnowledgeConfig(1, List.of(), List.of(), List.of(), InspectionConfig.disabled());
    }

    /** One {@code nodes:} entry — a URL-pattern-matched naming override for a discovered state. */
    public record NodeConfig(
            String urlPattern,
            String key,
            String displayName,
            String technicalName,
            List<String> aliases,
            Map<String, String> metadata
    ) {
        public NodeConfig {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    /** One {@code flows:} entry — a declared, unordered business-capability grouping of node keys. */
    public record FlowConfig(
            String key,
            String name,
            List<String> nodeKeys,
            String description,
            Map<String, String> metadata
    ) {
        public FlowConfig {
            nodeKeys = nodeKeys == null ? List.of() : List.copyOf(nodeKeys);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    /**
     * One {@code journeys:} entry — a declared, ordered reference path of
     * node keys. {@code expectedMaxSteps} is an optional, organization-
     * declared expectation (null when not declared) — the UX Quality
     * catalog's navigation-friction check only ever fires against this
     * explicit number, never an AEGIS-invented "too many steps" judgment.
     */
    public record JourneyDefinitionConfig(
            String key,
            String name,
            List<String> nodeKeys,
            String description,
            Map<String, String> metadata,
            Integer expectedMaxSteps
    ) {
        public JourneyDefinitionConfig {
            nodeKeys = nodeKeys == null ? List.of() : List.copyOf(nodeKeys);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
