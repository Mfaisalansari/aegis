package com.aegis.core.knowledge;

import java.util.List;

/** Declared journey definitions plus what actually happened this run. */
public record JourneyCatalog(List<JourneyDefinition> definitions, List<Journey> observed) implements KnowledgeCatalog {

    public JourneyCatalog {
        definitions = definitions == null ? List.of() : List.copyOf(definitions);
        observed = observed == null ? List.of() : List.copyOf(observed);
    }

    @Override
    public String name() {
        return "journeys";
    }
}
