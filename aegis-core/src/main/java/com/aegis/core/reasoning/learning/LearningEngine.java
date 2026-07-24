package com.aegis.core.reasoning.learning;

import com.aegis.model.context.MissionContext;
import com.aegis.model.experience.Experience;

public interface LearningEngine {

    LearningResult learn(MissionContext missionContext);

    void recordExperience(Experience experience);

}