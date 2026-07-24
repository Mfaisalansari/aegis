package com.aegis.launcher;

import com.aegis.model.mission.Mission;

import java.util.Map;
import java.util.UUID;

/**
 * Targets https://www.saucedemo.com/ — the login page itself is the site
 * root, with fields #user-name / #password and a #login-button submit.
 * "standard_user" / "secret_sauce" are the credentials Sauce Labs
 * publishes on the login page itself for this demo site. A successful
 * login redirects to a URL containing "inventory.html".
 */
public class SauceDemoMain {

    public static void main(String[] args) {

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

        MissionRunner.run(mission);
    }
}
