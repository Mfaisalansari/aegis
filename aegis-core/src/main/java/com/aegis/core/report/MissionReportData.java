package com.aegis.core.report;

import com.aegis.core.reasoning.memory.StateSignature;
import com.aegis.model.action.Action;
import com.aegis.model.context.MissionContext;
import com.aegis.model.finding.Finding;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionStatus;
import com.aegis.model.observation.Observation;
import com.aegis.model.reasoning.CandidateAction;
import com.aegis.model.reasoning.NavigationEdge;
import com.aegis.model.reasoning.ReasoningStep;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
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
        List<ReasoningStep> reasoningSteps
) {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    public static MissionReportData from(MissionContext context, MissionStatus status) {

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

        return new MissionReportData(
                mission.name(),
                goalStatement(mission),
                status,
                List.copyOf(pages),
                List.copyOf(states),
                edges,
                context.getExecutionState().getFindings(),
                context.getExecutionState().getReasoningSteps()
        );
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

        String summary = finding.summary();
        String kind = summary.contains(":") ? summary.substring(0, summary.indexOf(':')) : summary;

        return switch (kind) {
            case "CRASH" -> "The browser tab crashed outright — a real, serious defect.";
            case "PAGE_ERROR" -> "An uncaught JavaScript exception on the page — usually a genuine client-side bug.";
            case "REQUEST_FAILED" -> isLikelyThirdParty(finding)
                    ? "A network request failed, but it looks like a third-party resource (ads/analytics) — probably not a bug in the app itself."
                    : "A network request to the app's own domain failed to load — worth checking.";
            case "CONSOLE_ERROR" -> "The page logged a console error — often noise from third-party scripts, but worth a look if it keeps recurring.";
            case "DIALOG" -> "A native browser dialog (alert/confirm/prompt) appeared and was automatically dismissed — the action that triggered it may not have completed the way a user clicking \"OK\" would expect.";
            case "Engine error" -> "AEGIS itself hit a problem interacting with the page (e.g. a stale element right after navigation) — it recovered and kept going, but this step may not have done what was intended.";
            default -> "Unclassified signal — review manually.";
        };
    }

    private boolean isLikelyThirdParty(Finding finding) {

        String pageHost = hostOf(finding.url());
        String resourceHost = firstUrlIn(finding.summary()).map(this::hostOf).orElse(null);

        return pageHost != null && resourceHost != null && !pageHost.equalsIgnoreCase(resourceHost);
    }

    private java.util.Optional<String> firstUrlIn(String text) {
        Matcher matcher = URL_PATTERN.matcher(text);
        return matcher.find() ? java.util.Optional.of(matcher.group()) : java.util.Optional.empty();
    }

    private String hostOf(String url) {
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
