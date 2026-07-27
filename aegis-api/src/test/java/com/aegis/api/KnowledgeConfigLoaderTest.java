package com.aegis.api;

import com.aegis.core.knowledge.KnowledgeConfig;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeConfigLoaderTest {

    @Test
    void parsesNodesFlowsAndJourneysCorrectly() {

        String yaml = """
                version: 1

                nodes:
                  - urlPattern: "*/customers/search*"
                    key: customer-search
                    displayName: "Customer Search"
                    technicalName: "CUST_SEARCH"
                    aliases: ["Find Customer"]
                    metadata: { owner: "billing-team" }

                flows:
                  - key: policy-creation
                    name: "Policy Creation"
                    nodes: [customer-search, create-policy]
                    description: "Creating a new policy"

                journeys:
                  - key: onboarding
                    name: "New Customer Onboarding"
                    nodes: [login, dashboard, create-policy]
                    expectedMaxSteps: 5
                """;

        KnowledgeConfig config = KnowledgeConfigLoader.load(inputStream(yaml));

        assertEquals(1, config.version());

        assertEquals(1, config.nodes().size());
        assertEquals("customer-search", config.nodes().get(0).key());
        assertEquals("Customer Search", config.nodes().get(0).displayName());
        assertEquals("billing-team", config.nodes().get(0).metadata().get("owner"));
        assertEquals("Find Customer", config.nodes().get(0).aliases().get(0));

        assertEquals(1, config.flows().size());
        assertEquals("Policy Creation", config.flows().get(0).name());
        assertEquals(2, config.flows().get(0).nodeKeys().size());

        assertEquals(1, config.journeys().size());
        assertEquals("New Customer Onboarding", config.journeys().get(0).name());
        assertEquals(3, config.journeys().get(0).nodeKeys().size());
        assertEquals(5, config.journeys().get(0).expectedMaxSteps());
    }

    @Test
    void parsesTheInspectionBlockCorrectly() {

        String yaml = """
                inspection:
                  captureDom: true
                  consoleWarnings: true
                  contrastThreshold: 3.0
                  probeLinks: true
                  noiseDenyPatterns: ["known-noise"]
                """;

        KnowledgeConfig config = KnowledgeConfigLoader.load(inputStream(yaml));

        assertTrue(config.inspection().captureDom());
        assertTrue(config.inspection().consoleWarnings());
        assertEquals(3.0, config.inspection().contrastThreshold());
        assertTrue(config.inspection().probeLinks());
        assertEquals("known-noise", config.inspection().noiseDenyPatterns().get(0));
    }

    @Test
    void missingInspectionBlockFallsBackToDisabled() {

        KnowledgeConfig config = KnowledgeConfigLoader.load(inputStream("version: 1\n"));

        assertEquals(com.aegis.core.knowledge.InspectionConfig.disabled(), config.inspection());
    }

    @Test
    void expectedMaxStepsDefaultsToNullWhenOmitted() {

        String yaml = """
                journeys:
                  - key: onboarding
                    name: "New Customer Onboarding"
                    nodes: [login, dashboard]
                """;

        KnowledgeConfig config = KnowledgeConfigLoader.load(inputStream(yaml));

        assertNull(config.journeys().get(0).expectedMaxSteps());
    }

    @Test
    void anEmptyFileProducesAnEmptyConfig() {

        KnowledgeConfig config = KnowledgeConfigLoader.load(inputStream(""));

        assertTrue(config.nodes().isEmpty());
        assertTrue(config.flows().isEmpty());
        assertTrue(config.journeys().isEmpty());
    }

    @Test
    void missingSectionsFallBackToEmptyLists() {

        KnowledgeConfig config = KnowledgeConfigLoader.load(inputStream("version: 1\n"));

        assertTrue(config.nodes().isEmpty());
    }

    @Test
    void anUnsupportedVersionThrowsAClearError() {

        AegisConfigException e = assertThrows(AegisConfigException.class,
                () -> KnowledgeConfigLoader.load(inputStream("version: 99\n")));

        assertTrue(e.getMessage().contains("99"));
    }

    @Test
    void malformedYamlThrowsAegisConfigException() {

        assertThrows(AegisConfigException.class, () -> KnowledgeConfigLoader.load(inputStream("nodes: [this is not: valid: yaml")));
    }

    @Test
    void aTopLevelListInsteadOfAMappingThrowsAegisConfigException() {
        assertThrows(AegisConfigException.class, () -> KnowledgeConfigLoader.load(inputStream("- a\n- b\n")));
    }

    private InputStream inputStream(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }
}
