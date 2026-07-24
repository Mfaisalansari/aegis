package com.aegis.core.reasoning.generator;

import com.aegis.model.context.MissionContext;
import com.aegis.model.reasoning.CandidateAction;

import java.util.List;

public interface CandidateActionGenerator {

    List<CandidateAction> generate(MissionContext context);

}