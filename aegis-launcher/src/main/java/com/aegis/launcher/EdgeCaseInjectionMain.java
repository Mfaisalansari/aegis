package com.aegis.launcher;

import com.aegis.model.mission.Mission;

import java.util.Map;
import java.util.UUID;

/**
 * Targets the same https://demowebshop.tricentis.com/register form as
 * DemoWebShopMain, but with "inputStrategy": "edge-case" — every field
 * gets a deliberately malformed value (empty, oversized, XSS/SQLi-shaped,
 * malformed email, unicode, ...) instead of realistic registration data.
 *
 * Read the outcome the other way around from a normal mission: FAILED
 * here is the expected, healthy result — it means the site's validation
 * rejected the bad input and registration never completed. SUCCESS would
 * mean garbage data was accepted, which is itself the defect worth
 * reporting. Findings (console/page errors, crashes) are the other thing
 * worth checking either way — a field that throws a raw stack trace to
 * the console instead of a clean validation message is a real bug too.
 */
public class EdgeCaseInjectionMain {

    public static void main(String[] args) {

        Mission mission = new Mission(
                UUID.randomUUID(),
                "Edge-Case Input Injection — Demo Web Shop Registration",
                "Probe the registration form's validation with malformed input instead of real registration data",
                Map.of(
                        "baseUrl", "https://demowebshop.tricentis.com/register",
                        "inputStrategy", "edge-case",
                        "successUrlContains", "registerresult",
                        "maxIterations", "20"
                )
        );

        MissionRunner.run(mission);
    }
}
