package com.aegis.core.knowledge;

import com.aegis.model.finding.FindingSeverity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Every record in this package with a mutable collection field defensively copies it — same pattern established in Stage 5. */
class KnowledgeRecordsDefensiveCopyTest {

    @Test
    void nodeAliasesAndMetadataAreDefensivelyCopiedAndImmutable() {

        List<String> aliases = new ArrayList<>(List.of("a"));
        Map<String, String> metadata = new HashMap<>(Map.of("k", "v"));

        Node node = new Node("S1", "key", "Display", "Technical", aliases, NameSource.AUTO_URL, metadata);

        aliases.add("mutated");
        metadata.put("k2", "mutated");

        assertEquals(1, node.aliases().size());
        assertEquals(1, node.metadata().size());
        assertThrows(UnsupportedOperationException.class, () -> node.aliases().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> node.metadata().put("x", "y"));
    }

    @Test
    void flowNodeKeysAreDefensivelyCopiedAndImmutable() {

        List<String> nodeKeys = new ArrayList<>(List.of("a"));
        Flow flow = new Flow("key", "name", nodeKeys, "desc", List.of(), Map.of());

        nodeKeys.add("mutated");

        assertEquals(1, flow.nodeKeys().size());
        assertThrows(UnsupportedOperationException.class, () -> flow.nodeKeys().add("x"));
    }

    @Test
    void journeyDefinitionNodeKeysAreDefensivelyCopiedAndImmutable() {

        List<String> nodeKeys = new ArrayList<>(List.of("a"));
        JourneyDefinition definition = new JourneyDefinition("key", "name", nodeKeys, "desc", List.of(), Map.of());

        nodeKeys.add("mutated");

        assertEquals(1, definition.nodeKeys().size());
        assertThrows(UnsupportedOperationException.class, () -> definition.nodeKeys().add("x"));
    }

    @Test
    void journeyActualSequenceIsDefensivelyCopiedAndImmutable() {

        List<String> sequence = new ArrayList<>(List.of("a"));
        Journey journey = new Journey(sequence, List.of());

        sequence.add("mutated");

        assertEquals(1, journey.actualNodeKeySequence().size());
        assertThrows(UnsupportedOperationException.class, () -> journey.actualNodeKeySequence().add("x"));
    }

    @Test
    void knowledgeConfigListsAreDefensivelyCopiedAndImmutable() {

        List<KnowledgeConfig.NodeConfig> nodes = new ArrayList<>();
        KnowledgeConfig config = new KnowledgeConfig(1, nodes, List.of(), List.of(), null);

        nodes.add(new KnowledgeConfig.NodeConfig("*", "k", "d", "t", List.of(), Map.of()));

        assertEquals(0, config.nodes().size());
        assertThrows(UnsupportedOperationException.class, () -> config.nodes().add(null));
    }

    @Test
    void uxFindingMetadataIsDefensivelyCopiedAndImmutable() {

        Map<String, String> metadata = new HashMap<>(Map.of("visitCount", "2"));
        UxFinding finding = new UxFinding(UxFindingType.BACKTRACKING, FindingSeverity.LOW, "summary", "evidence", metadata);

        metadata.put("mutated", "x");

        assertEquals(1, finding.metadata().size());
        assertThrows(UnsupportedOperationException.class, () -> finding.metadata().put("x", "y"));
    }

    @Test
    void uxFindingNullMetadataNormalizesToEmpty() {

        UxFinding finding = new UxFinding(UxFindingType.BACKTRACKING, FindingSeverity.LOW, "summary", "evidence", null);

        assertEquals(Map.of(), finding.metadata());
    }

    @Test
    void inspectionFindingMetadataIsDefensivelyCopiedAndImmutable() {

        Map<String, String> metadata = new HashMap<>(Map.of("status", "404"));
        InspectionFinding finding = new InspectionFinding(InspectionCheckType.NETWORK_FAILURE, FindingSeverity.MEDIUM, "summary", "evidence", metadata);

        metadata.put("mutated", "x");

        assertEquals(1, finding.metadata().size());
        assertThrows(UnsupportedOperationException.class, () -> finding.metadata().put("x", "y"));
    }

    @Test
    void signalLogListsAreDefensivelyCopiedAndImmutable() {

        List<ConsoleSignal> console = new ArrayList<>();
        SignalLog log = new SignalLog(console, List.of(), List.of());

        console.add(new ConsoleSignal(FindingSeverity.LOW, "x", "", "url", java.time.Instant.now()));

        assertEquals(0, log.console().size());
        assertThrows(UnsupportedOperationException.class, () -> log.console().add(null));
    }

    @Test
    void elementSnapshotHrefsAreDefensivelyCopiedAndImmutable() {

        List<String> hrefs = new ArrayList<>(List.of("https://app/a"));
        ElementSnapshot snapshot = new ElementSnapshot("locator", "name", hrefs, null, null, null, 0, false);

        hrefs.add("mutated");

        assertEquals(1, snapshot.hrefs().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.hrefs().add("x"));
    }

    @Test
    void inspectionConfigNoiseDenyPatternsAreDefensivelyCopiedAndImmutable() {

        List<String> patterns = new ArrayList<>(List.of("known-noise"));
        InspectionConfig inspection = new InspectionConfig(false, false, 4.5, false, patterns);

        patterns.add("mutated");

        assertEquals(1, inspection.noiseDenyPatterns().size());
        assertThrows(UnsupportedOperationException.class, () -> inspection.noiseDenyPatterns().add("x"));
    }

    @Test
    void nullKnowledgeConfigInspectionNormalizesToDisabled() {
        KnowledgeConfig config = new KnowledgeConfig(1, List.of(), List.of(), List.of(), null);
        assertEquals(InspectionConfig.disabled(), config.inspection());
    }

    @Test
    void nullListsAndMapsNormalizeToEmpty() {

        Node node = new Node("S1", "key", "Display", "Technical", null, NameSource.AUTO_URL, null);
        assertEquals(List.of(), node.aliases());
        assertEquals(Map.of(), node.metadata());

        KnowledgeConfig config = new KnowledgeConfig(1, null, null, null, null);
        assertEquals(List.of(), config.nodes());
        assertEquals(List.of(), config.flows());
        assertEquals(List.of(), config.journeys());
        assertEquals(InspectionConfig.disabled(), config.inspection());
    }
}
