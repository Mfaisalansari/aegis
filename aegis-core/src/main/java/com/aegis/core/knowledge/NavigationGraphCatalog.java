package com.aegis.core.knowledge;

import java.util.List;

/**
 * AEGIS 2.0 Phase 4 — the observed navigation structure of this run:
 * every distinct transition actually taken ({@link #edges()}), every
 * cycle actually walked ({@link #loops()}), and every node visited that
 * the run never found a way onward from ({@link #deadEndNodeKeys()}).
 * Built by {@link NavigationGraphCatalogProvider} from the same raw
 * {@code Observation} history every other catalog in this package reads —
 * this works identically whether the observations came from AEGIS's own
 * autonomous run or an ingested Selenium/Playwright/annotation-driven
 * session (see {@code aegis-observation-api}), since neither needs a live
 * {@code WorldModel}.
 */
public record NavigationGraphCatalog(
        List<NavigationGraphEdge> edges,
        List<NavigationLoop> loops,
        List<String> deadEndNodeKeys
) implements KnowledgeCatalog {

    public NavigationGraphCatalog {
        edges = edges == null ? List.of() : List.copyOf(edges);
        loops = loops == null ? List.of() : List.copyOf(loops);
        deadEndNodeKeys = deadEndNodeKeys == null ? List.of() : List.copyOf(deadEndNodeKeys);
    }

    @Override
    public String name() {
        return "navigation-graph";
    }
}
