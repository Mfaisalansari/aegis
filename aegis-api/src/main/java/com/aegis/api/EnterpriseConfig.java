package com.aegis.api;

import java.util.Map;
import java.util.Set;

/**
 * Stage 3 "Enterprise Readiness": a second, additive top-level shape an
 * {@code application.yml}-style file can take — {@code environments:}
 * (where) crossed with {@code missions:} (what), instead of the single
 * flat {@code application:}/{@code browser:}/{@code mission:} shape from
 * Stage 1 (still fully supported, completely unrelated to this).
 *
 * The key design property: {@link #resolve} *produces* an ordinary
 * {@link AegisConfig} — everything downstream ({@link MissionBuilder},
 * {@link AegisApplication}, {@link Launcher}) needs zero changes to
 * consume either shape.
 */
public record EnterpriseConfig(
        Map<String, EnvironmentProfile> environments,
        Map<String, MissionProfile> missions,
        ReportConfig report
) {

    public AegisConfig resolve(String environmentName, String missionName) {

        EnvironmentProfile environment = environments.get(environmentName);
        if (environment == null) {
            throw new AegisConfigException(
                    "Unknown environment: '" + environmentName + "' (known: " + names(environments) + ")");
        }

        MissionProfile mission = missions.get(missionName);
        if (mission == null) {
            throw new AegisConfigException(
                    "Unknown mission: '" + missionName + "' (known: " + names(missions) + ")");
        }

        ApplicationConfig application = ApplicationConfig.merge(environment.application(), mission.applicationOverrides());

        return new AegisConfig(application, environment.browser(), mission.mission(), report);
    }

    private static Set<String> names(Map<String, ?> profiles) {
        return profiles.keySet();
    }
}
