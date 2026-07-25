package com.aegis.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * {@code aegis init <directory> [--name <appName>] [--base-url <url>]}
 *
 * Scaffolds a standalone starter project — a plain Maven module whose
 * only AEGIS dependency is {@code aegis-api}, copying the exact
 * {@code AegisApplication}/{@code Launcher.run(...)} shape
 * {@code samples/sample-saucedemo} already uses. This doubles as the
 * "IDE templates" deliverable (Stage 4): the generated project needs no
 * IDE-specific plugin or archetype, so a separate one was deliberately
 * not built — open the generated directory directly in IntelliJ/VS
 * Code/etc, same as any of AEGIS's own {@code samples/*} modules.
 */
final class InitCommand {

    private static final String AEGIS_VERSION = "1.0.0-SNAPSHOT";

    private InitCommand() {
    }

    static int run(String[] args) {

        if (args.length == 0) {
            return fail("Missing required <directory>");
        }

        String directoryArg = args[0];
        String appNameArg = null;
        String baseUrl = "https://example.com/";

        int i = 1;
        while (i < args.length) {

            String flag = args[i];

            if (!flag.equals("--name") && !flag.equals("--base-url")) {
                return fail("Unknown flag: " + flag);
            }

            if (i + 1 >= args.length) {
                return fail("Missing value for " + flag);
            }

            String value = args[i + 1];

            switch (flag) {
                case "--name" -> appNameArg = value;
                case "--base-url" -> baseUrl = value;
            }

            i += 2;
        }

        Path directory = Path.of(directoryArg);

        try {
            if (Files.exists(directory)) {
                if (!Files.isDirectory(directory)) {
                    return fail("Path exists and is not a directory: " + directory);
                }
                try (var listing = Files.list(directory)) {
                    if (listing.findAny().isPresent()) {
                        return fail("Directory already exists and is not empty: " + directory);
                    }
                }
            }
        } catch (IOException e) {
            return fail("Could not inspect directory: " + directory + " (" + e.getMessage() + ")");
        }

        String rawName = appNameArg != null ? appNameArg : directory.getFileName().toString();
        String appName = toClassName(rawName);
        String artifactId = toArtifactId(rawName);
        String packageName = "com.aegis.generated." + appName.toLowerCase(Locale.ROOT);
        String packagePath = packageName.replace('.', '/');
        Path sourceDir = directory.resolve("src/main/java").resolve(packagePath);

        try {
            Files.createDirectories(sourceDir);

            writeFromTemplate("pom.xml.template", directory.resolve("pom.xml"), appName, artifactId, packageName, baseUrl);
            writeFromTemplate("application.yml.template", directory.resolve("application.yml"), appName, artifactId, packageName, baseUrl);
            writeFromTemplate("README.md.template", directory.resolve("README.md"), appName, artifactId, packageName, baseUrl);
            writeFromTemplate("Main.java.template", sourceDir.resolve("Main.java"), appName, artifactId, packageName, baseUrl);
            writeFromTemplate("Application.java.template", sourceDir.resolve(appName + "Application.java"), appName, artifactId, packageName, baseUrl);

        } catch (IOException e) {
            return fail("Failed to write generated project: " + e.getMessage());
        }

        System.out.println("Created " + appName + " in " + directory.toAbsolutePath());
        System.out.println();
        System.out.println("Next steps:");
        System.out.println("  cd " + directoryArg);
        System.out.println("  # edit application.yml, then:");
        System.out.println("  mvn compile exec:java");

        return 0;
    }

    private static void writeFromTemplate(String templateName, Path destination,
                                           String appName, String artifactId, String packageName, String baseUrl) throws IOException {

        String content = readTemplate(templateName)
                .replace("{{APP_NAME}}", appName)
                .replace("{{ARTIFACT_ID}}", artifactId)
                .replace("{{PACKAGE}}", packageName)
                .replace("{{BASE_URL}}", baseUrl)
                .replace("{{AEGIS_VERSION}}", AEGIS_VERSION);

        Files.writeString(destination, content, StandardCharsets.UTF_8);
    }

    private static String readTemplate(String name) throws IOException {

        try (InputStream in = InitCommand.class.getResourceAsStream("/templates/starter-app/" + name)) {

            if (in == null) {
                throw new IOException("Missing bundled template: " + name);
            }

            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String toClassName(String rawName) {

        String[] words = rawName.split("[^A-Za-z0-9]+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }

        return result.isEmpty() ? "App" : result.toString();
    }

    private static String toArtifactId(String rawName) {

        String kebab = rawName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        return kebab.isEmpty() ? "aegis-app" : kebab;
    }

    private static int fail(String message) {
        System.err.println("Error: " + message);
        printUsage();
        return 1;
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  aegis init <directory> [--name <appName>] [--base-url <url>]

                Scaffolds a standalone Maven project depending only on aegis-api —
                open it directly in your IDE, no archetype/plugin needed. Refuses to
                run if <directory> already exists and is not empty.
                """);
    }
}
