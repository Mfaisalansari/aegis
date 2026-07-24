package com.aegis.core.reasoning.filter;

import com.aegis.model.context.MissionContext;
import com.aegis.model.reasoning.CandidateAction;

import java.util.List;

/**
 * Applies a chain of filters in order. If the chain would eliminate every
 * candidate, the original unfiltered list is returned instead, so the
 * scorer always has something to choose from.
 */
public class CompositeCandidateFilter implements CandidateFilter {

    private final List<CandidateFilter> filters;

    public CompositeCandidateFilter(List<CandidateFilter> filters) {
        this.filters = filters;
    }

    @Override
    public List<CandidateAction> filter(MissionContext context, List<CandidateAction> candidates) {

        List<CandidateAction> result = candidates;

        for (CandidateFilter filter : filters) {
            result = filter.filter(context, result);
        }

        if (result.isEmpty()) {
            return candidates;
        }

        return result;
    }
}
