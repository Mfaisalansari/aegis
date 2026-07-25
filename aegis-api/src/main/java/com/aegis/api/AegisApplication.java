package com.aegis.api;

import com.aegis.model.mission.Mission;

/**
 * The one type a consumer implements to run AEGIS against their own app —
 * "application-specific pieces" means declarative facts (base URL,
 * credentials, success condition — see {@link ApplicationConfig}), not
 * scripted steps. AEGIS still discovers and drives the actual login form
 * itself through normal autonomous exploration; nothing here changes that.
 */
public interface AegisApplication {

    /** A short identifying name for console output — e.g. "SauceDemo". Distinct from the mission's own name/description. */
    String name();

    AegisConfig config();

    /** Built from {@link #config()} via {@link MissionBuilder#from(AegisConfig)}. Override only for cases the config shape can't express. */
    default Mission mission() {
        return MissionBuilder.from(config()).build();
    }
}
