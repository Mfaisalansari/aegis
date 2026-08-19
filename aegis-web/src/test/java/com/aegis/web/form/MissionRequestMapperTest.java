package com.aegis.web.form;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionRequestMapperTest {

    @Test
    void blankBaseUrlProducesExactlyOneErrorMentioningBaseUrl() {

        Map<String, String> values = validValues();
        values.put("baseUrl", "");

        MappingResult result = MissionRequestMapper.map(MissionFormRequest.fromSubmittedValues(values));

        assertFalse(result.isValid());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("Base URL"));
    }

    @Test
    void nonNumericMaxIterationsIsAnError() {

        Map<String, String> values = validValues();
        values.put("maxIterations", "abc");

        MappingResult result = MissionRequestMapper.map(MissionFormRequest.fromSubmittedValues(values));

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Max Iterations")));
    }

    @Test
    void validMaxIterationsIsPassedThroughAsAMissionParameter() {

        Map<String, String> values = validValues();
        values.put("maxIterations", "42");

        MappingResult result = MissionRequestMapper.map(MissionFormRequest.fromSubmittedValues(values));

        assertTrue(result.isValid());
        assertEquals("42", result.mission().parameter("maxIterations"));
    }

    @Test
    void blankMaxIterationsOmitsTheParameterEntirely() {

        Map<String, String> values = validValues();
        values.put("maxIterations", "");

        MappingResult result = MissionRequestMapper.map(MissionFormRequest.fromSubmittedValues(values));

        assertTrue(result.isValid());
        assertNull(result.mission().parameter("maxIterations"));
    }

    @Test
    void validStrategyEndsUpOnTheExplorationStrategyParameter() {

        Map<String, String> values = validValues();
        values.put("strategy", "coverage-aware");

        MappingResult result = MissionRequestMapper.map(MissionFormRequest.fromSubmittedValues(values));

        assertTrue(result.isValid());
        assertEquals("coverage-aware", result.mission().parameter("explorationStrategy"));
    }

    @Test
    void unknownStrategyIsAnErrorNotAnException() {

        Map<String, String> values = validValues();
        values.put("strategy", "not-a-real-one");

        MappingResult result = MissionRequestMapper.map(MissionFormRequest.fromSubmittedValues(values));

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("not-a-real-one")));
    }

    @Test
    void blankStrategyOmitsTheParameter() {

        Map<String, String> values = validValues();
        values.put("strategy", "");

        MappingResult result = MissionRequestMapper.map(MissionFormRequest.fromSubmittedValues(values));

        assertTrue(result.isValid());
        assertNull(result.mission().parameter("explorationStrategy"));
    }

    @Test
    void validInputStrategyIsAccepted() {

        Map<String, String> values = validValues();
        values.put("inputStrategy", "edge-case");

        MappingResult result = MissionRequestMapper.map(MissionFormRequest.fromSubmittedValues(values));

        assertTrue(result.isValid());
        assertEquals("edge-case", result.mission().parameter("inputStrategy"));
    }

    @Test
    void unknownInputStrategyIsAnError() {

        Map<String, String> values = validValues();
        values.put("inputStrategy", "bogus");

        MappingResult result = MissionRequestMapper.map(MissionFormRequest.fromSubmittedValues(values));

        assertFalse(result.isValid());
    }

    @Test
    void blankInputStrategyOmitsTheParameter() {

        Map<String, String> values = validValues();
        values.put("inputStrategy", "");

        MappingResult result = MissionRequestMapper.map(MissionFormRequest.fromSubmittedValues(values));

        assertTrue(result.isValid());
        assertNull(result.mission().parameter("inputStrategy"));
    }

    @Test
    void browserTypeIsAcceptedCaseInsensitivelyAndNormalized() {

        Map<String, String> values = validValues();
        values.put("browserType", "FIREFOX");

        MappingResult result = MissionRequestMapper.map(MissionFormRequest.fromSubmittedValues(values));

        assertTrue(result.isValid());
        assertEquals("firefox", result.browserConfig().type());
    }

    @Test
    void unknownBrowserTypeIsAnError() {

        Map<String, String> values = validValues();
        values.put("browserType", "ie6");

        MappingResult result = MissionRequestMapper.map(MissionFormRequest.fromSubmittedValues(values));

        assertFalse(result.isValid());
    }

    @Test
    void nonNumericContrastThresholdIsAnError() {

        Map<String, String> values = validValues();
        values.put("contrastThreshold", "not-a-number");

        MappingResult result = MissionRequestMapper.map(MissionFormRequest.fromSubmittedValues(values));

        assertFalse(result.isValid());
    }

    @Test
    void blankContrastThresholdDefaultsTo4_5() {

        Map<String, String> values = validValues();
        values.put("contrastThreshold", "");

        MappingResult result = MissionRequestMapper.map(MissionFormRequest.fromSubmittedValues(values));

        assertTrue(result.isValid());
        assertEquals(4.5, result.knowledgeConfig().inspection().contrastThreshold());
    }

    @Test
    void invalidRegexLineProducesAnErrorButValidLinesStillParse() {

        Map<String, String> values = validValues();
        values.put("noiseDenyPatterns", "valid-.*-pattern\n(unbalanced");

        MappingResult result = MissionRequestMapper.map(MissionFormRequest.fromSubmittedValues(values));

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("(unbalanced")));
    }

    @Test
    void validKnowledgeYamlPopulatesNodes() {

        Map<String, String> values = validValues();
        values.put("knowledgeYaml", """
                version: 1
                nodes:
                  - urlPattern: "*/login*"
                    key: login
                    displayName: "Login"
                """);

        MappingResult result = MissionRequestMapper.map(MissionFormRequest.fromSubmittedValues(values));

        assertTrue(result.isValid());
        assertEquals(1, result.knowledgeConfig().nodes().size());
        assertEquals("login", result.knowledgeConfig().nodes().get(0).key());
    }

    @Test
    void malformedKnowledgeYamlIsAnErrorWrappingTheLoaderMessage() {

        Map<String, String> values = validValues();
        values.put("knowledgeYaml", "not: [valid, yaml: structure");

        MappingResult result = MissionRequestMapper.map(MissionFormRequest.fromSubmittedValues(values));

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.startsWith("Knowledge YAML:")));
    }

    @Test
    void structuredInspectionFieldsWinOverInspectionBlockInPastedYaml() {

        Map<String, String> values = validValues();
        values.put("captureDom", "on");
        values.put("knowledgeYaml", """
                version: 1
                inspection:
                  captureDom: false
                """);

        MappingResult result = MissionRequestMapper.map(MissionFormRequest.fromSubmittedValues(values));

        assertTrue(result.isValid());
        assertTrue(result.knowledgeConfig().inspection().captureDom());
    }

    @Test
    void fullyValidRequestBuildsAConsistentMissionAndBrowserConfig() {

        MappingResult result = MissionRequestMapper.map(MissionFormRequest.fromSubmittedValues(validValues()));

        assertTrue(result.isValid());
        assertEquals("https://example.com", result.mission().parameter("baseUrl"));
        assertEquals("user", result.mission().parameter("username"));
        assertEquals("pass", result.mission().parameter("password"));
        assertEquals("done", result.mission().parameter("successUrlContains"));
        assertEquals("chromium", result.browserConfig().type());
        assertFalse(result.browserConfig().headless());
        assertEquals("reports", result.reportDirectory());
    }

    private static Map<String, String> validValues() {

        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", "Test Mission");
        values.put("description", "A test");
        values.put("baseUrl", "https://example.com");
        values.put("username", "user");
        values.put("password", "pass");
        values.put("successUrlContains", "done");
        values.put("browserType", "chromium");
        values.put("strategy", "greedy");
        values.put("maxIterations", "10");
        values.put("inputStrategy", "realistic");
        values.put("reportDirectory", "reports");
        values.put("contrastThreshold", "4.5");
        values.put("noiseDenyPatterns", "");
        values.put("knowledgeYaml", "");

        return values;
    }
}
