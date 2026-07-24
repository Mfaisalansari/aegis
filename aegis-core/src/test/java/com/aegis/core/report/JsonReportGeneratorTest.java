package com.aegis.core.report;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.finding.Finding;
import com.aegis.model.finding.FindingSeverity;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionStatus;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonReportGeneratorTest {

    private final JsonReportGenerator generator = new JsonReportGenerator();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void producesValidParseableJson() throws Exception {

        MissionContext context = new MissionContext(mission());

        String json = generator.generate(context, MissionStatus.SUCCESS);

        JsonNode root = mapper.readTree(json);

        assertEquals("Test", root.path("mission").path("name").asText());
        assertEquals("SUCCESS", root.path("mission").path("status").asText());
    }

    @Test
    void includesCoverageAndPages() throws Exception {

        MissionContext context = new MissionContext(mission());

        context.getExecutionState().setCurrentObservation(observation("https://example.com", input("#a")));
        context.getExecutionState().addAction(type("#a"));

        String json = generator.generate(context, MissionStatus.SUCCESS);
        JsonNode root = mapper.readTree(json);

        assertEquals(1, root.path("coverage").path("elementsDiscovered").asInt());
        assertEquals(1, root.path("coverage").path("elementsInteracted").asInt());
        assertTrue(root.path("coverage").path("pages").isArray());
        assertEquals(1, root.path("coverage").path("pages").size());
        assertEquals("https://example.com", root.path("coverage").path("pages").get(0).path("url").asText());
    }

    @Test
    void includesTimelineEvents() throws Exception {

        MissionContext context = new MissionContext(mission());

        String json = generator.generate(context, MissionStatus.SUCCESS);
        JsonNode root = mapper.readTree(json);

        JsonNode timeline = root.path("timeline");

        assertTrue(timeline.isArray());
        assertTrue(timeline.size() >= 2);
        assertEquals("MISSION_STARTED", timeline.get(0).path("kind").asText());
        assertTrue(timeline.get(0).path("screenshotDataUri").isNull());
    }

    @Test
    void includesBugClustersAndFindingsByCategory() throws Exception {

        MissionContext context = new MissionContext(mission());

        context.getExecutionState().addFinding(
                new Finding(FindingSeverity.CRITICAL, "CRASH: tab crashed", "https://example.com", Instant.now()));

        String json = generator.generate(context, MissionStatus.FAILED);
        JsonNode root = mapper.readTree(json);

        assertEquals(1, root.path("bugClusters").size());
        assertEquals("CRITICAL", root.path("bugClusters").get(0).path("severity").asText());
        assertFalse(root.path("bugClusters").get(0).path("explanation").asText().isBlank());

        assertTrue(root.path("findingsByCategory").has("STABILITY"));
        assertEquals(1, root.path("findings").size());
    }

    @Test
    void includesLearningSummary() throws Exception {

        MissionContext context = new MissionContext(mission());

        String json = generator.generate(context, MissionStatus.SUCCESS);
        JsonNode root = mapper.readTree(json);

        JsonNode learning = root.path("learning");

        assertEquals(0, learning.path("newExperiences").asInt());
        assertTrue(learning.path("actionPerformance").isArray());
    }

    @Test
    void includesRecommendationAndPlan() throws Exception {

        MissionContext context = new MissionContext(mission());

        String json = generator.generate(context, MissionStatus.SUCCESS);
        JsonNode root = mapper.readTree(json);

        assertFalse(root.path("recommendation").asText().isBlank());
        assertTrue(root.path("plan").isArray());
    }

    private Mission mission() {
        return new Mission(UUID.randomUUID(), "Test", "Test", Map.of());
    }

    private Observation observation(String url, ElementInfo... elements) {
        return new Observation(url, "Title", List.of(elements), List.of(), List.of(elements), List.of(), List.of(), Instant.now());
    }

    private ElementInfo input(String locator) {
        return new ElementInfo("input", "", "", "", "text", "", true, true, locator);
    }

    private Action type(String target) {
        return new Action(
                UUID.randomUUID(), ActionType.TYPE, target, "value", "test",
                1.0, "test", Duration.ofSeconds(5), Instant.now(), "input"
        );
    }
}
