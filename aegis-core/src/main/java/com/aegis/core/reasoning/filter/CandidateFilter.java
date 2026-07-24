package com.aegis.core.reasoning.filter;

import com.aegis.model.context.MissionContext;
import com.aegis.model.reasoning.CandidateAction;

import java.util.List;

public interface CandidateFilter {

    List<CandidateAction> filter(MissionContext context, List<CandidateAction> candidates);

}
