package com.aegis.sample.orangehrm;

import com.aegis.api.AegisApplication;
import com.aegis.api.AegisConfig;
import com.aegis.api.AegisConfigLoader;

import java.nio.file.Path;

public class OrangeHrmApplication implements AegisApplication {

    @Override
    public String name() {
        return "OrangeHRM";
    }

    @Override
    public AegisConfig config() {
        return AegisConfigLoader.load(Path.of("application.yml"));
    }
}
