package com.aegis.observation;

import com.aegis.model.action.Action;
import com.aegis.model.action.ActionType;
import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionStatus;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AEGIS 2.0 Phase 1 — the entry point for turning what an externally-driven
 * test (Selenium, Playwright, or a manual note-taker) actually did into the
 * exact same {@link MissionContext} shape AEGIS's own autonomous engine
 * produces. Once {@link #finish} returns, the result is indistinguishable
 * in structure from a live AEGIS run — it goes through the same
 * {@code KnowledgeBaseBuilder.build(...)}/{@code MissionReportData.from(...)}
 * calls (both in {@code aegis-core}, deliberately not a dependency of this
 * module — see this module's own {@code pom.xml}), producing the same
 * Knowledge Layer output and the same report.
 *
 * Not thread-safe, and not meant to be — one session per externally-observed
 * mission/test, mirroring one {@code MissionContext} per AEGIS run.
 */
public final class ObservationSession {

    private final String sourceName;
    private final MissionContext context;

    /**
     * @param sourceName identifies where these observations came from —
     *                    e.g. "Selenium", "Playwright", "Manual" — recorded
     *                    into every {@link Action#reasoning()} this session
     *                    produces, so a reader of any downstream report can
     *                    never mistake an ingested action for one AEGIS
     *                    itself chose.
     * @param missionName shown as the mission/report title, same as any
     *                     AEGIS-driven mission's name.
     */
    public ObservationSession(String sourceName, String missionName, String missionDescription) {

        this.sourceName = sourceName;

        Mission mission = new Mission(UUID.randomUUID(), missionName, missionDescription, Map.of());
        this.context = new MissionContext(mission);
    }

    /**
     * Records a DOM snapshot at the current point in the external test —
     * the same shape {@code DefaultObserver} already builds from
     * {@code Browser.getButtons/getInputs/getLinks/getSelects()}, just
     * supplied directly instead of read live from a browser AEGIS itself
     * drives. {@code elements} (the combined list every report/knowledge
     * builder actually reads) is computed here as the union of the four
     * categorized lists, mirroring {@code DefaultObserver}'s own
     * convention exactly, so a caller only ever has to categorize once.
     */
    public void recordObservation(
            String url, String pageTitle,
            List<ElementInfo> buttons, List<ElementInfo> inputs,
            List<ElementInfo> links, List<ElementInfo> selects) {

        List<ElementInfo> elements = new ArrayList<>();
        elements.addAll(inputs);
        elements.addAll(buttons);
        elements.addAll(links);
        elements.addAll(selects);

        context.getExecutionState().setCurrentObservation(
                new Observation(url, pageTitle, elements, buttons, inputs, links, selects, Instant.now()));
    }

    /**
     * Records one action the external test actually took.
     *
     * {@link Action#confidence()}/{@link Action#reasoning()}/{@link
     * Action#expectedOutcome()} are AEGIS's own decision-making artifacts —
     * this session never fabricates a plausible-looking one. Confidence is
     * fixed at {@code 1.0} (it happened, it's a fact, not a candidate AEGIS
     * was ever unsure about); reasoning is a plain, honest label naming the
     * source; expectedOutcome is left {@code null} — there's no honest
     * value for "what AEGIS expected to happen," since AEGIS never
     * predicted anything here.
     */
    public void recordAction(ActionType type, String target, String value, String elementTag) {

        Action action = new Action(
                UUID.randomUUID(),
                type,
                target,
                value,
                "Externally observed via " + sourceName + " — not scored by AEGIS",
                1.0,
                null,
                Duration.ZERO,
                Instant.now(),
                elementTag
        );

        context.getExecutionState().addAction(action);
    }

    /**
     * Records a self-healing event — a locator that failed and was
     * transparently substituted with an alternate that worked (see
     * {@code aegis-selenium-active}'s {@code SelfHealingWebDriverDecorator}).
     * Confidence is fixed at {@code 1.0} for the same reason {@link
     * #recordAction} fixes it there — a successful heal is a fact, not a
     * guess — but {@code reasoning} carries a fixed, greppable prefix
     * distinct from a plain observed action, so a reader can always tell
     * "AEGIS substituted a locator here" apart from "the test's own action,"
     * and is explicitly prompted to verify the substitution was cosmetic
     * (a shifted id/name/class) rather than a masked regression.
     */
    public void recordHealedLocator(ActionType type, String originalLocator, String healedLocator, String value, String elementTag) {

        Action action = new Action(
                UUID.randomUUID(),
                type,
                healedLocator,
                value,
                "Self-healed by AEGIS via " + sourceName + ": original locator '" + originalLocator
                        + "' failed, substituted '" + healedLocator
                        + "' — verify this substitution didn't mask a real UI regression",
                1.0,
                null,
                Duration.ZERO,
                Instant.now(),
                elementTag
        );

        context.getExecutionState().addAction(action);
    }

    /**
     * Records a self-input event — a field value AEGIS generated on the
     * test's behalf (see {@code aegis-selenium-active}'s {@code
     * AegisSelfInput}), rather than one the test author typed. This is a
     * bigger behavior change than a heal (it contributes genuinely new
     * data, not just a preserved locator), so recording it is mandatory
     * for every caller of {@code AegisSelfInput.fillWith(...)} rather
     * than left optional.
     */
    public void recordSelfInput(String target, String value, String elementTag, String strategy) {

        Action action = new Action(
                UUID.randomUUID(),
                ActionType.TYPE,
                target,
                value,
                "Self-input by AEGIS via " + sourceName + " (" + strategy
                        + " strategy) — value was generated, not authored by the test",
                1.0,
                null,
                Duration.ZERO,
                Instant.now(),
                elementTag
        );

        context.getExecutionState().addAction(action);
    }

    /**
     * Closes out the session. {@code status} is caller-supplied, not
     * AEGIS-derived — an ingested session never runs through AEGIS's own
     * {@code GoalEvaluator}, so whatever pass/fail signal the external test
     * framework already has (a JUnit assertion, a TestNG result, a
     * Cucumber scenario outcome) is the honest source of truth here.
     */
    public ObservationResult finish(MissionStatus status) {
        return new ObservationResult(context, status);
    }
}
