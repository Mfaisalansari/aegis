package com.aegis.sample.nopcommerce;

import com.aegis.api.AegisApplication;
import com.aegis.api.AegisConfig;
import com.aegis.api.AegisConfigLoader;

import java.nio.file.Path;

public class NopCommerceApplication implements AegisApplication {

    @Override
    public String name() {
        return "nopCommerce";
    }

    @Override
    public AegisConfig config() {
        return AegisConfigLoader.load(Path.of("application.yml"));
    }
}
