package com.aegis.core.reasoning.generator;

import com.aegis.model.context.MissionContext;
import com.aegis.model.reasoning.CandidateAction;

import java.util.ArrayList;
import java.util.List;

/**
 * Combines candidates from multiple sources — element-scoped candidates
 * from GenericCandidateActionGenerator plus page-level ones (refresh,
 * back) that aren't tied to any element — into one list for the reasoner.
 * Mirrors CompositeCandidateFilter's shape for the same kind of reason:
 * one place that knows how to combine, so the reasoner doesn't need to.
 */
public class CompositeCandidateActionGenerator implements CandidateActionGenerator {

    private final List<CandidateActionGenerator> generators;

    public CompositeCandidateActionGenerator(List<CandidateActionGenerator> generators) {
        this.generators = generators;
    }

    @Override
    public List<CandidateAction> generate(MissionContext context) {

        List<CandidateAction> combined = new ArrayList<>();

        for (CandidateActionGenerator generator : generators) {
            combined.addAll(generator.generate(context));
        }

        return combined;
    }
}
