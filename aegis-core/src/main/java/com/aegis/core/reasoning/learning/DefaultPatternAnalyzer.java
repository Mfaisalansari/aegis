package com.aegis.core.reasoning.learning;

import com.aegis.model.action.Action;
import com.aegis.model.experience.Experience;
import com.aegis.model.experience.ExperienceOutcome;
import com.aegis.model.reasoning.CandidateAction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultPatternAnalyzer implements PatternAnalyzer {

    @Override
    public Map<Action, PatternStatistics> analyze(List<Experience> experiences) {

        if (experiences == null || experiences.isEmpty()) {
            return Map.of();
        }

        Map<Action, List<Experience>> groupedExperiences =
                experiences.stream()
                        .collect(Collectors.groupingBy(
                                experience -> experience.candidateAction().action()
                        ));

        Map<Action, PatternStatistics> statistics = new HashMap<>();

        for (Map.Entry<Action, List<Experience>> entry : groupedExperiences.entrySet()) {

            Action action = entry.getKey();
            List<Experience> actionExperiences = entry.getValue();

            long total = actionExperiences.size();

            long successful = actionExperiences.stream()
                    .filter(e -> e.outcome() == ExperienceOutcome.SUCCESS)
                    .count();

            long failed = total - successful;

            double successRate =
                    total == 0 ? 0.0 : (double) successful / total;

            statistics.put(
                    action,
                    new PatternStatistics(
                            action,
                            total,
                            successful,
                            failed,
                            successRate
                    )
            );
        }

        return Map.copyOf(statistics);
    }
}