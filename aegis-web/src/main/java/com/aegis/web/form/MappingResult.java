package com.aegis.web.form;

import com.aegis.core.browser.BrowserConfig;
import com.aegis.core.knowledge.KnowledgeConfig;
import com.aegis.model.mission.Mission;

import java.util.List;

/**
 * What {@link MissionRequestMapper#map(MissionFormRequest)} produces.
 * When {@link #errors()} is non-empty, {@code mission()}/{@code
 * browserConfig()}/{@code knowledgeConfig()} are {@code null} — callers
 * must check {@link #isValid()} first.
 */
public record MappingResult(
        Mission mission, BrowserConfig browserConfig, KnowledgeConfig knowledgeConfig,
        String reportDirectory, List<String> errors) {

    public MappingResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    static MappingResult invalid(List<String> errors) {
        return new MappingResult(null, null, null, null, errors);
    }

    static MappingResult valid(Mission mission, BrowserConfig browserConfig, KnowledgeConfig knowledgeConfig, String reportDirectory) {
        return new MappingResult(mission, browserConfig, knowledgeConfig, reportDirectory, List.of());
    }
}
