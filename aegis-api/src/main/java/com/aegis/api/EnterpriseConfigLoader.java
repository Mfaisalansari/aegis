package com.aegis.api;

import com.aegis.core.plugin.CredentialProvider;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the Stage 3 {@code environments:}/{@code missions:} shape into
 * an {@link EnterpriseConfig} — same hand-mapped-tree approach as
 * {@link AegisConfigLoader} (reuses its section/field helpers directly,
 * package-private for exactly this), not SnakeYAML's POJO binding.
 *
 * A file with neither an {@code environments:} nor a {@code missions:}
 * top-level key isn't a valid enterprise config — use
 * {@link AegisConfigLoader#load} for the plain single-mission shape
 * instead. {@link #isEnterpriseShaped} distinguishes the two without
 * needing to parse twice.
 */
public final class EnterpriseConfigLoader {

    private EnterpriseConfigLoader() {
    }

    /** True if the given file's top level has an {@code environments:} or {@code missions:} key — the Stage 3 shape, not Stage 1's. */
    public static boolean isEnterpriseShaped(Path path) {

        try (InputStream in = Files.newInputStream(path)) {

            Object parsed = new Yaml().load(in);

            if (!(parsed instanceof Map<?, ?> root)) {
                return false;
            }

            return root.containsKey("environments") || root.containsKey("missions");

        } catch (IOException e) {
            throw new AegisConfigException("Failed to read config file: " + path, e);
        }
    }

    public static EnterpriseConfig load(Path path) {

        try (InputStream in = Files.newInputStream(path)) {
            return load(in);
        } catch (IOException e) {
            throw new AegisConfigException("Failed to read config file: " + path, e);
        }
    }

    public static EnterpriseConfig load(InputStream in) {

        Object parsed;

        try {
            parsed = new Yaml().load(in);
        } catch (RuntimeException e) {
            throw new AegisConfigException("Failed to parse YAML config", e);
        }

        if (!(parsed instanceof Map<?, ?> root)) {
            throw new AegisConfigException("Config file must contain a YAML mapping at the top level");
        }

        List<CredentialProvider> credentialProviders = AegisConfigLoader.discoverCredentialProviders();

        Map<String, EnvironmentProfile> environments = new LinkedHashMap<>();
        Object environmentsRaw = root.get("environments");
        if (environmentsRaw instanceof Map<?, ?> environmentsMap) {
            for (Map.Entry<?, ?> entry : environmentsMap.entrySet()) {
                environments.put(String.valueOf(entry.getKey()), readEnvironmentProfile(entry.getValue(), credentialProviders));
            }
        }

        Map<String, MissionProfile> missions = new LinkedHashMap<>();
        Object missionsRaw = root.get("missions");
        if (missionsRaw instanceof Map<?, ?> missionsMap) {
            for (Map.Entry<?, ?> entry : missionsMap.entrySet()) {
                missions.put(String.valueOf(entry.getKey()), readMissionProfile(entry.getValue(), credentialProviders));
            }
        }

        ReportConfig report = AegisConfigLoader.readReport(AegisConfigLoader.section(root, "report"));

        return new EnterpriseConfig(environments, missions, report);
    }

    private static EnvironmentProfile readEnvironmentProfile(Object rawValue, List<CredentialProvider> credentialProviders) {

        Map<?, ?> section = rawValue instanceof Map<?, ?> map ? map : Map.of();

        return new EnvironmentProfile(
                AegisConfigLoader.readApplication(AegisConfigLoader.section(section, "application"), credentialProviders),
                AegisConfigLoader.readBrowser(AegisConfigLoader.section(section, "browser"))
        );
    }

    private static MissionProfile readMissionProfile(Object rawValue, List<CredentialProvider> credentialProviders) {

        Map<?, ?> section = rawValue instanceof Map<?, ?> map ? map : Map.of();
        Map<?, ?> applicationSection = AegisConfigLoader.section(section, "application");

        ApplicationConfig applicationOverrides = applicationSection.isEmpty()
                ? null
                : AegisConfigLoader.readApplication(applicationSection, credentialProviders);

        return new MissionProfile(
                AegisConfigLoader.readMission(AegisConfigLoader.section(section, "mission")),
                applicationOverrides
        );
    }
}
