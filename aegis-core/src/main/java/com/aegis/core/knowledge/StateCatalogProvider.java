package com.aegis.core.knowledge;

import com.aegis.core.reasoning.memory.StateSignature;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Built-in {@link KnowledgeProvider} for {@link StateCatalog} — the
 * first catalog built every time, everything else depends on it.
 * Deliberately reuses the exact same {@code StateSignature.of(...)} +
 * first-seen-order dedup that {@code MissionReportData.from(...)}
 * already uses to build its own {@code states} list, so this catalog's
 * node identity agrees with what the existing reports already show
 * (see ARCHITECTURE.md's Knowledge Enrichment Layer section for why
 * this reads from raw Observations rather than the live WorldModel).
 */
public final class StateCatalogProvider implements KnowledgeProvider {

    @Override
    public StateCatalog provide(KnowledgeBuildContext context) {

        Map<String, Observation> firstObservationByState = new LinkedHashMap<>();

        for (Observation observation : context.observations()) {
            firstObservationByState.putIfAbsent(StateSignature.of(observation), observation);
        }

        List<State> states = new ArrayList<>();
        Set<String> distinctLocators = new LinkedHashSet<>();

        int index = 1;

        for (Map.Entry<String, Observation> entry : firstObservationByState.entrySet()) {

            String signature = entry.getKey();
            Observation observation = entry.getValue();

            for (ElementInfo element : observation.elements()) {
                distinctLocators.add(element.locator());
            }

            states.add(new State(
                    "S" + index,
                    signature,
                    observation.url(),
                    observation.pageTitle(),
                    observation.elements().size(),
                    Map.of()
            ));

            index++;
        }

        return new StateCatalog(states, distinctLocators.size());
    }
}
