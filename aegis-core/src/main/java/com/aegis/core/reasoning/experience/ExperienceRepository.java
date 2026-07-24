package com.aegis.core.reasoning.experience;

import com.aegis.model.experience.Experience;
import com.aegis.model.mission.Mission;

import java.util.List;

public interface ExperienceRepository {

    void save(Experience experience);

    List<Experience> findAll();

    List<Experience> findByMission(Mission mission);

}