package com.aegis.core.plugin;

/**
 * Stage 2 "Identity Integration" — the credential half (see
 * {@link SessionProvider} for the session half): resolves a secret
 * reference into a real value, so plaintext secrets never need to sit in
 * an {@code application.yml} — e.g. a "vault:secret/myapp#username"
 * reference resolved against HashiCorp Vault, or an "env:MY_APP_PASSWORD"
 * reference resolved against an environment variable.
 *
 * Discovered via {@link java.util.ServiceLoader} and applied by
 * {@code AegisConfigLoader} to {@code application.username}/{@code .password}
 * before building {@code ApplicationConfig} — a raw value only gets
 * resolved if some discovered provider's {@link #supports(String)}
 * returns true for it; otherwise it's used literally, exactly as today.
 */
public interface CredentialProvider {

    /** True if this provider recognizes the raw config value (e.g. checks a prefix like "vault:") and can resolve it. */
    boolean supports(String rawValue);

    String resolve(String rawValue);
}
