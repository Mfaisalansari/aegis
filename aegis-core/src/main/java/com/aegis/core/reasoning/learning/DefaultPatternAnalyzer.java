package com.aegis.core.reasoning.learning;

import com.aegis.model.action.Action;
import com.aegis.model.experience.Experience;
import com.aegis.model.experience.ExperienceOutcome;

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

        // Grouped by ActionKey (type + target), not the raw Action record:
        // every Action carries a fresh random id/createdAt each time a
        // candidate is regenerated, so grouping by the record itself would
        // put every experience in its own singleton group and the "pattern"
        // in PatternAnalyzer would never see more than one data point.
        Map<String, List<Experience>> groupedExperiences =
                experiences.stream()
                        .collect(Collectors.groupingBy(
                                experience -> ActionKey.of(experience.candidateAction().action())
                        ));

        Map<Action, PatternStatistics> statistics = new HashMap<>();

        for (List<Experience> actionExperiences : groupedExperiences.values()) {

            // Representative Action for this group, purely so callers still
            // have a real Action to look at — the group's identity is the
            // ActionKey above, not this specific instance.
            Action representativeAction = actionExperiences.get(0).candidateAction().action();

            long total = actionExperiences.size();

            long successful = actionExperiences.stream()
                    .filter(e -> e.outcome() == ExperienceOutcome.SUCCESS)
                    .count();

            long failed = total - successful;

            double successRate =
                    total == 0 ? 0.0 : (double) successful / total;

            statistics.put(
                    representativeAction,
                    new PatternStatistics(
                            representativeAction,
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
