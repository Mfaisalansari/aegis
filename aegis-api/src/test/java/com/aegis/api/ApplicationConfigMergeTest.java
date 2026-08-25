package com.aegis.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ApplicationConfigMergeTest {

    private final ApplicationConfig base =
            new ApplicationConfig("https://example.com", "base-user", "base-pass", "base-success", "base-context.md");

    @Test
    void nullOverrideReturnsBaseUnchanged() {
        assertSame(base, ApplicationConfig.merge(base, null));
    }

    @Test
    void nonNullOverrideFieldsWin() {

        ApplicationConfig override = new ApplicationConfig(null, "override-user", null, null, null);
        ApplicationConfig merged = ApplicationConfig.merge(base, override);

        assertEquals("https://example.com", merged.baseUrl());
        assertEquals("override-user", merged.username());
        assertEquals("base-pass", merged.password());
        assertEquals("base-success", merged.successUrlContains());
        assertEquals("base-context.md", merged.contextFile());
    }

    @Test
    void fullyPopulatedOverrideReplacesEveryField() {

        ApplicationConfig override =
                new ApplicationConfig("https://override.com", "u", "p", "s", "override-context.md");
        ApplicationConfig merged = ApplicationConfig.merge(base, override);

        assertEquals(override, merged);
    }
}
