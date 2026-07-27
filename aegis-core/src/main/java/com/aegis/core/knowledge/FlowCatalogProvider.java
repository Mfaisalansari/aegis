package com.aegis.core.knowledge;

import java.util.ArrayList;
import java.util.List;

/** Built-in {@link KnowledgeProvider} for {@link FlowCatalog} — resolves each declared flow's node keys against {@link NodeCatalog}. */
public final class FlowCatalogProvider implements KnowledgeProvider {

    @Override
    public FlowCatalog provide(KnowledgeBuildContext context) {

        NodeCatalog nodeCatalog = context.partialBase().require(NodeCatalog.class);
        KnowledgeConfig config = context.config() == null ? KnowledgeConfig.empty() : context.config();

        List<Flow> flows = new ArrayList<>();

        for (KnowledgeConfig.FlowConfig flowConfig : config.flows()) {

            List<String> unmatched = new ArrayList<>();

            for (String nodeKey : flowConfig.nodeKeys()) {
                if (nodeCatalog.byKey(nodeKey).isEmpty()) {
                    unmatched.add(nodeKey);
                }
            }

            flows.add(new Flow(
                    flowConfig.key(),
                    flowConfig.name(),
                    flowConfig.nodeKeys(),
                    flowConfig.description(),
                    unmatched,
                    flowConfig.metadata()
            ));
        }

        return new FlowCatalog(flows);
    }
}
