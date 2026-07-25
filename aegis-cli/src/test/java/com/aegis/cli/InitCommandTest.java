package com.aegis.cli;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitCommandTest {

    @Test
    void generatesAStandaloneProjectWithPlaceholdersResolved() throws IOException {

        Path directory = Files.createTempDirectory("aegis-init-test").resolve("my-app");

        try {
            int exitCode = InitCommand.run(new String[] {directory.toString(), "--name", "My App", "--base-url", "https://example.org/"});

            assertEquals(0, exitCode);

            String pom = Files.readString(directory.resolve("pom.xml"));
            assertTrue(pom.contains("<artifactId>my-app</artifactId>"));
            assertTrue(pom.contains("com.aegis.generated.myapp.Main"));
            assertTrue(pom.contains("aegis-api"));

            String yaml = Files.readString(directory.resolve("application.yml"));
            assertTrue(yaml.contains("baseUrl: https://example.org/"));

            Path sourceDir = directory.resolve("src/main/java/com/aegis/generated/myapp");
            assertTrue(Files.exists(sourceDir.resolve("Main.java")));
            assertTrue(Files.exists(sourceDir.resolve("MyAppApplication.java")));

            String applicationClass = Files.readString(sourceDir.resolve("MyAppApplication.java"));
            assertTrue(applicationClass.contains("package com.aegis.generated.myapp;"));
            assertTrue(applicationClass.contains("class MyAppApplication"));
            assertTrue(applicationClass.contains("return \"MyApp\";"));

            assertTrue(Files.exists(directory.resolve("README.md")));

        } finally {
            deleteRecursively(directory);
        }
    }

    @Test
    void derivesAppNameAndPackageFromDirectoryWhenNameNotGiven() throws IOException {

        Path directory = Files.createTempDirectory("aegis-init-test").resolve("swag-labs-checkout");

        try {
            int exitCode = InitCommand.run(new String[] {directory.toString()});

            assertEquals(0, exitCode);
            assertTrue(Files.exists(directory.resolve("src/main/java/com/aegis/generated/swaglabscheckout/SwagLabsCheckoutApplication.java")));

        } finally {
            deleteRecursively(directory);
        }
    }

    @Test
    void refusesToRunIntoANonEmptyDirectory() throws IOException {

        Path directory = Files.createTempDirectory("aegis-init-test");
        Files.writeString(directory.resolve("existing-file.txt"), "not empty");

        try {
            int exitCode = InitCommand.run(new String[] {directory.toString()});
            assertEquals(1, exitCode);
            assertTrue(Files.notExists(directory.resolve("pom.xml")));

        } finally {
            deleteRecursively(directory);
        }
    }

    @Test
    void missingDirectoryArgumentFails() {
        assertEquals(1, InitCommand.run(new String[0]));
    }

    private void deleteRecursively(Path path) throws IOException {

        if (Files.notExists(path)) {
            return;
        }

        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
