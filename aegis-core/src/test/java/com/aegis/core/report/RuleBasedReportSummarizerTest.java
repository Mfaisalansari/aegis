package com.aegis.core.report;

import com.aegis.core.knowledge.KnowledgeBase;
import com.aegis.core.knowledge.KnowledgeBaseBuilder;
import com.aegis.core.knowledge.KnowledgeConfig;
import com.aegis.model.mission.MissionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleBasedReportSummarizerTest {

    @Test
    void alwaysReturnsTheGivenFallbackUnchanged() {

        KnowledgeBase knowledgeBase = KnowledgeBaseBuilder.standard().build("Test", List.of(), List.of(), KnowledgeConfig.empty());

        String result = new RuleBasedReportSummarizer().summarize(
                "Mission", "Goal", MissionStatus.SUCCESS,
                new ExplorationCoverage(0, 0, 0.0), List.of(), Map.of(),
                "recommendation", knowledgeBase, "the fallback text");

        assertEquals("the fallback text", result);
    }
}
