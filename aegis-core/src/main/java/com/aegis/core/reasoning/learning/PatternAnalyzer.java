package com.aegis.core.reasoning.learning;

import com.aegis.model.action.Action;
import com.aegis.model.experience.Experience;

import java.util.List;
import java.util.Map;

public interface PatternAnalyzer {

    Map<Action, PatternStatistics> analyze(List<Experience> experiences);

}