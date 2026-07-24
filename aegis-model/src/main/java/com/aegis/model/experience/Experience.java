package com.aegis.model.experience;

import com.aegis.model.context.MissionContext;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record Experience(

        UUID id,

        MissionContext missionContext,

        Observation observation,

        CandidateAction candidateAction,

        ExperienceOutcome outcome,

        Duration executionDuration,

        Instant createdAt

) {

    public static Experience create(

            MissionContext missionContext,

            Observation observation,

            CandidateAction candidateAction,

            ExperienceOutcome outcome,

            Duration executionDuration

    ) {

        return new Experience(

                UUID.randomUUID(),

                missionContext,

                observation,

                candidateAction,

                outcome,

                executionDuration,

                Instant.now()

        );

    }

}