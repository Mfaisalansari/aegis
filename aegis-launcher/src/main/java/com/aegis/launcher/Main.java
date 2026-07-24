package com.aegis.launcher;

import com.aegis.model.mission.Mission;

import java.util.Map;
import java.util.UUID;

public class Main {

    public static void main(String[] args) {

        Mission mission = new Mission(
                UUID.randomUUID(),
                "Validate Login",
                "Login using valid credentials",
                Map.of(
                        "baseUrl", "https://the-internet.herokuapp.com/login",
                        "username", "tomsmith",
                        "password", "SuperSecretPassword!",
                        "successUrlContains", "/secure"
                )
        );

        MissionRunner.run(mission);
    }
}
