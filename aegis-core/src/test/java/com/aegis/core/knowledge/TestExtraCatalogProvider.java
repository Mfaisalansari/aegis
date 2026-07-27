package com.aegis.core.knowledge;

/**
 * Test-only {@link KnowledgeProvider}, discovered via {@code
 * ServiceLoader} — proves a third-party catalog can read the built-in
 * catalogs ({@code partialBase}) that ran before it, without
 * {@link KnowledgeBase} ever needing to know this type exists.
 */
public class TestExtraCatalogProvider implements KnowledgeProvider {

    @Override
    public TestExtraCatalog provide(KnowledgeBuildContext context) {
        return new TestExtraCatalog(context.partialBase().require(NodeCatalog.class).nodes().size());
    }
}
