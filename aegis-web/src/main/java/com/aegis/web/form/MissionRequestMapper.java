package com.aegis.web.form;

import com.aegis.api.AegisConfigException;
import com.aegis.api.KnowledgeConfigLoader;
import com.aegis.api.MissionBuilder;
import com.aegis.core.browser.BrowserConfig;
import com.aegis.core.knowledge.InspectionConfig;
import com.aegis.core.knowledge.KnowledgeConfig;
import com.aegis.model.mission.Mission;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Converts a submitted {@link MissionFormRequest} into the real domain
 * objects {@code Aegis.run(...)} needs, collecting every problem as a
 * plain-English error message instead of letting an
 * {@code IllegalArgumentException} (thrown deep inside
 * {@code ActionScorerRegistry}/{@code InputValueResolverRegistry}) or a
 * {@code NumberFormatException} surface as an opaque background-thread
 * failure. Every parse below is validate-then-build: nothing partially
 * mutates state before all fields are checked.
 */
public final class MissionRequestMapper {

    private MissionRequestMapper() {
    }

    public static MappingResult map(MissionFormRequest request) {

        List<String> errors = new ArrayList<>();

        if (request.baseUrl() == null || request.baseUrl().isBlank()) {
            errors.add("Target Base URL is required.");
        }

        Integer maxIterations = parseMaxIterations(request.maxIterations(), errors);
        String strategy = validatedStrategy(request.strategy(), errors);
        String inputStrategy = validatedInputStrategy(request.inputStrategy(), errors);
        String browserType = validatedBrowserType(request.browserType(), errors);
        double contrastThreshold = parseContrastThreshold(request.contrastThreshold(), errors);
        List<String> noiseDenyPatterns = validatedNoisePatterns(request.noiseDenyPatterns(), errors);
        KnowledgeConfig pastedKnowledge = parsePastedKnowledgeYaml(request.knowledgeYaml(), errors);

        if (!errors.isEmpty()) {
            return MappingResult.invalid(errors);
        }

        MissionBuilder builder = MissionBuilder.create(
                        blankToDefault(request.name(), "AEGIS Mission"),
                        blankToDefault(request.description(), "Autonomous exploration"))
                .baseUrl(request.baseUrl())
                .credentials(nullIfBlank(request.username()), nullIfBlank(request.password()))
                .successWhenUrlContains(nullIfBlank(request.successUrlContains()))
                .interruptions(request.interruptions())
                .doubleClicks(request.doubleClicks())
                .raceConditions(request.raceConditions());

        if (strategy != null) {
            builder.strategy(strategy);
        }
        if (inputStrategy != null) {
            builder.inputStrategy(inputStrategy);
        }
        if (maxIterations != null) {
            builder.maxIterations(maxIterations);
        }

        Mission mission = builder.build();

        BrowserConfig browserConfig = new BrowserConfig(browserType, request.headless());

        InspectionConfig inspectionConfig = new InspectionConfig(
                request.captureDom(), request.consoleWarnings(), contrastThreshold,
                request.probeLinks(), noiseDenyPatterns);

        KnowledgeConfig knowledgeConfig = pastedKnowledge == null
                ? new KnowledgeConfig(1, List.of(), List.of(), List.of(), inspectionConfig)
                : new KnowledgeConfig(1, pastedKnowledge.nodes(), pastedKnowledge.flows(), pastedKnowledge.journeys(), inspectionConfig);

        String reportDirectory = blankToDefault(request.reportDirectory(), "reports");

        return MappingResult.valid(mission, browserConfig, knowledgeConfig, reportDirectory);
    }

    private static Integer parseMaxIterations(String raw, List<String> errors) {

        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            errors.add("Max Iterations must be a whole number.");
            return null;
        }
    }

    private static String validatedStrategy(String raw, List<String> errors) {

        if (raw == null || raw.isBlank()) {
            return null;
        }

        String trimmed = raw.trim();

        if (!KnownValues.isStrategy(trimmed)) {
            errors.add("Unknown exploration strategy: \"" + trimmed + "\".");
            return null;
        }

        return trimmed;
    }

    private static String validatedInputStrategy(String raw, List<String> errors) {

        if (raw == null || raw.isBlank()) {
            return null;
        }

        String trimmed = raw.trim();

        if (!KnownValues.isInputStrategy(trimmed)) {
            errors.add("Unknown input strategy: \"" + trimmed + "\".");
            return null;
        }

        return trimmed;
    }

    private static String validatedBrowserType(String raw, List<String> errors) {

        if (raw == null || raw.isBlank()) {
            return "chromium";
        }

        String trimmed = raw.trim();

        if (!KnownValues.isBrowserType(trimmed)) {
            errors.add("Unknown browser type: \"" + trimmed + "\".");
            return trimmed;
        }

        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static double parseContrastThreshold(String raw, List<String> errors) {

        if (raw == null || raw.isBlank()) {
            return 4.5;
        }

        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            errors.add("Contrast Threshold must be a number.");
            return 4.5;
        }
    }

    private static List<String> validatedNoisePatterns(String raw, List<String> errors) {

        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        List<String> patterns = new ArrayList<>();

        for (String line : raw.split("\\R")) {

            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            try {
                Pattern.compile(trimmed);
                patterns.add(trimmed);
            } catch (PatternSyntaxException e) {
                errors.add("Invalid regex in Noise Deny Patterns: \"" + trimmed + "\".");
            }
        }

        return patterns;
    }

    private static KnowledgeConfig parsePastedKnowledgeYaml(String raw, List<String> errors) {

        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return KnowledgeConfigLoader.load(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (AegisConfigException e) {
            errors.add("Knowledge YAML: " + e.getMessage());
            return null;
        }
    }

    private static String blankToDefault(String raw, String fallback) {
        return raw == null || raw.isBlank() ? fallback : raw;
    }

    private static String nullIfBlank(String raw) {
        return raw == null || raw.isBlank() ? null : raw;
    }
}
