package com.aegis.core.reasoning.experience;

import com.aegis.model.experience.Experience;
import com.aegis.model.mission.Mission;

import java.util.List;

/**
 * Persists action outcomes across separate {@code Aegis.run(...)} calls for
 * the same mission configuration — the one thing {@link ExperienceRepository}
 * deliberately doesn't do (it's in-memory and scoped to a single run). See
 * {@link FileExperienceStore} for the real, file-based implementation and
 * how "same configuration" is defined despite {@link Mission#id()} being a
 * fresh random UUID every submission.
 *
 * {@code EngineFactory.create(...)} seeds a fresh {@link ExperienceRepository}
 * from {@link #loadPriorExperiences} before a mission starts, so {@code
 * HeuristicCandidateConfidenceEstimator}'s scoring adjustments reflect real
 * cross-run history from the very first candidate — not just what happened
 * earlier in the same run. {@code Aegis.run} calls {@link #recordOutcome}
 * after a mission finishes, with only the experiences genuinely recorded
 * during that run (never the ones this same call seeded back in).
 */
public interface ExperienceStore {

    /** Used by every {@code Aegis.run}/{@code EngineFactory.create} overload that doesn't take an explicit store — identical to today's behavior. */
    ExperienceStore NO_OP = new ExperienceStore() {

        @Override
        public List<Experience> loadPriorExperiences(Mission mission) {
            return List.of();
        }

        @Override
        public void recordOutcome(Mission mission, List<Experience> newExperiencesThisRun) {
        }
    };

    /** Prior runs' outcomes for this mission's configuration, reconstructed as synthetic {@link Experience}s wrapping this exact {@code mission} — empty if none exist yet. */
    List<Experience> loadPriorExperiences(Mission mission);

    /** Merges this run's own newly-recorded experiences into whatever history already exists for this mission's configuration. */
    void recordOutcome(Mission mission, List<Experience> newExperiencesThisRun);
}
