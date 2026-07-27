package com.aegis.launcher;

import com.aegis.api.KnowledgeConfigLoader;
import com.aegis.core.Aegis;
import com.aegis.core.AegisReport;
import com.aegis.core.browser.BrowserConfig;
import com.aegis.core.knowledge.KnowledgeBase;
import com.aegis.core.knowledge.KnowledgeBaseBuilder;
import com.aegis.core.knowledge.KnowledgeBaseTextRenderer;
import com.aegis.core.knowledge.KnowledgeConfig;
import com.aegis.model.mission.Mission;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Runs a real mission, then builds and prints the Knowledge Enrichment
 * Layer's "World Model Summary" for it — the fastest way to see the
 * state/node/flow/journey catalogs against a real site. See USAGE.md
 * §8e for the full explanation.
 *
 * Drop a {@code knowledge.yml} in the working directory to see
 * organization-declared names/flows/journeys override the auto-naming
 * default; without one, every node falls back to its real page title
 * (or the URL if the title isn't usable).
 */
public class WorldModelDemoMain {

    public static void main(String[] args) throws Exception {

        Mission mission = new Mission(
                UUID.randomUUID(),
                "Login to Sauce Demo",
                "Login to saucedemo.com with valid credentials",
                Map.of(
                        "baseUrl", "https://www.saucedemo.com/",
                        "username", "standard_user",
                        "password", "secret_sauce",
                        "successUrlContains", "inventory.html"
                )
        );

        AegisReport report = Aegis.run(mission, BrowserConfig.defaults());

        System.out.println("Mission status: " + report.status());
        System.out.println();

        var executionState = report.missionResult().context().getExecutionState();

        Path knowledgeConfigPath = Path.of("knowledge.yml");
        boolean hasKnowledgeConfig = Files.exists(knowledgeConfigPath);

        KnowledgeConfig config = hasKnowledgeConfig
                ? KnowledgeConfigLoader.load(knowledgeConfigPath)
                : KnowledgeConfig.empty();

        if (!hasKnowledgeConfig) {
            System.out.println("(no knowledge.yml found in the working directory — every name below is auto-generated)");
            System.out.println();
        }

        KnowledgeBase knowledgeBase = KnowledgeBaseBuilder.standard()
                .build("SauceDemo", executionState.getObservations(), executionState.getActions(), config);

        System.out.println(new KnowledgeBaseTextRenderer().render("SauceDemo", knowledgeBase));
    }
}
