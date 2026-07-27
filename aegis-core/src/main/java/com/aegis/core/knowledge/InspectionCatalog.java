package com.aegis.core.knowledge;

import java.util.List;

/** Every {@link InspectionFinding} this run produced, built by {@link InspectionCheckProvider}. */
public record InspectionCatalog(List<InspectionFinding> findings) implements KnowledgeCatalog {

    public InspectionCatalog {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    @Override
    public String name() {
        return "page-inspection";
    }
}
