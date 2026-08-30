package com.aegis.core.knowledge;

import com.aegis.model.observation.Observation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.aegis.core.knowledge.KnowledgeTestFixtures.observation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeBaseBuilderTest {

    @Test
    void standardBuildsAllEightBuiltInCatalogsInDependencyOrder() {

        List<Observation> observations = List.of(observation("https://app/login", "Login", "a"));

        KnowledgeBase base = KnowledgeBaseBuilder.standard().build("Test App", observations, List.of(), KnowledgeConfig.empty());

        assertTrue(base.get(StateCatalog.class).isPresent());
        assertTrue(base.get(NodeCatalog.class).isPresent());
        assertTrue(base.get(FlowCatalog.class).isPresent());
        assertTrue(base.get(JourneyCatalog.class).isPresent());
        assertTrue(base.get(UxFindingCatalog.class).isPresent());
        assertTrue(base.get(InspectionCatalog.class).isPresent());
        assertTrue(base.get(NavigationGraphCatalog.class).isPresent());
        assertTrue(base.get(ExperienceScoreCatalog.class).isPresent());
        assertEquals(1, base.require(StateCatalog.class).states().size());
    }

    // Real ServiceLoader discovery — see TestExtraCatalogProvider,
    // registered via src/test/resources/META-INF/services — not a
    // hand-wired fake standing in for the mechanism.
    @Test
    void discoversThirdPartyProvidersViaServiceLoaderAfterTheBuiltIns() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b")
        );

        KnowledgeBase base = KnowledgeBaseBuilder.standard().build("Test App", observations, List.of(), KnowledgeConfig.empty());

        TestExtraCatalog extra = base.require(TestExtraCatalog.class);
        assertEquals(2, extra.discoveredNodeCountAtBuildTime());
    }

    @Test
    void withProviderAddsAnAdditionalProviderExplicitly() {

        KnowledgeBase base = new KnowledgeBaseBuilder()
                .withProvider(new StateCatalogProvider())
                .withProvider(new NodeCatalogProvider())
                .build("Test App", List.of(observation("https://app/login", "Login", "a")), List.of(), KnowledgeConfig.empty());

        assertTrue(base.get(NodeCatalog.class).isPresent());
        assertTrue(base.get(FlowCatalog.class).isEmpty());
    }
}
