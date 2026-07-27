package com.aegis.core.knowledge;

import java.util.List;
import java.util.Optional;

/** Human-named dictionary over {@link StateCatalog}'s discovered facts. */
public record NodeCatalog(List<Node> nodes) implements KnowledgeCatalog {

    public NodeCatalog {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    @Override
    public String name() {
        return "nodes";
    }

    public Optional<Node> byKey(String key) {
        return nodes.stream().filter(n -> n.key().equals(key)).findFirst();
    }

    public Optional<Node> byStateId(String stateId) {
        return nodes.stream().filter(n -> n.stateId().equals(stateId)).findFirst();
    }
}
