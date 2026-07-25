package com.aegis.api;

import com.aegis.core.plugin.CredentialProvider;

/**
 * Stage 3 "secret management": ships as a real, built-in part of
 * {@code aegis-api} (registered via this module's own
 * {@code META-INF/services}) rather than only existing as a worked
 * example — every consumer gets "env:VAR_NAME" resolution in
 * {@code application.yml} for free, no extra plugin jar needed.
 * Resolves an "env:VAR_NAME" reference against a real environment
 * variable, so a real secret never needs to sit in plaintext YAML.
 *
 * {@code examples/plugin-example}'s own copy of this same idea stays
 * where it is — that one exists to demonstrate the plugin mechanism
 * itself, this one exists to actually be used.
 */
public class EnvCredentialProvider implements CredentialProvider {

    private static final String PREFIX = "env:";

    @Override
    public boolean supports(String rawValue) {
        return rawValue.startsWith(PREFIX);
    }

    @Override
    public String resolve(String rawValue) {

        String variableName = rawValue.substring(PREFIX.length());
        String value = System.getenv(variableName);

        if (value == null) {
            throw new AegisConfigException("Environment variable not set: " + variableName);
        }

        return value;
    }
}
