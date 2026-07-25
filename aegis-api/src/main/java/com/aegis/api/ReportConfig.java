package com.aegis.api;

/**
 * The "report" section of an {@code application.yml} — where to write the
 * three generated report files. {@code directory} is resolved relative
 * to the JVM's working directory, same convention {@code Launcher} always
 * used before this was configurable.
 */
public record ReportConfig(String directory) {

    private static final String DEFAULT_DIRECTORY = "reports";

    public static ReportConfig defaults() {
        return new ReportConfig(DEFAULT_DIRECTORY);
    }

    public ReportConfig {
        if (directory == null || directory.isBlank()) {
            directory = DEFAULT_DIRECTORY;
        }
    }
}
