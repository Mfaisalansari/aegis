package com.aegis.core.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Built-in {@link KnowledgeProvider} for {@link NodeCatalog} — applies
 * {@link KnowledgeConfig}'s {@code nodes:} overrides (matched by {@code
 * urlPattern}, first match in declaration order wins) on top of {@link
 * StateCatalog}'s discovered facts. Every field falls back
 * independently to the auto-naming heuristic when config doesn't
 * declare it — a config entry that only sets {@code displayName} still
 * gets an auto-derived {@code key}/{@code technicalName}.
 */
public final class NodeCatalogProvider implements KnowledgeProvider {

    @Override
    public NodeCatalog provide(KnowledgeBuildContext context) {

        StateCatalog stateCatalog = context.partialBase().require(StateCatalog.class);
        KnowledgeConfig config = context.config() == null ? KnowledgeConfig.empty() : context.config();

        List<Node> nodes = new ArrayList<>();

        for (State state : stateCatalog.states()) {

            KnowledgeConfig.NodeConfig match = firstMatch(config, state.url());

            nodes.add(buildNode(state, match));
        }

        return new NodeCatalog(nodes);
    }

    private KnowledgeConfig.NodeConfig firstMatch(KnowledgeConfig config, String url) {

        for (KnowledgeConfig.NodeConfig candidate : config.nodes()) {
            if (UrlPatternMatcher.matches(candidate.urlPattern(), url)) {
                return candidate;
            }
        }

        return null;
    }

    private Node buildNode(State state, KnowledgeConfig.NodeConfig match) {

        String configuredDisplayName = match == null ? null : blankToNull(match.displayName());
        String configuredTechnicalName = match == null ? null : blankToNull(match.technicalName());
        String configuredKey = match == null ? null : blankToNull(match.key());

        String displayName;
        NameSource nameSource;

        if (configuredDisplayName != null) {
            displayName = configuredDisplayName;
            nameSource = NameSource.CONFIGURED;
        } else if (state.pageTitle() != null && !state.pageTitle().isBlank()) {
            displayName = state.pageTitle();
            nameSource = NameSource.AUTO_TITLE;
        } else {
            displayName = NodeNaming.humanize(state.url());
            nameSource = NameSource.AUTO_URL;
        }

        String technicalName = configuredTechnicalName != null ? configuredTechnicalName : NodeNaming.slug(state.url());
        String key = configuredKey != null ? configuredKey : NodeNaming.slug(state.url());

        List<String> aliases = match == null ? List.of() : match.aliases();
        Map<String, String> metadata = match == null ? Map.of() : match.metadata();

        return new Node(state.id(), key, displayName, technicalName, aliases, nameSource, metadata);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
