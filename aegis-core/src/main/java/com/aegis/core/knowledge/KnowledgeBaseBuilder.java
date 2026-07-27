package com.aegis.core.knowledge;

import com.aegis.model.action.Action;
import com.aegis.model.observation.Observation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Orchestrates a set of {@link KnowledgeProvider}s into a {@link
 * KnowledgeBase}. {@link #standard()} registers the 6 built-in
 * catalogs, in dependency order (state → node → flow → journey → ux
 * quality → page inspection — each later one reads an earlier one out of
 * the in-progress base). Third parties add their own catalogs via
 * {@link #withProvider} or by registering a {@code
 * META-INF/services/com.aegis.core.knowledge.KnowledgeProvider} file —
 * same discovery mechanism as Stage 2's plugins.
 */
public final class KnowledgeBaseBuilder {

    private final List<KnowledgeProvider> providers = new ArrayList<>();

    public static KnowledgeBaseBuilder standard() {
        return new KnowledgeBaseBuilder()
                .withProvider(new StateCatalogProvider())
                .withProvider(new NodeCatalogProvider())
                .withProvider(new FlowCatalogProvider())
                .withProvider(new JourneyCatalogProvider())
                .withProvider(new UxAnalysisCatalogProvider())
                .withProvider(new InspectionCheckProvider());
    }

    public KnowledgeBaseBuilder withProvider(KnowledgeProvider provider) {
        providers.add(provider);
        return this;
    }

    public KnowledgeBase build(String applicationName, List<Observation> observations, List<Action> actions, KnowledgeConfig config) {
        return build(applicationName, observations, actions, config, SignalLog.empty());
    }

    /**
     * Same as the 4-arg {@link #build}, plus whatever {@code
     * SignalRecorder} captured live during the run (Page Inspection
     * Layer) — pass {@link SignalLog#empty()} (what the 4-arg overload
     * above does) when inspection capture was disabled.
     */
    public KnowledgeBase build(
            String applicationName, List<Observation> observations, List<Action> actions,
            KnowledgeConfig config, SignalLog signals) {

        Map<Class<? extends KnowledgeCatalog>, KnowledgeCatalog> catalogs = new LinkedHashMap<>();

        for (KnowledgeProvider provider : providers) {
            runProvider(provider, applicationName, observations, actions, config, signals, catalogs);
        }

        for (KnowledgeProvider provider : ServiceLoader.load(KnowledgeProvider.class)) {
            runProvider(provider, applicationName, observations, actions, config, signals, catalogs);
        }

        return new KnowledgeBase(catalogs);
    }

    private void runProvider(
            KnowledgeProvider provider, String applicationName, List<Observation> observations,
            List<Action> actions, KnowledgeConfig config, SignalLog signals,
            Map<Class<? extends KnowledgeCatalog>, KnowledgeCatalog> catalogs) {

        KnowledgeBase partialBase = new KnowledgeBase(catalogs);
        KnowledgeBuildContext buildContext = new KnowledgeBuildContext(applicationName, observations, actions, config, partialBase, signals);
        KnowledgeCatalog catalog = provider.provide(buildContext);

        catalogs.put(catalog.getClass(), catalog);
    }
}
