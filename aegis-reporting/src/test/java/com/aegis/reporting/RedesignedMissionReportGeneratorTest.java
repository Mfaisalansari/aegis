package com.aegis.reporting;

import com.aegis.core.bug.BugExplainer;
import com.aegis.core.bug.RecommendationEngine;
import com.aegis.core.mission.MissionPlan;
import com.aegis.core.report.MissionReportData;
import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.experience.Experience;
import com.aegis.model.experience.ExperienceOutcome;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionStatus;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;
import com.aegis.model.reasoning.ReasoningStep;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Same fixture-construction style as {@code HtmlExplainabilityReportGeneratorTest}
 * (the frozen generator's own test) — this class produces an equivalent
 * report from the same {@link MissionReportData}, so it's tested the same
 * way. The one test with no equivalent in the frozen suite is {@link
 * #graphSpacingWidensToFitALongLabelInsteadOfOverlapping()} — a direct
 * regression guard for the fixed-spacing overlap bug this redesign exists
 * to fix.
 */
class RedesignedMissionReportGeneratorTest {

    private final RedesignedMissionReportGenerator generator = new RedesignedMissionReportGenerator();

    @Test
    void generatesAllFiveTabsFromABasicTwoStateMission() {

        String html = generator.generate(MissionReportData.from(twoStateContext(), MissionStatus.SUCCESS));

        assertTrue(html.contains("<title>AEGIS Report — Test</title>"));
        assertTrue(html.contains("data-tab=\"overview\""));
        assertTrue(html.contains("data-tab=\"findings\""));
        assertTrue(html.contains("data-tab=\"reasoning\""));
        assertTrue(html.contains("data-tab=\"timeline\""));
        assertTrue(html.contains("data-tab=\"world-model\""));
        assertTrue(html.contains("node-label\">Login<"));
        assertTrue(html.contains("node-label\">Dashboard<"));
    }

    @Test
    void pathViewIsTheDefaultVisibleGraphView() {

        String html = generator.generate(MissionReportData.from(twoStateContext(), MissionStatus.SUCCESS));

        assertTrue(html.contains("data-view=\"path\" class=\"chip active\""));
        assertTrue(html.contains("data-graph-view=\"path\">"));
        assertTrue(html.contains("data-graph-view=\"graph\" style=\"display:none\">"));
    }

    @Test
    void selfLoopsCollapseIntoABadgeInsteadOfADrawnCurve() {

        MissionContext context = new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", Map.of()));
        context.getExecutionState().setCurrentObservation(observation("https://example.com/login", "Login", "login-button"));
        context.getExecutionState().addAction(click("login-button"));
        context.getExecutionState().setCurrentObservation(observation("https://example.com/login", "Login", "login-button"));
        context.getExecutionState().addAction(click("login-button"));
        context.getExecutionState().setCurrentObservation(observation("https://example.com/login", "Login", "login-button"));

        String html = generator.generate(MissionReportData.from(context, MissionStatus.SUCCESS));

        int graphViewIndex = html.indexOf("data-graph-view=\"graph\"");
        String graphViewHtml = html.substring(graphViewIndex);

        assertTrue(graphViewHtml.contains("node-label\">Login<"));
        assertTrue(graphViewHtml.contains("node-sub\">×2 visits<"));
    }

    /**
     * Direct regression guard for the bug the user reported: the frozen
     * generator used a fixed 160px {@code nodeSpacing} constant regardless
     * of label length, so a long display name would overlap its neighbor.
     * This generator instead derives spacing from each label's estimated
     * rendered width. Two branches from the same source state land in the
     * same BFS layer — one with a short name, one with a deliberately long
     * one — and the graph's computed SVG viewBox must measurably widen to
     * fit the long one; a fixed-spacing layout would produce the same
     * width regardless of label content.
     */
    @Test
    void graphSpacingWidensToFitALongLabelInsteadOfOverlapping() {

        String shortLabelHtml = generator.generate(MissionReportData.from(twoSiblingContext("Cart", "Pay"), MissionStatus.SUCCESS));
        String longLabelHtml = generator.generate(MissionReportData.from(
                twoSiblingContext("Cart", "Complete Your International Shipping Address Form"), MissionStatus.SUCCESS));

        double shortViewBoxWidth = viewBoxWidth(shortLabelHtml);
        double longViewBoxWidth = viewBoxWidth(longLabelHtml);

        assertTrue(longViewBoxWidth > shortViewBoxWidth,
                "expected the graph to widen for a long sibling label (short=" + shortViewBoxWidth
                        + ", long=" + longViewBoxWidth + ")");
    }

    /**
     * Regression guard for the misleading-"learned"-badge bug: a plain
     * exploration bonus ({@code HeuristicCandidateConfidenceEstimator}'s
     * flat +0.05 for a never-tried candidate) must NOT render the
     * "learned" badge — it isn't a real learned adjustment, just an
     * incentive to try something new.
     */
    @Test
    void explorationBonusDoesNotRenderALearnedBadge() {

        MissionContext context = twoStateContext();
        context.getExecutionState().addReasoningStep(reasoningStepWith(
                "Generic input, no credential match (learning-adjusted +0.05, never tried yet this mission)"));

        String html = generator.generate(MissionReportData.from(context, MissionStatus.SUCCESS));

        assertTrue(html.contains("exploring new ground"), html);
        assertTrue(!extractReasoningTabHtml(html).contains("learned-badge"),
                "a flat exploration bonus should never render the learned badge");
    }

    /** Counterpart: a genuine prior-experience adjustment (a real success/failure rate) should still show "learned". */
    @Test
    void genuineLearnedAdjustmentRendersALearnedBadge() {

        MissionContext context = twoStateContext();
        context.getExecutionState().addReasoningStep(reasoningStepWith("Persistent nav link (learning-adjusted +0.20)"));

        String html = generator.generate(MissionReportData.from(context, MissionStatus.SUCCESS));

        assertTrue(html.contains("learned: tends to work, +0.20"), html);
        assertTrue(extractReasoningTabHtml(html).contains("learned-badge"),
                "a genuine learned adjustment should render the learned badge");
    }

    @Test
    void learningTabNeverClaimsReliabilityForActionsTriedOnlyOnce() {

        MissionContext context = twoStateContext();
        Observation obs = observation("https://example.com/login", "Login", "el");

        List<Experience> experiences = List.of(
                Experience.create(context, obs, candidate("user-name"), ExperienceOutcome.SUCCESS, Duration.ofMillis(10)),
                Experience.create(context, obs, candidate("password"), ExperienceOutcome.SUCCESS, Duration.ofMillis(10)),
                Experience.create(context, obs, candidate("login-button"), ExperienceOutcome.SUCCESS, Duration.ofMillis(10)));

        String html = generator.generate(withExperiences(context, experiences));
        String learningHtml = extractLearningSectionHtml(html);

        assertTrue(learningHtml.contains(">First try<"), learningHtml);
        assertFalse(learningHtml.contains("improved"), learningHtml);
        assertTrue(learningHtml.contains("first attempt"), learningHtml);
        assertTrue(learningHtml.contains(">0<"), "Confirmed Reliable tile should read 0: " + learningHtml);
    }

    @Test
    void learningTabShowsConfirmedReliableForAGenuinelyRepeatedAction() {

        MissionContext context = twoStateContext();
        Observation obs = observation("https://example.com/login", "Login", "el");

        List<Experience> experiences = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            experiences.add(Experience.create(context, obs, candidate("nav-logout"), ExperienceOutcome.SUCCESS, Duration.ofMillis(10)));
        }
        experiences.add(Experience.create(context, obs, candidate("one-off"), ExperienceOutcome.SUCCESS, Duration.ofMillis(10)));

        String html = generator.generate(withExperiences(context, experiences));
        String learningHtml = extractLearningSectionHtml(html);

        assertTrue(learningHtml.contains(">Confirmed reliable<"), learningHtml);
    }

    @Test
    void learningTabShowsConfirmedUnreliableForARepeatedFailingAction() {

        MissionContext context = twoStateContext();
        Observation obs = observation("https://example.com/login", "Login", "el");

        List<Experience> experiences = List.of(
                Experience.create(context, obs, candidate("flaky-button"), ExperienceOutcome.ERROR, Duration.ofMillis(10)),
                Experience.create(context, obs, candidate("flaky-button"), ExperienceOutcome.ERROR, Duration.ofMillis(10)),
                Experience.create(context, obs, candidate("flaky-button"), ExperienceOutcome.SUCCESS, Duration.ofMillis(10)));

        String html = generator.generate(withExperiences(context, experiences));
        String learningHtml = extractLearningSectionHtml(html);

        assertTrue(learningHtml.contains(">Confirmed unreliable<"), learningHtml);
    }

    @Test
    void learningTabTableIsVisibleWithoutExpandingAnything() {

        MissionContext context = twoStateContext();
        Observation obs = observation("https://example.com/login", "Login", "el");

        List<Experience> experiences = List.of(
                Experience.create(context, obs, candidate("user-name"), ExperienceOutcome.SUCCESS, Duration.ofMillis(10)));

        String html = generator.generate(withExperiences(context, experiences));
        String learningHtml = extractLearningSectionHtml(html);

        assertFalse(html.contains("Show the technical breakdown, action by action"), html);
        assertTrue(learningHtml.contains("<table>"), learningHtml);
    }

    private String extractLearningSectionHtml(String html) {
        int start = html.indexOf("<section id=\"learning\">");
        int end = html.indexOf("<section", start + 1);
        return html.substring(start, end);
    }

    private MissionReportData withExperiences(MissionContext context, List<Experience> experiences) {
        return MissionReportData.from(
                context, MissionStatus.SUCCESS,
                (BugExplainer) (cluster, fallback) -> fallback,
                (RecommendationEngine) (clusters, fallback) -> fallback,
                new MissionPlan(List.of()),
                experiences);
    }

    private CandidateAction candidate(String target) {
        return new CandidateAction(click(target), 0.8, "test");
    }

    private String extractReasoningTabHtml(String html) {
        int start = html.indexOf("data-tab-panel=\"reasoning\"");
        int end = html.indexOf("data-tab-panel=\"timeline\"");
        return html.substring(start, end);
    }

    private ReasoningStep reasoningStepWith(String reasoning) {

        Action action = click("submit-button");
        CandidateAction candidate = new CandidateAction(action, 0.85, reasoning);

        return new ReasoningStep(1, List.of(candidate), candidate, Instant.now(), "greedy");
    }

    private double viewBoxWidth(String html) {

        int graphViewIndex = html.indexOf("data-graph-view=\"graph\"");
        String graphViewHtml = html.substring(graphViewIndex);

        Matcher matcher = Pattern.compile("viewBox=\"(-?\\d+) (-?\\d+) (\\d+) (\\d+)\"").matcher(graphViewHtml);
        assertTrue(matcher.find(), "expected a viewBox attribute in the graph view");

        return Double.parseDouble(matcher.group(3));
    }

    /** Two branches (siblings) from the same "Home" source state, landing in the same BFS layer. */
    private MissionContext twoSiblingContext(String firstChildTitle, String secondChildTitle) {

        MissionContext context = new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", Map.of()));

        context.getExecutionState().setCurrentObservation(observation("https://example.com/home", "Home", "link-a"));
        context.getExecutionState().addAction(click("link-a"));
        context.getExecutionState().setCurrentObservation(observation("https://example.com/a", firstChildTitle, "back-link"));
        context.getExecutionState().addAction(click("back-link"));
        context.getExecutionState().setCurrentObservation(observation("https://example.com/home", "Home", "link-b"));
        context.getExecutionState().addAction(click("link-b"));
        context.getExecutionState().setCurrentObservation(observation("https://example.com/b", secondChildTitle, "final"));

        return context;
    }

    private MissionContext twoStateContext() {

        MissionContext context = new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", Map.of()));

        context.getExecutionState().setCurrentObservation(observation("https://example.com/login", "Login", "login-button"));
        context.getExecutionState().addAction(click("login-button"));
        context.getExecutionState().setCurrentObservation(observation("https://example.com/dashboard", "Dashboard", "logout-link"));

        return context;
    }

    private Observation observation(String url, String pageTitle, String elementLocator) {
        List<ElementInfo> elements = List.of(new ElementInfo("button", null, null, null, "button", null, true, true, elementLocator));
        return new Observation(url, pageTitle, elements, elements, List.of(), List.of(), List.of(), Instant.now());
    }

    private Action click(String target) {
        return new Action(UUID.randomUUID(), ActionType.CLICK, target, null, "test", 0.8, "test", Duration.ofSeconds(5), Instant.now(), "button");
    }
}
