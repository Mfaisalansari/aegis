package com.aegis.api;

import com.aegis.core.browser.BrowserConfig;
import com.aegis.core.plugin.CredentialProvider;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Parses an {@code application.yml} into an {@link AegisConfig}. Loads
 * into a generic {@code Map}/{@code List} tree and hand-maps fields —
 * same "no reflection-binding magic" approach already used for JSON via
 * Jackson's tree API in {@code JsonReportGenerator} — rather than
 * SnakeYAML's POJO {@code Constructor} binding.
 *
 * Every field is optional: a missing section or key falls back to
 * {@link AegisConfig#defaults()}'s value for that field, so a minimal
 * file with just {@code application.baseUrl} set is a valid config.
 */
public final class AegisConfigLoader {

    private AegisConfigLoader() {
    }

    public static AegisConfig load(Path path) {

        try (InputStream in = Files.newInputStream(path)) {
            return load(in);
        } catch (IOException e) {
            throw new AegisConfigException("Failed to read config file: " + path, e);
        }
    }

    public static AegisConfig load(InputStream in) {

        Object parsed;

        try {
            parsed = new Yaml().load(in);
        } catch (RuntimeException e) {
            throw new AegisConfigException("Failed to parse YAML config", e);
        }

        // An empty document (blank/comments-only file) parses to null —
        // treated as "nothing set," same as every other missing field,
        // not an error. Anything else that isn't a mapping (a bare list
        // or scalar at the top level) is a real structural mistake.
        if (parsed == null) {
            return AegisConfig.defaults();
        }

        if (!(parsed instanceof Map<?, ?> root)) {
            throw new AegisConfigException("Config file must contain a YAML mapping at the top level");
        }

        List<CredentialProvider> credentialProviders = discoverCredentialProviders();

        return new AegisConfig(
                readApplication(section(root, "application"), credentialProviders),
                readBrowser(section(root, "browser")),
                readMission(section(root, "mission")),
                readReport(section(root, "report"))
        );
    }

    /** Package-private (not private): reused by {@link EnterpriseConfigLoader} for the environments:/missions: shape. */
    static Map<?, ?> section(Map<?, ?> root, String key) {
        Object value = root.get(key);
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    static List<CredentialProvider> discoverCredentialProviders() {
        List<CredentialProvider> credentialProviders = new ArrayList<>();
        ServiceLoader.load(CredentialProvider.class).forEach(credentialProviders::add);
        return credentialProviders;
    }

    static ApplicationConfig readApplication(Map<?, ?> section, List<CredentialProvider> credentialProviders) {
        return new ApplicationConfig(
                string(section, "baseUrl"),
                resolveCredential(string(section, "username"), credentialProviders),
                resolveCredential(string(section, "password"), credentialProviders),
                string(section, "successUrlContains")
        );
    }

    /**
     * Stage 2 "Identity Integration" (credential half): a raw config
     * value only gets resolved if some discovered {@link CredentialProvider}
     * recognizes it (e.g. a "vault:..."/"env:..." prefix) — otherwise
     * it's used literally, exactly as before this existed.
     */
    private static String resolveCredential(String rawValue, List<CredentialProvider> providers) {

        if (rawValue == null) {
            return null;
        }

        for (CredentialProvider provider : providers) {
            if (provider.supports(rawValue)) {
                return provider.resolve(rawValue);
            }
        }

        return rawValue;
    }

    static BrowserConfig readBrowser(Map<?, ?> section) {
        // BrowserConfig's own compact constructor already normalizes a
        // null/blank type to "chromium" — only headless needs a fallback
        // here, since it's a primitive that can't carry "unset" itself.
        return new BrowserConfig(string(section, "type"), boolOr(section, "headless", false));
    }

    static ReportConfig readReport(Map<?, ?> section) {
        // ReportConfig's own compact constructor already normalizes a
        // null/blank directory to "reports" — nothing extra needed here.
        return new ReportConfig(string(section, "directory"));
    }

    static MissionConfig readMission(Map<?, ?> section) {

        MissionConfig defaults = MissionConfig.defaults();

        return new MissionConfig(
                stringOr(section, "name", defaults.name()),
                stringOr(section, "description", defaults.description()),
                string(section, "strategy"),
                integer(section, "maxIterations"),
                string(section, "inputStrategy"),
                boolOr(section, "interruptions", false),
                boolOr(section, "doubleClicks", false),
                boolOr(section, "raceConditions", false)
        );
    }

    static String string(Map<?, ?> section, String key) {
        Object value = section.get(key);
        return value == null ? null : String.valueOf(value);
    }

    static String stringOr(Map<?, ?> section, String key, String fallback) {
        String value = string(section, key);
        return value != null ? value : fallback;
    }

    static boolean boolOr(Map<?, ?> section, String key, boolean fallback) {
        Object value = section.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    static Integer integer(Map<?, ?> section, String key) {
        Object value = section.get(key);
        return value instanceof Number number ? number.intValue() : null;
    }
}
