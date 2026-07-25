package com.aegis.example.plugin;

import com.aegis.core.plugin.CredentialProvider;

/**
 * Worked example of a Stage 2 "Identity Integration" credential provider:
 * resolves an "env:VAR_NAME" reference against a real environment
 * variable, so a value like "env:AEGIS_DEMO_PASSWORD" in
 * {@code application.yml} never needs the real secret in plaintext.
 * Registered via {@code META-INF/services/com.aegis.core.plugin.CredentialProvider}.
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
            throw new IllegalStateException("Environment variable not set: " + variableName);
        }

        return value;
    }
}
