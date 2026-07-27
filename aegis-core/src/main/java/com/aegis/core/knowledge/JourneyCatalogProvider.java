package com.aegis.core.knowledge;

import com.aegis.core.reasoning.memory.StateSignature;
import com.aegis.model.observation.Observation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Built-in {@link KnowledgeProvider} for {@link JourneyCatalog}.
 * {@code observed} is deliberately one whole-mission trace per run for
 * v1 — segmenting a run into multiple sub-journeys would mean AEGIS
 * guessing where one journey ends and another begins, which is exactly
 * the kind of invented business meaning this layer avoids. Future work,
 * not this slice.
 */
public final class JourneyCatalogProvider implements KnowledgeProvider {

    @Override
    public JourneyCatalog provide(KnowledgeBuildContext context) {

        StateCatalog stateCatalog = context.partialBase().require(StateCatalog.class);
        NodeCatalog nodeCatalog = context.partialBase().require(NodeCatalog.class);
        KnowledgeConfig config = context.config() == null ? KnowledgeConfig.empty() : context.config();

        Map<String, String> keyByStateSignature = new HashMap<>();
        for (State state : stateCatalog.states()) {
            nodeCatalog.byStateId(state.id()).ifPresent(node -> keyByStateSignature.put(state.stateSignature(), node.key()));
        }

        List<String> actualSequence = deriveActualSequence(context.observations(), keyByStateSignature);

        List<JourneyDefinition> definitions = new ArrayList<>();

        for (KnowledgeConfig.JourneyDefinitionConfig journeyConfig : config.journeys()) {

            List<String> unmatched = new ArrayList<>();

            for (String nodeKey : journeyConfig.nodeKeys()) {
                if (nodeCatalog.byKey(nodeKey).isEmpty()) {
                    unmatched.add(nodeKey);
                }
            }

            definitions.add(new JourneyDefinition(
                    journeyConfig.key(),
                    journeyConfig.name(),
                    journeyConfig.nodeKeys(),
                    journeyConfig.description(),
                    unmatched,
                    journeyConfig.metadata()
            ));
        }

        List<Journey> observed = new ArrayList<>();

        if (!actualSequence.isEmpty()) {

            List<String> matchedDefinitionKeys = new ArrayList<>();

            for (JourneyDefinition definition : definitions) {
                if (isSubsequence(definition.nodeKeys(), actualSequence)) {
                    matchedDefinitionKeys.add(definition.key());
                }
            }

            observed.add(new Journey(actualSequence, matchedDefinitionKeys));
        }

        return new JourneyCatalog(definitions, observed);
    }

    /** The chronological sequence of distinct node keys visited this run, first-occurrence order. */
    private List<String> deriveActualSequence(List<Observation> observations, Map<String, String> keyByStateSignature) {

        Set<String> seen = new LinkedHashSet<>();

        for (Observation observation : observations) {

            String signature = StateSignature.of(observation);
            String key = keyByStateSignature.get(signature);

            if (key != null) {
                seen.add(key);
            }
        }

        return List.copyOf(seen);
    }

    /** True if every key in definitionKeys appears in actualSequence, in the same relative order (not necessarily contiguous). */
    private boolean isSubsequence(List<String> definitionKeys, List<String> actualSequence) {

        if (definitionKeys.isEmpty()) {
            return false;
        }

        int position = 0;

        for (String key : actualSequence) {
            if (position < definitionKeys.size() && definitionKeys.get(position).equals(key)) {
                position++;
            }
        }

        return position == definitionKeys.size();
    }
}
