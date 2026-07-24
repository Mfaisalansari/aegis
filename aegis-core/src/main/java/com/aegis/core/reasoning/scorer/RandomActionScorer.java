package com.aegis.core.reasoning.scorer;

import com.aegis.model.context.MissionContext;
import com.aegis.model.reasoning.CandidateAction;

import java.util.List;
import java.util.Random;

/**
 * Random Exploration: ignores confidence and picks a candidate at random,
 * to shake loose paths a greedy strategy would never try.
 */
public class RandomActionScorer implements ActionScorer {

    private final Random random;

    public RandomActionScorer() {
        this(new Random());
    }

    public RandomActionScorer(Random random) {
        this.random = random;
    }

    @Override
    public CandidateAction choose(MissionContext context, List<CandidateAction> candidates) {

        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("No candidate actions available.");
        }

        return candidates.get(random.nextInt(candidates.size()));
    }
}
