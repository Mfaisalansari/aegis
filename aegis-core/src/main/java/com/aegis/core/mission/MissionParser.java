package com.aegis.core.mission;

import com.aegis.model.mission.Mission;

/**
 * Phase 8 "Natural language missions": translates a free-text QA
 * instruction into a structured Mission. Always produces a Mission —
 * never throws, never returns null — even when it can't extract much
 * from the text; see RuleBasedMissionParser for what "not much" means.
 */
public interface MissionParser {

    Mission parse(String naturalLanguageDescription);

}
