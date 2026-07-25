package com.aegis.sample.saucedemo;

import com.aegis.api.AegisApplication;
import com.aegis.api.AegisConfig;
import com.aegis.api.AegisConfigLoader;

import java.nio.file.Path;

/**
 * Everything application-specific for this sample lives in
 * {@code application.yml} — this class just points at it. Copy this
 * shape for your own application: rename the class, point {@link #name()}
 * at your app, and edit application.yml.
 */
public class SauceDemoApplication implements AegisApplication {

    @Override
    public String name() {
        return "SauceDemo";
    }

    @Override
    public AegisConfig config() {
        return AegisConfigLoader.load(Path.of("application.yml"));
    }
}
