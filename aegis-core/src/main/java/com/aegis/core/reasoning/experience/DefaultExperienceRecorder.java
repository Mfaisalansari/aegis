package com.aegis.core.reasoning.experience;

import com.aegis.model.context.MissionContext;
import com.aegis.model.experience.Experience;
import com.aegis.model.experience.ExperienceOutcome;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;

import java.time.Duration;
import java.util.Objects;

public class DefaultExperienceRecorder implements ExperienceRecorder {

    private final ExperienceRepository repository;

    public DefaultExperienceRecorder(ExperienceRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public void record(
            MissionContext missionContext,
            Observation observation,
            CandidateAction candidateAction,
            ExperienceOutcome outcome,
            Duration executionDuration) {

        Experience experience = Experience.create(
                missionContext,
                observation,
                candidateAction,
                outcome,
                executionDuration
        );

        repository.save(experience);
    }
}