package com.aegis.api;

/**
 * The "application" section of an {@code application.yml} — declarative
 * facts about the app under test. Maps directly onto existing mission
 * parameters (see USAGE.md §4); nothing here is new capability.
 */
public record ApplicationConfig(String baseUrl, String username, String password, String successUrlContains) {

    public static ApplicationConfig defaults() {
        return new ApplicationConfig(null, null, null, null);
    }

    /**
     * Stage 3 "mission profile" support: layers {@code override} on top of
     * {@code base} field-by-field — each non-null field in {@code override}
     * wins, otherwise {@code base}'s value carries through. {@code override}
     * may be null (no override at all), in which case {@code base} is
     * returned unchanged.
     */
    public static ApplicationConfig merge(ApplicationConfig base, ApplicationConfig override) {

        if (override == null) {
            return base;
        }

        return new ApplicationConfig(
                override.baseUrl() != null ? override.baseUrl() : base.baseUrl(),
                override.username() != null ? override.username() : base.username(),
                override.password() != null ? override.password() : base.password(),
                override.successUrlContains() != null ? override.successUrlContains() : base.successUrlContains()
        );
    }
}
