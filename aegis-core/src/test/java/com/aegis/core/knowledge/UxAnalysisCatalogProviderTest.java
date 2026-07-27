package com.aegis.core.knowledge;

import com.aegis.model.finding.FindingSeverity;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.aegis.core.knowledge.KnowledgeTestFixtures.observation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UxAnalysisCatalogProviderTest {

    // --- Backtracking ---

    @Test
    void twoNonAdjacentVisitsProduceALowSeverityBacktrackingFinding() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b"),
                observation("https://app/payment", "Payment", "c"),
                observation("https://app/login", "Login", "a")
        );

        List<UxFinding> findings = findingsOfType(buildUxCatalog(observations, KnowledgeConfig.empty()), UxFindingType.BACKTRACKING);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.LOW, findings.get(0).severity());
        assertEquals("login", findings.get(0).evidence());
        assertEquals("2", findings.get(0).metadata().get("visitCount"));
        assertTrue(!findings.get(0).metadata().containsKey("immediateBacktrack"));
    }

    @Test
    void threeNonAdjacentVisitsEscalateToMediumSeverity() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b"),
                observation("https://app/payment", "Payment", "c"),
                observation("https://app/extra1", "Extra1", "d"),
                observation("https://app/login", "Login", "a"),
                observation("https://app/checkout", "Checkout", "e"),
                observation("https://app/extra2", "Extra2", "f"),
                observation("https://app/wishlist", "Wishlist", "g"),
                observation("https://app/login", "Login", "a")
        );

        List<UxFinding> findings = findingsOfType(buildUxCatalog(observations, KnowledgeConfig.empty()), UxFindingType.BACKTRACKING);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.MEDIUM, findings.get(0).severity());
        assertEquals("3", findings.get(0).metadata().get("visitCount"));
    }

    @Test
    void immediateBackAndForthEscalatesSeverityAndTagsMetadata() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b"),
                observation("https://app/login", "Login", "a")
        );

        List<UxFinding> findings = findingsOfType(buildUxCatalog(observations, KnowledgeConfig.empty()), UxFindingType.BACKTRACKING);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.MEDIUM, findings.get(0).severity());
        assertEquals("true", findings.get(0).metadata().get("immediateBacktrack"));
    }

    @Test
    void noRevisitsProducesNoBacktrackingFindings() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b"),
                observation("https://app/payment", "Payment", "c")
        );

        assertTrue(findingsOfType(buildUxCatalog(observations, KnowledgeConfig.empty()), UxFindingType.BACKTRACKING).isEmpty());
    }

    // --- Journey divergence ---

    @Test
    void divergedJourneyReportsMissingNodeKeys() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b")
        );

        KnowledgeConfig config = new KnowledgeConfig(1, List.of(), List.of(), List.of(
                new KnowledgeConfig.JourneyDefinitionConfig("onboarding", "Onboarding", List.of("login", "payment"), "desc", Map.of(), null)
        ), null);

        List<UxFinding> findings = findingsOfType(buildUxCatalog(observations, config), UxFindingType.JOURNEY_DIVERGENCE);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.HIGH, findings.get(0).severity());
        assertTrue(findings.get(0).metadata().get("missingNodeKeys").contains("payment"));
    }

    @Test
    void divergedJourneyReportsOutOfOrderNodeKeys() {

        List<Observation> observations = List.of(
                observation("https://app/payment", "Payment", "a"),
                observation("https://app/login", "Login", "b")
        );

        KnowledgeConfig config = new KnowledgeConfig(1, List.of(), List.of(), List.of(
                new KnowledgeConfig.JourneyDefinitionConfig("onboarding", "Onboarding", List.of("login", "payment"), "desc", Map.of(), null)
        ), null);

        List<UxFinding> findings = findingsOfType(buildUxCatalog(observations, config), UxFindingType.JOURNEY_DIVERGENCE);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.MEDIUM, findings.get(0).severity());
        assertEquals("", findings.get(0).metadata().get("missingNodeKeys"));
        assertTrue(findings.get(0).metadata().get("outOfOrderNodeKeys").contains("payment"));
    }

    @Test
    void matchedJourneyProducesNoDivergenceFinding() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b"),
                observation("https://app/payment", "Payment", "c")
        );

        KnowledgeConfig config = new KnowledgeConfig(1, List.of(), List.of(), List.of(
                new KnowledgeConfig.JourneyDefinitionConfig("onboarding", "Onboarding", List.of("login", "payment"), "desc", Map.of(), null)
        ), null);

        assertTrue(findingsOfType(buildUxCatalog(observations, config), UxFindingType.JOURNEY_DIVERGENCE).isEmpty());
    }

    @Test
    void noObservedJourneyProducesNoFindingsAtAll() {

        List<UxFinding> findings = buildUxCatalog(List.of(), KnowledgeConfig.empty()).findings();

        assertTrue(findings.isEmpty());
    }

    // --- Navigation friction ---

    @Test
    void exceedingExpectedMaxStepsProducesANavigationFrictionFinding() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b"),
                observation("https://app/extra", "Extra", "c"),
                observation("https://app/payment", "Payment", "d")
        );

        KnowledgeConfig config = new KnowledgeConfig(1, List.of(), List.of(), List.of(
                new KnowledgeConfig.JourneyDefinitionConfig("onboarding", "Onboarding", List.of("login", "payment"), "desc", Map.of(), 2)
        ), null);

        List<UxFinding> findings = findingsOfType(buildUxCatalog(observations, config), UxFindingType.NAVIGATION_FRICTION);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.MEDIUM, findings.get(0).severity());
        assertEquals("2", findings.get(0).metadata().get("expectedMaxSteps"));
        assertEquals("4", findings.get(0).metadata().get("actualSteps"));
    }

    @Test
    void withinExpectedMaxStepsProducesNoFrictionFinding() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b"),
                observation("https://app/payment", "Payment", "c")
        );

        KnowledgeConfig config = new KnowledgeConfig(1, List.of(), List.of(), List.of(
                new KnowledgeConfig.JourneyDefinitionConfig("onboarding", "Onboarding", List.of("login", "payment"), "desc", Map.of(), 10)
        ), null);

        assertTrue(findingsOfType(buildUxCatalog(observations, config), UxFindingType.NAVIGATION_FRICTION).isEmpty());
    }

    @Test
    void noExpectedMaxStepsDeclaredProducesNoFrictionFinding() {

        List<Observation> observations = List.of(
                observation("https://app/login", "Login", "a"),
                observation("https://app/dashboard", "Dashboard", "b"),
                observation("https://app/extra", "Extra", "c"),
                observation("https://app/payment", "Payment", "d")
        );

        KnowledgeConfig config = new KnowledgeConfig(1, List.of(), List.of(), List.of(
                new KnowledgeConfig.JourneyDefinitionConfig("onboarding", "Onboarding", List.of("login", "payment"), "desc", Map.of(), null)
        ), null);

        assertTrue(findingsOfType(buildUxCatalog(observations, config), UxFindingType.NAVIGATION_FRICTION).isEmpty());
    }

    // --- Missing accessible name ---

    @Test
    void blankAccessibleNameOnVisibleEnabledElementProducesAFinding() {

        Observation obs = elementObservation("https://app/login", "Login", null, null, null, true, true);

        List<UxFinding> findings = findingsOfType(buildUxCatalog(List.of(obs), KnowledgeConfig.empty()), UxFindingType.MISSING_ACCESSIBLE_NAME);

        assertEquals(1, findings.size());
        assertEquals("login-button", findings.get(0).evidence());
    }

    @Test
    void elementWithATextLabelProducesNoFinding() {

        Observation obs = elementObservation("https://app/login", "Login", "Log In", null, null, true, true);

        assertTrue(findingsOfType(buildUxCatalog(List.of(obs), KnowledgeConfig.empty()), UxFindingType.MISSING_ACCESSIBLE_NAME).isEmpty());
    }

    @Test
    void invisibleBlankElementProducesNoFinding() {

        Observation obs = elementObservation("https://app/login", "Login", null, null, null, false, true);

        assertTrue(findingsOfType(buildUxCatalog(List.of(obs), KnowledgeConfig.empty()), UxFindingType.MISSING_ACCESSIBLE_NAME).isEmpty());
    }

    @Test
    void disabledBlankElementProducesNoFinding() {

        Observation obs = elementObservation("https://app/login", "Login", null, null, null, true, false);

        assertTrue(findingsOfType(buildUxCatalog(List.of(obs), KnowledgeConfig.empty()), UxFindingType.MISSING_ACCESSIBLE_NAME).isEmpty());
    }

    @Test
    void sameOffendingElementAcrossRepeatedObservationsIsReportedOnce() {

        Observation obs = elementObservation("https://app/login", "Login", null, null, null, true, true);

        List<UxFinding> findings = findingsOfType(
                buildUxCatalog(List.of(obs, obs, obs), KnowledgeConfig.empty()), UxFindingType.MISSING_ACCESSIBLE_NAME);

        assertEquals(1, findings.size());
    }

    private Observation elementObservation(
            String url, String pageTitle, String text, String name, String id, boolean visible, boolean enabled) {

        List<ElementInfo> elements = List.of(new ElementInfo("button", id, name, text, "button", null, visible, enabled, "login-button"));
        return new Observation(url, pageTitle, elements, elements, List.of(), List.of(), List.of(), Instant.now());
    }

    private List<UxFinding> findingsOfType(UxFindingCatalog catalog, UxFindingType type) {
        return catalog.findings().stream().filter(f -> f.type() == type).toList();
    }

    private UxFindingCatalog buildUxCatalog(List<Observation> observations, KnowledgeConfig config) {

        StateCatalog stateCatalog = new StateCatalogProvider().provide(
                new KnowledgeBuildContext("Test", observations, List.of(), config, new KnowledgeBase(Map.of()), null));

        NodeCatalog nodeCatalog = new NodeCatalogProvider().provide(
                new KnowledgeBuildContext("Test", observations, List.of(), config, new KnowledgeBase(Map.of(StateCatalog.class, stateCatalog)), null));

        JourneyCatalog journeyCatalog = new JourneyCatalogProvider().provide(
                new KnowledgeBuildContext("Test", observations, List.of(), config,
                        new KnowledgeBase(Map.of(StateCatalog.class, stateCatalog, NodeCatalog.class, nodeCatalog)), null));

        KnowledgeBase partial = new KnowledgeBase(Map.of(
                StateCatalog.class, stateCatalog, NodeCatalog.class, nodeCatalog, JourneyCatalog.class, journeyCatalog));

        return new UxAnalysisCatalogProvider().provide(new KnowledgeBuildContext("Test", observations, List.of(), config, partial, null));
    }
}
