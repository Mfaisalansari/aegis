package com.aegis.core.report;

import com.aegis.core.knowledge.InspectionCheckType;
import com.aegis.core.knowledge.UxFindingType;
import com.aegis.model.action.ActionType;
import com.aegis.model.finding.FindingSeverity;
import com.aegis.model.mission.MissionStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One assertion per enum constant — the exhaustive no-default switches in PlainLanguageGlossary already guarantee compile-time coverage; this locks in the actual wording. */
class PlainLanguageGlossaryTest {

    @Test
    void everySeverityHasADistinctLabelAndReason() {
        for (FindingSeverity severity : FindingSeverity.values()) {
            assertDiffersFromRawName(severity.name(), PlainLanguageGlossary.severityLabel(severity));
            assertNonBlank(PlainLanguageGlossary.severityWhyItMatters(severity));
        }
    }

    @Test
    void everyCategoryHasADistinctLabel() {
        for (FindingCategory category : FindingCategory.values()) {
            assertDiffersFromRawName(category.name(), PlainLanguageGlossary.categoryLabel(category));
        }
    }

    @Test
    void everyUxFindingTypeHasADistinctLabelAndExplanation() {
        for (UxFindingType type : UxFindingType.values()) {
            assertDiffersFromRawName(type.name(), PlainLanguageGlossary.uxFindingLabel(type));
            assertNonBlank(PlainLanguageGlossary.uxFindingExplanation(type));
        }
    }

    @Test
    void everyInspectionCheckTypeHasADistinctLabelAndExplanation() {
        for (InspectionCheckType type : InspectionCheckType.values()) {
            assertDiffersFromRawName(type.name(), PlainLanguageGlossary.inspectionCheckLabel(type));
            assertNonBlank(PlainLanguageGlossary.inspectionCheckExplanation(type));
        }
    }

    @Test
    void everyActionTypeHasAVerb() {
        for (ActionType type : ActionType.values()) {
            assertDiffersFromRawName(type.name(), PlainLanguageGlossary.actionVerb(type));
        }
    }

    @Test
    void everyMissionStatusHasADistinctLabel() {
        for (MissionStatus status : MissionStatus.values()) {
            assertDiffersFromRawName(status.name(), PlainLanguageGlossary.missionStatusLabel(status));
        }
    }

    @Test
    void missingAccessibleNameUsesTheSameWordingInBothCatalogs() {
        // Deliberate consistency: the same real-world problem, described
        // identically whether AEGIS detected it via the UX Quality proxy
        // or the higher-fidelity Page Inspection DOM-snapshot check.
        assertEquals(
                PlainLanguageGlossary.uxFindingLabel(UxFindingType.MISSING_ACCESSIBLE_NAME),
                PlainLanguageGlossary.inspectionCheckLabel(InspectionCheckType.MISSING_ACCESSIBLE_NAME));
    }

    private void assertDiffersFromRawName(String rawName, String translated) {
        assertNonBlank(translated);
        assertFalse(translated.equals(rawName), "translation should differ from the raw enum name: " + rawName);
    }

    private void assertNonBlank(String value) {
        assertNotNull(value);
        assertTrue(!value.isBlank());
    }
}
