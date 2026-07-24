package com.aegis.core.reasoning.experience;

import com.aegis.model.context.MissionContext;
import com.aegis.model.experience.ExperienceOutcome;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;

import java.time.Duration;

public interface ExperienceRecorder {

    void record(
            MissionContext missionContext,
            Observation observation,
            CandidateAction candidateAction,
            ExperienceOutcome outcome,
            Duration executionDuration
    );
}