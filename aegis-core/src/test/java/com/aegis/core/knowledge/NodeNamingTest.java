package com.aegis.core.knowledge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodeNamingTest {

    @Test
    void slugifiesAUrlPath() {
        assertEquals("customers-search", NodeNaming.slug("https://app.example.com/Customers/Search?x=1"));
    }

    @Test
    void slugOfRootPathIsRoot() {
        assertEquals("root", NodeNaming.slug("https://app.example.com/"));
    }

    @Test
    void humanizesAUrlPath() {
        assertEquals("Customers Search", NodeNaming.humanize("https://app.example.com/customers/search"));
    }

    @Test
    void humanizeOfRootPathIsHome() {
        assertEquals("Home", NodeNaming.humanize("https://app.example.com/"));
    }

    @Test
    void malformedUrlDoesNotThrowAndStillProducesASlug() {
        assertEquals("not-a-url", NodeNaming.slug("not a url"));
    }
}
