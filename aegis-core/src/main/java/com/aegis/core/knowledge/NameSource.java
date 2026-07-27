package com.aegis.core.knowledge;

/**
 * Where a {@link Node}'s name came from — kept on every node so a
 * report reader can always tell organization-declared meaning apart
 * from AEGIS's own mechanical guess. Never hidden, per this layer's
 * governing rule: AEGIS discovers, the organization decides what things
 * mean.
 */
public enum NameSource {

    /** Declared explicitly in {@code knowledge.yml} by a human. */
    CONFIGURED,

    /** No config match — defaulted from the page's real {@code <title>}. */
    AUTO_TITLE,

    /** No config match and no usable page title — defaulted from the URL path. */
    AUTO_URL
}
