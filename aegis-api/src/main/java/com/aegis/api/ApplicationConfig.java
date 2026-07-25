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
}
