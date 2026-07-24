package com.aegis.launcher;

import com.aegis.model.mission.Mission;

import java.util.Map;
import java.util.UUID;

/**
 * Same target and credentials as SauceDemoMain, but with
 * "explorationStrategy": "llm" — every decision (which candidate to try
 * next) is made by LlmActionScorer instead of the default greedy
 * heuristic. See OpenAiCompatibleChatClient for how to point this at a
 * real model:
 *
 *   AEGIS_LLM_BASE_URL   default http://localhost:11434/v1 (Ollama)
 *   AEGIS_LLM_MODEL      default llama3.1
 *   AEGIS_LLM_API_KEY    only needed for a real hosted provider
 *
 * With Ollama running locally (`ollama serve` + `ollama pull llama3.1`),
 * this runs with no extra configuration. Falls back to
 * HighestConfidenceActionScorer on any LLM failure, so a model that's
 * slow, unreachable, or returns garbage degrades the run instead of
 * crashing it — check the console log for "LLM scorer failed" if that
 * happens.
 */
public class LlmSauceDemoMain {

    public static void main(String[] args) {

        Mission mission = new Mission(
                UUID.randomUUID(),
                "Login to Sauce Demo (LLM strategy)",
                "Login to saucedemo.com with valid credentials, using the LLM-backed exploration strategy",
                Map.of(
                        "baseUrl", "https://www.saucedemo.com/",
                        "username", "standard_user",
                        "password", "secret_sauce",
                        "successUrlContains", "inventory.html",
                        "explorationStrategy", "llm"
                )
        );

        MissionRunner.run(mission);
    }
}
