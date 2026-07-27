package com.aegis.core.knowledge;

/**
 * Test-only {@link KnowledgeCatalog}, registered for real via {@code
 * META-INF/services} (see {@code src/test/resources}) so {@code
 * KnowledgeBaseBuilderTest} exercises the actual {@code ServiceLoader}
 * discovery path a third-party catalog would use — same discipline as
 * the plugin tests elsewhere in this project.
 */
public record TestExtraCatalog(int discoveredNodeCountAtBuildTime) implements KnowledgeCatalog {

    @Override
    public String name() {
        return "test-extra";
    }
}
