package com.aegis.launcher;

import com.aegis.model.mission.Mission;

import java.util.Map;
import java.util.UUID;

/**
 * Targets https://demowebshop.tricentis.com/register.
 *
 * Verified separately (real HTTP POST, not guessed) that this exact
 * field set — Gender, FirstName, LastName, Email, Password,
 * ConfirmPassword — passes the site's server-side validation and
 * redirects to a URL containing "registerresult" on success.
 *
 * No "username" mission parameter is set on purpose: DefaultInputValueResolver
 * then generates a fresh timestamped email each run, so repeated runs
 * don't collide with "email already registered".
 */
public class DemoWebShopMain {

    public static void main(String[] args) {

        Mission mission = new Mission(
                UUID.randomUUID(),
                "Register on Demo Web Shop",
                "Register a new account on demowebshop.tricentis.com",
                Map.of(
                        "baseUrl", "https://demowebshop.tricentis.com/register",
                        "password", "Aegis!2345",
                        "successUrlContains", "registerresult",
                        "maxIterations", "20",
                       "explorationStrategy", "llm"
                )
        );

        MissionRunner.run(mission);
    }
}
