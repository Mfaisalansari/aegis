package com.aegis.core.knowledge;

import com.aegis.core.reasoning.memory.StateSignature;
import com.aegis.model.finding.FindingSeverity;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Built-in {@link KnowledgeProvider} for {@link UxFindingCatalog} — forms
 * an opinion on how good the mission's navigation experience was, on top
 * of the facts {@link StateCatalog}/{@link NodeCatalog}/{@link
 * JourneyCatalog} already discovered. Same governing principle as the
 * rest of this layer: report facts (backtracking, journey divergence),
 * only make a value judgment (navigation friction) where the organization
 * declared an expectation to measure against.
 */
public final class UxAnalysisCatalogProvider implements KnowledgeProvider {

    @Override
    public UxFindingCatalog provide(KnowledgeBuildContext context) {

        StateCatalog stateCatalog = context.partialBase().require(StateCatalog.class);
        NodeCatalog nodeCatalog = context.partialBase().require(NodeCatalog.class);
        JourneyCatalog journeyCatalog = context.partialBase().require(JourneyCatalog.class);
        KnowledgeConfig config = context.config() == null ? KnowledgeConfig.empty() : context.config();

        List<UxFinding> findings = new ArrayList<>();
        findings.addAll(detectBacktracking(context.observations(), stateCatalog, nodeCatalog));
        findings.addAll(detectJourneyDivergence(journeyCatalog));
        findings.addAll(detectNavigationFriction(journeyCatalog, config));
        findings.addAll(detectMissingAccessibleNames(context.observations()));

        return new UxFindingCatalog(findings);
    }

    /**
     * A distinct state visited more than once — the deduplicated {@link
     * Journey#actualNodeKeySequence()} hides this, so it's recomputed
     * here directly from the raw observations, same key-resolution
     * approach {@link JourneyCatalogProvider} uses.
     */
    private List<UxFinding> detectBacktracking(List<Observation> observations, StateCatalog stateCatalog, NodeCatalog nodeCatalog) {

        Map<String, String> keyByStateSignature = new HashMap<>();
        for (State state : stateCatalog.states()) {
            nodeCatalog.byStateId(state.id()).ifPresent(node -> keyByStateSignature.put(state.stateSignature(), node.key()));
        }

        List<String> visitedKeysInOrder = new ArrayList<>();
        for (Observation observation : observations) {
            String key = keyByStateSignature.get(StateSignature.of(observation));
            if (key != null) {
                visitedKeysInOrder.add(key);
            }
        }

        Map<String, Integer> visitCounts = new LinkedHashMap<>();
        for (String key : visitedKeysInOrder) {
            visitCounts.merge(key, 1, Integer::sum);
        }

        List<UxFinding> findings = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : visitCounts.entrySet()) {

            String nodeKey = entry.getKey();
            int visitCount = entry.getValue();

            if (visitCount < 2) {
                continue;
            }

            boolean immediateBacktrack = hasImmediateBackAndForth(visitedKeysInOrder, nodeKey);

            FindingSeverity severity = severityForVisitCount(visitCount);
            if (immediateBacktrack) {
                severity = escalate(severity);
            }

            String displayName = nodeCatalog.byKey(nodeKey).map(Node::displayName).orElse(nodeKey);

            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("visitCount", String.valueOf(visitCount));
            if (immediateBacktrack) {
                metadata.put("immediateBacktrack", "true");
            }

            findings.add(new UxFinding(
                    UxFindingType.BACKTRACKING,
                    severity,
                    "Visited '" + displayName + "' " + visitCount + " times during this run",
                    nodeKey,
                    metadata
            ));
        }

        return findings;
    }

    /** True if the same node was visited, left, and immediately returned to (A → B → A) — worse than a distant revisit. */
    private boolean hasImmediateBackAndForth(List<String> sequence, String nodeKey) {

        for (int i = 2; i < sequence.size(); i++) {
            if (sequence.get(i).equals(nodeKey)
                    && sequence.get(i - 2).equals(nodeKey)
                    && !sequence.get(i - 1).equals(nodeKey)) {
                return true;
            }
        }

        return false;
    }

    private FindingSeverity severityForVisitCount(int visitCount) {
        if (visitCount >= 4) {
            return FindingSeverity.HIGH;
        }
        if (visitCount == 3) {
            return FindingSeverity.MEDIUM;
        }
        return FindingSeverity.LOW;
    }

    private FindingSeverity escalate(FindingSeverity severity) {
        return switch (severity) {
            case LOW -> FindingSeverity.MEDIUM;
            case MEDIUM -> FindingSeverity.HIGH;
            case HIGH, CRITICAL -> FindingSeverity.CRITICAL;
        };
    }

    /**
     * Extends {@code Journey.matchesAnyDefinition()}'s yes/no signal into
     * a real diagnostic for any declared definition that did NOT match —
     * which declared nodes were never visited at all vs. visited but out
     * of the declared relative order.
     */
    private List<UxFinding> detectJourneyDivergence(JourneyCatalog journeyCatalog) {

        if (journeyCatalog.observed().isEmpty()) {
            return List.of();
        }

        Journey journey = journeyCatalog.observed().get(0);
        List<UxFinding> findings = new ArrayList<>();

        for (JourneyDefinition definition : journeyCatalog.definitions()) {

            if (definition.nodeKeys().isEmpty() || journey.matchedDefinitionKeys().contains(definition.key())) {
                continue;
            }

            JourneyDivergence divergence = diagnoseDivergence(definition.nodeKeys(), journey.actualNodeKeySequence());

            if (divergence.missingNodeKeys().isEmpty() && divergence.outOfOrderNodeKeys().isEmpty()) {
                continue;
            }

            FindingSeverity severity = divergence.missingNodeKeys().isEmpty() ? FindingSeverity.MEDIUM : FindingSeverity.HIGH;

            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("missingNodeKeys", String.join(",", divergence.missingNodeKeys()));
            metadata.put("outOfOrderNodeKeys", String.join(",", divergence.outOfOrderNodeKeys()));

            findings.add(new UxFinding(
                    UxFindingType.JOURNEY_DIVERGENCE,
                    severity,
                    "Journey '" + definition.name() + "' was not followed as declared",
                    definition.key(),
                    metadata
            ));
        }

        return findings;
    }

    private record JourneyDivergence(List<String> missingNodeKeys, List<String> outOfOrderNodeKeys) {
    }

    /** Which declared keys never appeared at all, vs. appeared but weren't consumed by the same greedy left-to-right scan {@code isSubsequence} uses. */
    private JourneyDivergence diagnoseDivergence(List<String> declaredKeys, List<String> actualSequence) {

        List<String> missing = declaredKeys.stream().filter(key -> !actualSequence.contains(key)).toList();

        Set<String> consumedInOrder = new LinkedHashSet<>();
        int position = 0;

        for (String key : actualSequence) {
            if (position < declaredKeys.size() && declaredKeys.get(position).equals(key)) {
                consumedInOrder.add(key);
                position++;
            }
        }

        List<String> outOfOrder = declaredKeys.stream()
                .filter(key -> actualSequence.contains(key) && !consumedInOrder.contains(key))
                .toList();

        return new JourneyDivergence(missing, outOfOrder);
    }

    /**
     * Only fires against an organization-declared {@code expectedMaxSteps}
     * — never an AEGIS-invented "too many steps" judgment. Scoped to the
     * distinct-node span between the journey's first and last declared
     * node within the whole mission's actual sequence, not the mission's
     * total length, so unrelated exploration elsewhere isn't penalized.
     */
    private List<UxFinding> detectNavigationFriction(JourneyCatalog journeyCatalog, KnowledgeConfig config) {

        if (journeyCatalog.observed().isEmpty()) {
            return List.of();
        }

        Journey journey = journeyCatalog.observed().get(0);
        List<String> actualSequence = journey.actualNodeKeySequence();

        List<UxFinding> findings = new ArrayList<>();

        for (KnowledgeConfig.JourneyDefinitionConfig journeyConfig : config.journeys()) {

            Integer expectedMaxSteps = journeyConfig.expectedMaxSteps();

            if (expectedMaxSteps == null || !journey.matchedDefinitionKeys().contains(journeyConfig.key())) {
                continue;
            }

            List<String> declaredKeys = journeyConfig.nodeKeys();
            if (declaredKeys.isEmpty()) {
                continue;
            }

            int firstIndex = actualSequence.indexOf(declaredKeys.get(0));
            int lastIndex = actualSequence.lastIndexOf(declaredKeys.get(declaredKeys.size() - 1));

            if (firstIndex < 0 || lastIndex < firstIndex) {
                continue;
            }

            int actualSteps = lastIndex - firstIndex + 1;

            if (actualSteps <= expectedMaxSteps) {
                continue;
            }

            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("expectedMaxSteps", String.valueOf(expectedMaxSteps));
            metadata.put("actualSteps", String.valueOf(actualSteps));

            findings.add(new UxFinding(
                    UxFindingType.NAVIGATION_FRICTION,
                    severityForFriction(actualSteps, expectedMaxSteps),
                    "Journey '" + journeyConfig.name() + "' took " + actualSteps
                            + " distinct screens to complete (expected at most " + expectedMaxSteps + ")",
                    journeyConfig.key(),
                    metadata
            ));
        }

        return findings;
    }

    private FindingSeverity severityForFriction(int actualSteps, int expectedMaxSteps) {

        double ratio = (double) actualSteps / expectedMaxSteps;

        if (ratio > 2.0) {
            return FindingSeverity.HIGH;
        }
        if (ratio > 1.5) {
            return FindingSeverity.MEDIUM;
        }
        return FindingSeverity.LOW;
    }

    /**
     * A structural proxy, not a real accessibility audit: {@link
     * ElementInfo} carries no role/aria-* data anywhere, so this can only
     * flag a visible, enabled, tag-recognized interactive element (button/
     * input/link/select) whose text, name, and id are all blank.
     */
    private List<UxFinding> detectMissingAccessibleNames(List<Observation> observations) {

        List<UxFinding> findings = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (Observation observation : observations) {
            for (ElementInfo element : observation.elements()) {

                if (!element.visible() || !element.enabled()) {
                    continue;
                }

                if (!isBlank(element.text()) || !isBlank(element.name()) || !isBlank(element.id())) {
                    continue;
                }

                String dedupeKey = observation.url() + "|" + element.locator();
                if (!seen.add(dedupeKey)) {
                    continue;
                }

                Map<String, String> metadata = new LinkedHashMap<>();
                metadata.put("tag", element.tag());
                metadata.put("url", observation.url());

                findings.add(new UxFinding(
                        UxFindingType.MISSING_ACCESSIBLE_NAME,
                        FindingSeverity.MEDIUM,
                        "Interactive <" + element.tag() + "> element has no discoverable accessible name",
                        element.locator(),
                        metadata
                ));
            }
        }

        return findings;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
