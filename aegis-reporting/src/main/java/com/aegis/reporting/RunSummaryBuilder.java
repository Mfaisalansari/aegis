package com.aegis.reporting;

import com.aegis.core.knowledge.ExperienceScoreCatalog;
import com.aegis.core.knowledge.InspectionCatalog;
import com.aegis.core.knowledge.InspectionFinding;
import com.aegis.core.knowledge.KnowledgeBase;
import com.aegis.core.knowledge.Node;
import com.aegis.core.knowledge.NodeCatalog;
import com.aegis.core.knowledge.TestIntelligenceCatalog;
import com.aegis.core.knowledge.UxFinding;
import com.aegis.core.knowledge.UxFindingCatalog;
import com.aegis.model.action.Action;
import com.aegis.model.finding.FindingSeverity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extracts a {@link RunSummary} from an already-built {@link
 * KnowledgeBase} and its {@code Action} history — pure distillation, no
 * new detection logic. The heal/self-input counts key off the exact same
 * reasoning-string prefixes {@code Hooks.java}'s own audit-trail filter
 * already uses ({@code ObservationSession#recordHealedLocator}/{@code
 * recordSelfInput}'s conventions), so this must never drift from those
 * two constants independently.
 */
public final class RunSummaryBuilder {

    static final String HEAL_PREFIX = "Self-healed by AEGIS via ";
    static final String SELF_INPUT_PREFIX = "Self-input by AEGIS via ";

    private static final int MAX_NOTABLE_FINDINGS = 3;

    private RunSummaryBuilder() {
    }

    public static RunSummary from(String name, boolean passed, KnowledgeBase knowledgeBase, List<Action> actions) {

        ExperienceScoreCatalog experienceScore = knowledgeBase.require(ExperienceScoreCatalog.class);
        UxFindingCatalog uxFindingCatalog = knowledgeBase.require(UxFindingCatalog.class);
        InspectionCatalog inspectionCatalog = knowledgeBase.require(InspectionCatalog.class);

        int healCount = 0;
        int selfInputCount = 0;

        for (Action action : actions) {
            if (action.reasoning().startsWith(HEAL_PREFIX)) {
                healCount++;
            } else if (action.reasoning().startsWith(SELF_INPUT_PREFIX)) {
                selfInputCount++;
            }
        }

        List<Finding> findings = new ArrayList<>();
        for (UxFinding finding : uxFindingCatalog.findings()) {
            findings.add(new Finding(finding.severity(), finding.summary()));
        }
        for (InspectionFinding finding : inspectionCatalog.findings()) {
            findings.add(new Finding(finding.severity(), finding.summary()));
        }

        int criticalCount = (int) findings.stream().filter(f -> f.severity() == FindingSeverity.CRITICAL).count();

        List<String> notable = findings.stream()
                .sorted(Comparator.comparing(Finding::severity, Comparator.reverseOrder()))
                .map(Finding::summary)
                .limit(MAX_NOTABLE_FINDINGS)
                .toList();

        int newlyDiscoveredCount = 0;
        int untestedCount = 0;
        List<String> learningNotes = List.of();

        var testIntelligence = knowledgeBase.get(TestIntelligenceCatalog.class);
        if (testIntelligence.isPresent()) {
            TestIntelligenceCatalog catalog = testIntelligence.get();
            NodeCatalog nodeCatalog = knowledgeBase.require(NodeCatalog.class);

            newlyDiscoveredCount = catalog.newlyDiscoveredNodeKeys().size();
            untestedCount = catalog.untestedNodeKeys().size();

            List<String> newlyDiscoveredNames = catalog.newlyDiscoveredNodeKeys().stream()
                    .map(key -> displayNameFor(key, nodeCatalog))
                    .toList();
            List<String> untestedNames = catalog.untestedNodeKeys().stream()
                    .map(key -> displayNameFor(key, nodeCatalog))
                    .toList();

            learningNotes = ReportLanguage.learningNotesFor(newlyDiscoveredNames, untestedNames);
        }

        return new RunSummary(
                name, passed, experienceScore.score(),
                healCount, selfInputCount, criticalCount, findings.size(), notable,
                newlyDiscoveredCount, untestedCount, learningNotes);
    }

    /**
     * Suite-wide coverage learning notes, for a caller (e.g. a Cucumber
     * {@code @AfterAll} hook) tracking coverage across a whole suite run
     * rather than one {@code KnowledgeBase} at a time — see {@link #from}'s
     * heal/self-input scope note for why per-scenario coverage comparison
     * is the wrong scope for this specifically: a single scenario is only
     * ever meant to touch its own flow, not the whole app, so comparing
     * its pages alone against the app's full history would flag every
     * OTHER scenario's pages as "missing" on every single run.
     *
     * {@code nodeDisplayNames} should map every key touched at any point
     * this suite run to its real page name (each scenario's own {@code
     * NodeCatalog} has this); untested keys fall back to a humanized slug
     * since no name was ever captured for them (see {@link
     * #displayNameFor}).
     */
    public static List<String> suiteLearningNotes(
            Map<String, String> nodeDisplayNames, Set<String> newlyDiscoveredKeys, Set<String> untestedKeys) {

        List<String> newlyDiscoveredNames = newlyDiscoveredKeys.stream()
                .map(key -> nodeDisplayNames.getOrDefault(key, humanize(key)))
                .toList();
        List<String> untestedNames = untestedKeys.stream()
                .map(key -> nodeDisplayNames.getOrDefault(key, humanize(key)))
                .toList();

        return ReportLanguage.learningNotesFor(newlyDiscoveredNames, untestedNames);
    }

    /**
     * A raw {@code TestIntelligenceCatalog} node key (a URL-derived slug,
     * e.g. {@code "registerresult-1"}) is meaningless to a non-technical
     * reader. This run's own {@link NodeCatalog} has a real {@code
     * displayName} (the page's actual title, when captured) for anything
     * newly discovered THIS run. A node that's untested (not visited this
     * run) has no entry in this run's catalog — {@link CoverageStore} only
     * ever persisted the bare key, not its name — so the best available
     * fallback is cosmetically humanizing the slug itself, not a fabricated
     * page title.
     */
    private static String displayNameFor(String key, NodeCatalog nodeCatalog) {
        return nodeCatalog.byKey(key).map(Node::displayName).orElseGet(() -> humanize(key));
    }

    private static String humanize(String slug) {

        String[] parts = slug.split("[-_]+");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }

        return result.isEmpty() ? slug : result.toString();
    }

    private record Finding(FindingSeverity severity, String summary) {
    }
}
