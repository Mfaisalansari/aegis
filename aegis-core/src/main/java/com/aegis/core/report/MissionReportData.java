package com.aegis.core.report;

import com.aegis.core.bug.BugCluster;
import com.aegis.core.bug.BugExplainer;
import com.aegis.core.bug.DefaultBugClusterAnalyzer;
import com.aegis.core.bug.RecommendationEngine;
import com.aegis.core.bug.RuleBasedBugExplainer;
import com.aegis.core.bug.RuleBasedRecommendationEngine;
import com.aegis.core.mission.MissionPlan;
import com.aegis.core.mission.RuleBasedMissionPlanner;
import com.aegis.core.reasoning.memory.StateSignature;
import com.aegis.model.action.Action;
import com.aegis.model.context.MissionContext;
import com.aegis.model.finding.Finding;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionStatus;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;
import com.aegis.model.reasoning.NavigationEdge;
import com.aegis.model.reasoning.ReasoningStep;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything a report renderer (text, HTML, ...) needs, derived once from
 * a finished mission's stored ExecutionState. Kept separate from any one
 * rendering format so text/HTML generators can't drift on what "states",
 * "edges", or a finding's plain-English meaning is.
 */
public record MissionReportData(
        String missionName,
        String missionGoal,
        MissionStatus status,
        List<String> visitedPages,
        List<String> states,
        List<NavigationEdge> edges,
        List<Finding> findings,
        List<ReasoningStep> reasoningSteps,
        ExplorationCoverage coverage,
        List<PageCoverage> pageCoverage,
        Map<String, Long> stateVisitCounts,
        List<BugCluster> bugClusters,
        Map<String, String> bugExplanations,
        String recommendation,
        MissionPlan plan
) {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    public static MissionReportData from(MissionContext context, MissionStatus status) {
        return from(context, status, new RuleBasedBugExplainer(), new RuleBasedRecommendationEngine());
    }

    /**
     * Same as from(context, status), but with AI bug explanations
     * (Phase 8) opted into via an explicit BugExplainer — a real
     * LlmBugExplainer, or the default RuleBasedBugExplainer used by the
     * 2-arg overload above when no explainer is given. Explanations are
     * computed once per cluster right here, not lazily at render time,
     * so a single report generation never calls the model more than once
     * per bug regardless of how many report formats read this same data.
     */
    public static MissionReportData from(MissionContext context, MissionStatus status, BugExplainer explainer) {
        return from(context, status, explainer, new RuleBasedRecommendationEngine());
    }

    /**
     * Same as the 3-arg overload, with AI recommendations (Phase 8) also
     * opted into via an explicit RecommendationEngine — computed once,
     * across all of the mission's bug clusters together, not per cluster
     * like BugExplainer. Uses a rule-based MissionPlan by default (see
     * the 5-arg overload below to supply a real, pre-computed one).
     */
    public static MissionReportData from(
            MissionContext context, MissionStatus status, BugExplainer explainer, RecommendationEngine recommender) {

        return from(context, status, explainer, recommender, new RuleBasedMissionPlanner().plan(context.getMission()));
    }

    /**
     * Same as the 4-arg overload, with an AI mission plan (Phase 8) also
     * supplied — unlike BugExplainer/RecommendationEngine, a MissionPlan
     * is already-computed data here, not a strategy invoked internally:
     * a plan describes the *intended* approach, generated before the
     * mission runs (see MissionPlanner), so by the time a report is
     * being built from a finished MissionContext there's nothing left
     * to plan — this just carries that earlier decision through.
     */
    public static MissionReportData from(
            MissionContext context, MissionStatus status, BugExplainer explainer,
            RecommendationEngine recommender, MissionPlan plan) {

        List<Observation> observations = context.getExecutionState().getObservations();
        List<Action> actions = context.getExecutionState().getActions();

        Set<String> pages = new LinkedHashSet<>();
        Set<String> states = new LinkedHashSet<>();

        for (Observation observation : observations) {
            pages.add(observation.url());
            states.add(StateSignature.of(observation));
        }

        List<NavigationEdge> edges = new ArrayList<>();

        for (int i = 0; i < actions.size() && i + 1 < observations.size(); i++) {

            edges.add(new NavigationEdge(
                    StateSignature.of(observations.get(i)),
                    actions.get(i).type(),
                    actions.get(i).target(),
                    StateSignature.of(observations.get(i + 1))
            ));
        }

        Mission mission = context.getMission();

        List<Finding> findings = context.getExecutionState().getFindings();
        List<BugCluster> bugClusters = new DefaultBugClusterAnalyzer().analyze(findings);

        return new MissionReportData(
                mission.name(),
                goalStatement(mission),
                status,
                List.copyOf(pages),
                List.copyOf(states),
                edges,
                findings,
                context.getExecutionState().getReasoningSteps(),
                computeCoverage(observations, actions),
                computePageCoverage(observations, actions),
                computeStateVisitCounts(observations),
                bugClusters,
                computeBugExplanations(bugClusters, explainer),
                recommender.recommend(bugClusters, computeFallbackRecommendation(bugClusters)),
                plan
        );
    }

    private static Map<String, String> computeBugExplanations(List<BugCluster> bugClusters, BugExplainer explainer) {

        Map<String, String> explanations = new LinkedHashMap<>();

        for (BugCluster cluster : bugClusters) {

            String ruleBasedFallback = explainSummary(cluster.representativeSummary(), firstUrlOf(cluster));

            explanations.put(cluster.fingerprint(), explainer.explain(cluster, ruleBasedFallback));
        }

        return explanations;
    }

    /**
     * The rule-based recommendation every report gets unless AI
     * recommendations are opted into: point at the single highest-
     * severity cluster (bugClusters is already sorted that way by
     * DefaultBugClusterAnalyzer) and say how often it happened — the
     * one thing a reader most needs before anything else.
     */
    private static String computeFallbackRecommendation(List<BugCluster> bugClusters) {

        if (bugClusters.isEmpty()) {
            return "No issues found this run — nothing to prioritize.";
        }

        BugCluster top = bugClusters.get(0);

        return String.format(
                "%d issue(s) found. Highest priority: [%s] %s (occurred %d time%s%s).",
                bugClusters.size(),
                top.severity(),
                top.representativeSummary(),
                top.occurrenceCount(),
                top.occurrenceCount() == 1 ? "" : "s",
                top.spansMultiplePages() ? ", seen on " + top.urls().size() + " pages" : ""
        );
    }

    private static String firstUrlOf(BugCluster cluster) {
        return cluster.urls().isEmpty() ? "" : cluster.urls().iterator().next();
    }

    /**
     * The same elements-discovered-vs-interacted computation as
     * ExplorationCoverage, but grouped per page URL instead of blended
     * mission-wide — Phase 5's "Page coverage". An action's page is the
     * URL of the observation immediately preceding it (the state it was
     * actually taken from), the same pairing computeCoverage's caller
     * and the edges loop above both already rely on.
     */
    private static List<PageCoverage> computePageCoverage(List<Observation> observations, List<Action> actions) {

        Map<String, Set<String>> discoveredByPage = new LinkedHashMap<>();

        for (Observation observation : observations) {

            Set<String> pageElements =
                    discoveredByPage.computeIfAbsent(observation.url(), key -> new LinkedHashSet<>());

            for (ElementInfo element : observation.elements()) {
                pageElements.add(element.locator());
            }
        }

        Map<String, Set<String>> interactedByPage = new LinkedHashMap<>();

        for (int i = 0; i < actions.size() && i < observations.size(); i++) {

            String target = actions.get(i).target();

            if (target != null && !target.isBlank()) {
                interactedByPage
                        .computeIfAbsent(observations.get(i).url(), key -> new LinkedHashSet<>())
                        .add(target);
            }
        }

        List<PageCoverage> result = new ArrayList<>();

        for (Map.Entry<String, Set<String>> entry : discoveredByPage.entrySet()) {

            String url = entry.getKey();
            Set<String> discovered = entry.getValue();
            Set<String> interacted = interactedByPage.getOrDefault(url, Set.of());

            long interactedCount = interacted.stream().filter(discovered::contains).count();

            double percent = discovered.isEmpty()
                    ? 0.0
                    : (100.0 * interactedCount / discovered.size());

            result.add(new PageCoverage(url, discovered.size(), (int) interactedCount, percent));
        }

        return result;
    }

    /**
     * How many times each state (see StateSignature) was actually
     * observed — the raw revisit frequency the HTML report's navigation
     * graph uses to visually "heat" nodes and edges by how often
     * exploration passed through them, not just whether it ever did.
     */
    private static Map<String, Long> computeStateVisitCounts(List<Observation> observations) {

        Map<String, Long> counts = new LinkedHashMap<>();

        for (Observation observation : observations) {
            counts.merge(StateSignature.of(observation), 1L, Long::sum);
        }

        return counts;
    }

    /**
     * Every distinct interactive element locator seen across every
     * observation this mission, versus how many of those were the target
     * of an executed action. Page-level actions (REFRESH/BACK) have a
     * blank target and are excluded from the denominator and numerator
     * alike — there's no element to have "covered".
     */
    private static ExplorationCoverage computeCoverage(List<Observation> observations, List<Action> actions) {

        Set<String> discovered = new LinkedHashSet<>();

        for (Observation observation : observations) {
            for (ElementInfo element : observation.elements()) {
                discovered.add(element.locator());
            }
        }

        Set<String> interacted = new LinkedHashSet<>();

        for (Action action : actions) {

            String target = action.target();

            if (target != null && !target.isBlank() && discovered.contains(target)) {
                interacted.add(target);
            }
        }

        double percent = discovered.isEmpty()
                ? 0.0
                : (100.0 * interacted.size() / discovered.size());

        return new ExplorationCoverage(discovered.size(), interacted.size(), percent);
    }

    private static String goalStatement(Mission mission) {

        String description = mission.description();
        String successMarker = mission.parameter("successUrlContains");

        if (successMarker == null || successMarker.isBlank()) {
            return description;
        }

        return description + " (success = reaching a URL containing \"" + successMarker + "\")";
    }

    /**
     * One plain-English sentence covering what happened and how bad it was
     * — the thing a reader wants before they see a single raw log line.
     */
    public String outcomeSummary() {

        String base = status == MissionStatus.SUCCESS
                ? "AEGIS reached the mission goal after exploring " + states.size() + " state(s)."
                : "AEGIS did not reach the mission goal — it hit its iteration limit after exploring "
                        + states.size() + " state(s).";

        if (findings.isEmpty()) {
            return base + " No anomalies were observed along the way.";
        }

        long critical = countBySeverity("CRITICAL");
        long high = countBySeverity("HIGH");
        long other = findings.size() - critical - high;

        List<String> parts = new ArrayList<>();
        if (critical > 0) parts.add(critical + " critical");
        if (high > 0) parts.add(high + " high-severity");
        if (other > 0) parts.add(other + " lower-severity");

        return base + " Along the way it recorded " + String.join(", ", parts)
                + " finding" + (findings.size() == 1 ? "" : "s") + ".";
    }

    private long countBySeverity(String severity) {
        return findings.stream().filter(f -> f.severity().name().equals(severity)).count();
    }

    /** Findings ordered most-severe first, so the reader sees what matters before what's noise. */
    public List<Finding> rankedFindings() {
        return findings.stream()
                .sorted(Comparator.comparingInt((Finding f) -> f.severity().ordinal()).reversed())
                .toList();
    }

    /**
     * Translates a finding's raw signal type into what it actually means
     * for the reader, including a best-effort call on whether a failed
     * request is the app under test or an unrelated third-party resource
     * (ads/analytics) — the two look identical as a raw stack trace but
     * mean very different things.
     */
    public String explain(Finding finding) {
        return explainSummary(finding.summary(), finding.url());
    }

    /**
     * The AI explanation (Phase 8) for a BugCluster if one was computed
     * (see the from(context, status, BugExplainer) overload), falling
     * back to the same rule-based text explain(Finding) would produce —
     * every cluster has an entry either way, since computeBugExplanations
     * always writes one, even when the explainer in use is just
     * RuleBasedBugExplainer.
     */
    public String explanationFor(BugCluster cluster) {
        return bugExplanations.getOrDefault(
                cluster.fingerprint(),
                explainSummary(cluster.representativeSummary(), firstUrlOf(cluster)));
    }

    private static String explainSummary(String summary, String url) {

        String kind = summary.contains(":") ? summary.substring(0, summary.indexOf(':')) : summary;

        return switch (kind) {
            case "CRASH" -> "The browser tab crashed outright — a real, serious defect.";
            case "PAGE_ERROR" -> "An uncaught JavaScript exception on the page — usually a genuine client-side bug.";
            case "REQUEST_FAILED" -> isLikelyThirdParty(url, summary)
                    ? "A network request failed, but it looks like a third-party resource (ads/analytics) — probably not a bug in the app itself."
                    : "A network request to the app's own domain failed to load — worth checking.";
            case "CONSOLE_ERROR" -> "The page logged a console error — often noise from third-party scripts, but worth a look if it keeps recurring.";
            case "DIALOG" -> "A native browser dialog (alert/confirm/prompt) appeared and was automatically dismissed — the action that triggered it may not have completed the way a user clicking \"OK\" would expect.";
            case "Engine error" -> "AEGIS itself hit a problem interacting with the page (e.g. a stale element right after navigation) — it recovered and kept going, but this step may not have done what was intended.";
            default -> "Unclassified signal — review manually.";
        };
    }

    private static boolean isLikelyThirdParty(String url, String summary) {

        String pageHost = hostOf(url);
        String resourceHost = firstUrlIn(summary).map(MissionReportData::hostOf).orElse(null);

        return pageHost != null && resourceHost != null && !pageHost.equalsIgnoreCase(resourceHost);
    }

    private static java.util.Optional<String> firstUrlIn(String text) {
        Matcher matcher = URL_PATTERN.matcher(text);
        return matcher.find() ? java.util.Optional.of(matcher.group()) : java.util.Optional.empty();
    }

    private static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * How far the selected action's confidence was ahead of its closest
     * competitor — the actual "why this one and not another" signal that a
     * bare confidence score doesn't convey. NaN when there was nothing to
     * compare against (a single-candidate step).
     */
    public double runnerUpMargin(ReasoningStep step) {

        double runnerUp = step.candidates().stream()
                .filter(candidate -> !candidate.action().id().equals(step.selected().action().id()))
                .mapToDouble(CandidateAction::confidence)
                .max()
                .orElse(Double.NaN);

        return Double.isNaN(runnerUp) ? Double.NaN : step.selected().confidence() - runnerUp;
    }
}
