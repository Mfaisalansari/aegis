package com.aegis.api;

import com.aegis.core.plugin.CredentialProvider;

/**
 * Test-only {@link CredentialProvider}, registered for real via
 * {@code META-INF/services} (see {@code src/test/resources}) so
 * {@code AegisConfigLoaderTest} exercises the actual ServiceLoader
 * discovery path rather than a hand-wired fake standing in for it.
 * Recognizes "test-prefix:X" and resolves it to X uppercased.
 */
public class TestUppercaseCredentialProvider implements CredentialProvider {

    private static final String PREFIX = "test-prefix:";

    @Override
    public boolean supports(String rawValue) {
        return rawValue.startsWith(PREFIX);
    }

    @Override
    public String resolve(String rawValue) {
        return rawValue.substring(PREFIX.length()).toUpperCase();
    }
}
