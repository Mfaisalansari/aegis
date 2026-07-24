package com.aegis.core.reasoning.generator;

import com.aegis.core.reasoning.confidence.CandidateConfidenceEstimator;
import com.aegis.core.reasoning.confidence.ConfidenceEstimate;
import com.aegis.core.reasoning.factory.ActionFactory;
import com.aegis.core.reasoning.mapper.ElementActionMapper;
import com.aegis.core.reasoning.value.InputValueResolverRegistry;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;

import java.util.ArrayList;
import java.util.List;

public class GenericCandidateActionGenerator implements CandidateActionGenerator {

    private final ElementActionMapper mapper;
    private final ActionFactory factory;
    private final InputValueResolverRegistry valueResolverRegistry;
    private final CandidateConfidenceEstimator confidenceEstimator;

    public GenericCandidateActionGenerator(
            ElementActionMapper mapper,
            ActionFactory factory,
            InputValueResolverRegistry valueResolverRegistry,
            CandidateConfidenceEstimator confidenceEstimator) {

        this.mapper = mapper;
        this.factory = factory;
        this.valueResolverRegistry = valueResolverRegistry;
        this.confidenceEstimator = confidenceEstimator;
    }

    @Override
    public List<CandidateAction> generate(MissionContext context) {

        Observation observation =
                context.getExecutionState().getCurrentObservation();

        List<CandidateAction> candidates = new ArrayList<>();

        if (observation == null || observation.elements() == null) {
            return candidates;
        }

        for (ElementInfo element : observation.elements()) {

            List<ActionType> supportedActions =
                    mapper.supportedActions(element);

            for (ActionType actionType : supportedActions) {

                candidates.add(
                        buildCandidate(
                                context,
                                element,
                                actionType
                        )
                );
            }
        }

        return candidates;
    }

    private CandidateAction buildCandidate(
            MissionContext context,
            ElementInfo element,
            ActionType actionType) {

        String value = "";

        if (actionType == ActionType.TYPE) {
            value = valueResolverRegistry.select(context).resolve(context, element);
        }

        ConfidenceEstimate estimate =
                confidenceEstimator.estimate(context, element, actionType, value);

        Action action = factory.create(
                actionType,
                element,
                value,
                estimate.reason(),
                estimate.confidence()
        );

        return new CandidateAction(
                action,
                estimate.confidence(),
                estimate.reason()
        );
    }
}