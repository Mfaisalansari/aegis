package com.aegis.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvCredentialProviderTest {

    private final EnvCredentialProvider provider = new EnvCredentialProvider();

    @Test
    void supportsValuesWithTheEnvPrefix() {
        assertTrue(provider.supports("env:PATH"));
        assertFalse(provider.supports("plain-value"));
        assertFalse(provider.supports("vault:secret/foo"));
    }

    @Test
    void resolvesARealEnvironmentVariable() {

        // PATH is set in every real process environment.
        String expected = System.getenv("PATH");
        assertEquals(expected, provider.resolve("env:PATH"));
    }

    @Test
    void throwsClearlyWhenTheVariableIsNotSet() {

        String unlikelyVarName = "AEGIS_TEST_DOES_NOT_EXIST_" + System.nanoTime();

        AegisConfigException e = assertThrows(AegisConfigException.class,
                () -> provider.resolve("env:" + unlikelyVarName));

        assertTrue(e.getMessage().contains(unlikelyVarName));
    }
}
