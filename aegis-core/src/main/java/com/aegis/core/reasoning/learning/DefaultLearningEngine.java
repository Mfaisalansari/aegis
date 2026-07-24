package com.aegis.core.reasoning.learning;

import com.aegis.core.reasoning.experience.ExperienceRepository;
import com.aegis.model.action.Action;
import com.aegis.model.context.MissionContext;
import com.aegis.model.experience.Experience;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DefaultLearningEngine implements LearningEngine {

    private final ExperienceRepository repository;
    private final PatternAnalyzer patternAnalyzer;

    public DefaultLearningEngine(
            ExperienceRepository repository,
            PatternAnalyzer patternAnalyzer) {

        this.repository = Objects.requireNonNull(repository);
        this.patternAnalyzer = Objects.requireNonNull(patternAnalyzer);
    }

    @Override
    public LearningResult learn(MissionContext missionContext) {

        List<Experience> experiences =
                repository.findByMission(missionContext.getMission());

        if (experiences.isEmpty()) {
            return LearningResult.empty();
        }

        Map<Action, PatternStatistics> statistics =
                patternAnalyzer.analyze(experiences);

        Map<Action, Double> adjustments = new HashMap<>();

        for (PatternStatistics stat : statistics.values()) {

            adjustments.put(
                    stat.action(),
                    calculateAdjustment(stat.successRate())
            );
        }

        return new LearningResult(Map.copyOf(adjustments));
    }

    @Override
    public void recordExperience(Experience experience) {

        repository.save(experience);

    }

    private double calculateAdjustment(double successRate) {

        if (successRate >= 0.90) {
            return 0.20;
        }

        if (successRate >= 0.75) {
            return 0.10;
        }

        if (successRate >= 0.50) {
            return 0.00;
        }

        if (successRate >= 0.25) {
            return -0.10;
        }

        return -0.20;
    }
}