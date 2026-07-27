package com.aegis.core.knowledge;

import java.util.List;
import java.util.Optional;

/**
 * Every distinct application state discovered this run, in
 * first-discovery order. The facts layer everything else in this
 * package builds on — no naming, no business meaning, just what AEGIS
 * actually observed.
 *
 * {@code distinctComponentCount} is computed once by {@link
 * StateCatalogProvider} from the real per-observation element locators
 * (deduplicated globally, so a shared header/footer across many pages
 * isn't double-counted) — it can't be correctly re-derived from {@link
 * State#elementCount()} alone after the fact, since that's a per-state
 * count with unavoidable overlap between states.
 */
public record StateCatalog(List<State> states, int distinctComponentCount) implements KnowledgeCatalog {

    public StateCatalog {
        states = states == null ? List.of() : List.copyOf(states);
    }

    @Override
    public String name() {
        return "states";
    }

    public Optional<State> byId(String id) {
        return states.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    public int discoveredScreenCount() {
        return states.size();
    }
}
