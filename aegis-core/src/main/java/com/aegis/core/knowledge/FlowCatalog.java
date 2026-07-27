package com.aegis.core.knowledge;

import java.util.List;

/** Every declared business flow — always organization-declared, AEGIS never invents one. */
public record FlowCatalog(List<Flow> flows) implements KnowledgeCatalog {

    public FlowCatalog {
        flows = flows == null ? List.of() : List.copyOf(flows);
    }

    @Override
    public String name() {
        return "flows";
    }
}
