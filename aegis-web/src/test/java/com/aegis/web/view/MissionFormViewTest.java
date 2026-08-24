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

    @Test
    void everyDeclaredStrategyIsARealSelectableOption() {

        String html = MissionFormView.render(MissionFormRequest.defaults(), List.of());

        for (String[] entry : HelpText.STRATEGIES) {
            assertTrue(html.contains("value=\"" + entry[0] + "\""),
                    "strategy '" + entry[0] + "' is documented in HelpText.STRATEGIES but not a selectable <option>");
        }
    }

    @Test
    void strategyHelpTextHasAStableIdForClientSideUpdates() {

        String html = MissionFormView.render(MissionFormRequest.defaults(), List.of());

        assertTrue(html.contains("id=\"strategy-help\""));
    }

    @Test
    void strategyChangeScriptCarriesADescriptionForEveryStrategy() {

        String html = MissionFormView.render(MissionFormRequest.defaults(), List.of());

        int scriptStart = html.indexOf("addEventListener('change'");
        assertTrue(scriptStart >= 0, "no client-side change listener found — the help text would never update on selection");

        for (String[] entry : HelpText.STRATEGIES) {
            assertTrue(html.contains("\"" + entry[0] + "\":\""), "no JS description entry for strategy '" + entry[0] + "'");
        }
    }

    @Test
    void strategyDescriptionsInTheScriptAreHtmlEscapedBeforeJsEscaping() {

        // "risk-based"'s and "coverage-aware"'s descriptions contain literal " — " em-dashes and
        // apostrophes elsewhere in HelpText.STRATEGIES ("knowledge-aware": "run's record") — confirms
        // HTML-entity escaping (') runs before the string is embedded as a JS literal, so later
        // assigning it via innerHTML renders correctly instead of breaking the JS syntax.
        String html = MissionFormView.render(MissionFormRequest.defaults(), List.of());

        assertTrue(html.contains("run&#39;s record"));
        assertFalse(html.contains("run's record"));
    }
}
