package com.aegis.core.knowledge;

import java.util.List;

/** Every {@link UxFinding} this run produced, built by {@link UxAnalysisCatalogProvider}. */
public record UxFindingCatalog(List<UxFinding> findings) implements KnowledgeCatalog {

    public UxFindingCatalog {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    @Override
    public String name() {
        return "ux-quality";
    }
}
