package com.aegis.web.view;

import com.aegis.web.form.MissionFormRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionFormViewTest {

    @Test
    void rendersTheDefaultFormWithNoErrors() {

        String html = MissionFormView.render(MissionFormRequest.defaults(), List.of());

        assertTrue(html.contains("Start run"));
        assertFalse(html.contains("Please fix the following"));
    }

    @Test
    void stickyReRenderEscapesAScriptTagInAUserSuppliedValue() {

        MissionFormRequest form = MissionFormRequest.fromSubmittedValues(
                java.util.Map.of("baseUrl", "<script>alert(1)</script>"));

        String html = MissionFormView.render(form, List.of("Some error"));

        assertFalse(html.contains("<script>alert(1)</script>"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
    }

    @Test
    void invalidSubmissionShowsTheErrorMessage() {

        String html = MissionFormView.render(MissionFormRequest.defaults(), List.of("Target Base URL is required."));

        assertTrue(html.contains("Target Base URL is required."));
    }

    @Test
    void noticeBannerRendersWhenPresentAndNotWhenAbsent() {

        String withNotice = MissionFormView.render(MissionFormRequest.defaults(), List.of(), "Parsed from your description.");
        String without = MissionFormView.render(MissionFormRequest.defaults(), List.of());

        assertTrue(withNotice.contains("Parsed from your description."));
        assertFalse(without.contains("class=\"notice\""));
    }

    @Test
    void naturalLanguagePanelIsClosedWhenNoInstructionAndOpenAfterAParse() {

        String blank = MissionFormView.render(MissionFormRequest.defaults(), List.of());
        assertFalse(blank.contains("more-settings open") || blank.contains("open><summary>Describe"));

        MissionFormRequest parsed = MissionFormRequest.fromParsedMission(
                new com.aegis.model.mission.Mission(java.util.UUID.randomUUID(), "n", "d",
                        java.util.Map.of("baseUrl", "https://example.com/")),
                "Log into https://example.com/");

        String afterParse = MissionFormView.render(parsed, List.of(), "Parsed from your description.");
        assertTrue(afterParse.contains("Log into https://example.com/"));
    }

    @Test
    void theNaturalLanguageFormAndTheStructuredFormAreSiblingsNotNested() {

        String html = MissionFormView.render(MissionFormRequest.defaults(), List.of());

        int nlFormOpen = html.indexOf("action=\"/run/parse\"");
        int nlFormClose = html.indexOf("</form>", nlFormOpen);
        int structuredFormOpen = html.indexOf("action=\"/run\"");

        assertTrue(nlFormOpen >= 0 && nlFormClose >= 0 && structuredFormOpen >= 0);
        assertTrue(nlFormClose < structuredFormOpen, "the NL form must close before the structured form opens");
    }
}
