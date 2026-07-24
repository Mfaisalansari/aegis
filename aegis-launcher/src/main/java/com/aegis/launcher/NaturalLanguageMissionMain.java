package com.aegis.launcher;

import com.aegis.core.llm.OpenAiCompatibleChatClient;
import com.aegis.core.mission.LlmMissionParser;
import com.aegis.core.mission.MissionParser;
import com.aegis.model.mission.Mission;

/**
 * Phase 8 "Natural language missions": instead of building a Mission by
 * hand like every other *Main class, this one starts from a plain-
 * English instruction and lets LlmMissionParser extract the starting
 * URL, goal, success condition, and credentials from it.
 *
 * Same AEGIS_LLM_* configuration as LlmSauceDemoMain (see that class for
 * the full list). Falls back to RuleBasedMissionParser (a bare URL
 * regex against the instruction, nothing else) on any LLM failure — so
 * this always produces a runnable Mission, just a less complete one
 * without a reachable model.
 */
public class NaturalLanguageMissionMain {

    public static void main(String[] args) {

        String instruction =
                "Log into https://www.saucedemo.com/ using username \"standard_user\" and password "
                        + "\"secret_sauce\", and confirm you reach the inventory page.";

        MissionParser parser = new LlmMissionParser(OpenAiCompatibleChatClient.fromEnvironment());

        Mission mission = parser.parse(instruction);

        System.out.println("Parsed mission from natural language instruction:");
        System.out.println("  Instruction: " + instruction);
        System.out.println("  Name       : " + mission.name());
        System.out.println("  baseUrl    : " + mission.parameter("baseUrl"));
        System.out.println("  successUrlContains: " + mission.parameter("successUrlContains"));
        System.out.println();

        MissionRunner.run(mission);
    }
}
