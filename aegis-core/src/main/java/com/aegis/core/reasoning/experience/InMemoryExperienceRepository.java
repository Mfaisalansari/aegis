package com.aegis.core.reasoning.experience;

import com.aegis.model.experience.Experience;
import com.aegis.model.mission.Mission;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryExperienceRepository implements ExperienceRepository {

    private final List<Experience> experiences = new CopyOnWriteArrayList<>();

    @Override
    public void save(Experience experience) {
        experiences.add(experience);
    }

    @Override
    public List<Experience> findAll() {
        return List.copyOf(experiences);
    }

    @Override
    public List<Experience> findByMission(Mission mission) {
        return experiences.stream()
                .filter(e -> e.missionContext().getMission().equals(mission))
                .toList();
    }
}