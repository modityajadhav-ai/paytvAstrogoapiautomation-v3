package com.automation.api.auth;

import com.automation.api.config.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Loads VRGO OAuth secrets for the active {@link Environment}.
 * <p>
 * Local files (gitignored, one per stack):
 * <ul>
 *   <li>{@code secrets/vrgo-auth.test.local.properties}</li>
 *   <li>{@code secrets/vrgo-auth.load.local.properties}</li>
 *   <li>… {@code dev}, {@code stage}, {@code stage2}, {@code prod}</li>
 * </ul>
 * Falls back to legacy {@code secrets/vrgo-auth.local.properties} when the env-specific file is absent.
 * <p>
 * CI: set {@code VRGO_REFRESH_TOKEN} (or {@code VRGO_REFRESH_TOKEN_LOAD}, etc.) and optional
 * {@code VRGO_AUTH_USERNAME} / {@code VRGO_AUTH_PASSWORD} with the same per-env suffix.
 */
public final class VrgoAuthSecretsLoader {

    private static final Logger LOG = LoggerFactory.getLogger(VrgoAuthSecretsLoader.class);

    /** Legacy single-file path; still supported as fallback (especially for {@code test}). */
    public static final String LOCAL_SECRETS_PATH = "secrets/vrgo-auth.local.properties";

    private VrgoAuthSecretsLoader() {
    }

    /**
     * Applies OS env vars, then loads the active environment's local secrets file into system properties.
     * Does not override values already set via {@code -D} or environment variables.
     */
    public static void loadLocalSecretsIfPresent() {
        applyEnvironmentSecrets();

        Path path = resolveLocalSecretsPath();
        if (!Files.isRegularFile(path)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            LOG.warn("Could not read {}: {}", path, e.getMessage());
            return;
        }
        applyIfAbsent("vrgo.refresh.token", props.getProperty("vrgo.refresh.token"));
        applyIfAbsent("vrgo.profile.id", props.getProperty("vrgo.profile.id"));
        applyIfAbsent("vrgo.bearer.token", props.getProperty("vrgo.bearer.token"));
        applyGuestBearerTokenIfValid(path, props.getProperty("vrgo.search.proxy.guest.bearer.token"));
        applyIfAbsent("vrgo.auth.username", props.getProperty("vrgo.auth.username"));
        applyIfAbsent("vrgo.auth.password", props.getProperty("vrgo.auth.password"));
        applyIfAbsent("vrgo.web.basic.auth.username", props.getProperty("vrgo.web.basic.auth.username"));
        applyIfAbsent("vrgo.web.basic.auth.password", props.getProperty("vrgo.web.basic.auth.password"));
        applyIfAbsent("vrgo.auth.browser.recovery.enabled", props.getProperty("vrgo.auth.browser.recovery.enabled"));
        applyIfAbsent("vrgo.auth.browser.evict.device.on.limit", props.getProperty("vrgo.auth.browser.evict.device.on.limit"));
        applyIfAbsent("vrgo.token.generator.entitlements", props.getProperty("vrgo.token.generator.entitlements"));

        String refreshInFile = props.getProperty("vrgo.refresh.token");
        if (refreshInFile == null || refreshInFile.isBlank()) {
            LOG.warn(
                    "{} exists but vrgo.refresh.token is empty. Paste your refresh_token on line: vrgo.refresh.token=eyJ...",
                    path.toAbsolutePath()
            );
        } else if (VrgoJwtUtils.hasProfileId(refreshInFile) && !VrgoJwtUtils.isRefreshTokenJwt(refreshInFile)) {
            LOG.warn(
                    "vrgo.refresh.token looks like an access token (has profileId). "
                            + "Copy refresh_token from /v1/auth/token Response, not the Bearer access token."
            );
        } else {
            LOG.info("Loaded VRGO secrets for {} from {}", Environment.current().name().toLowerCase(), path.toAbsolutePath());
        }
    }

    /**
     * Resolves {@code VRGO_*_<ENV>} first, then generic {@code VRGO_*}.
     */
    public static String resolveEnvironmentVariable(String baseName) {
        Environment env = Environment.current();
        String envSpecific = System.getenv(baseName + "_" + env.name());
        if (envSpecific != null && !envSpecific.isBlank()) {
            return envSpecific.strip();
        }
        String generic = System.getenv(baseName);
        if (generic != null && !generic.isBlank()) {
            return generic.strip();
        }
        return null;
    }

    static Path resolveLocalSecretsPath() {
        String override = firstNonBlank(
                System.getProperty("vrgo.secrets.file"),
                System.getenv("VRGO_SECRETS_FILE")
        );
        if (override != null) {
            return Paths.get(override.strip());
        }

        Path envSpecific = Paths.get(environmentSecretsPath());
        if (Files.isRegularFile(envSpecific)) {
            return envSpecific;
        }
        return Paths.get(LOCAL_SECRETS_PATH);
    }

    /** Classpath-relative path for the active environment's secrets file. */
    public static String environmentSecretsPath() {
        return "secrets/vrgo-auth." + Environment.current().name().toLowerCase() + ".local.properties";
    }

    private static void applyEnvironmentSecrets() {
        applyEnvToProperty("vrgo.refresh.token", "VRGO_REFRESH_TOKEN");
        applyEnvToProperty("vrgo.profile.id", "VRGO_PROFILE_ID");
        applyEnvToProperty("vrgo.bearer.token", "VRGO_BEARER_TOKEN");
        applyGuestBearerEnvToProperty();
        applyEnvToProperty("vrgo.auth.username", "VRGO_AUTH_USERNAME", "VRGO_TEST_USERNAME");
        applyEnvToProperty("vrgo.auth.password", "VRGO_AUTH_PASSWORD", "VRGO_TEST_PASSWORD");
        applyEnvToProperty("vrgo.web.basic.auth.username", "VRGO_WEB_BASIC_AUTH_USERNAME");
        applyEnvToProperty("vrgo.web.basic.auth.password", "VRGO_WEB_BASIC_AUTH_PASSWORD");
    }

    private static void applyGuestBearerEnvToProperty() {
        String existing = System.getProperty("vrgo.search.proxy.guest.bearer.token");
        if (existing != null && !existing.isBlank()) {
            return;
        }
        String value = resolveEnvironmentVariable("VRGO_GUEST_BEARER_TOKEN");
        if (value == null || value.isBlank()) {
            return;
        }
        String token = value.strip();
        if (VrgoJwtUtils.isExpiringSoon(token, 90L)) {
            LOG.warn(
                    "Ignoring expired VRGO_GUEST_BEARER_TOKEN — guest browser recovery will capture a fresh token."
            );
            return;
        }
        System.setProperty("vrgo.search.proxy.guest.bearer.token", token);
    }

    private static void applyEnvToProperty(String systemPropertyKey, String... envKeys) {
        String existing = System.getProperty(systemPropertyKey);
        if (existing != null && !existing.isBlank()) {
            return;
        }
        for (String envKey : envKeys) {
            String value = resolveEnvironmentVariable(envKey);
            if (value != null) {
                System.setProperty(systemPropertyKey, value);
                return;
            }
        }
    }

    private static void applyGuestBearerTokenIfValid(Path secretsPath, String fileValue) {
        if (fileValue == null || fileValue.isBlank()) {
            return;
        }
        String token = fileValue.strip();
        if (VrgoJwtUtils.isExpiringSoon(token, 90L)) {
            LOG.warn(
                    "Ignoring expired vrgo.search.proxy.guest.bearer.token in {} — "
                            + "guest browser recovery will capture a fresh token (~300s TTL).",
                    secretsPath.toAbsolutePath()
            );
            return;
        }
        applyIfAbsent("vrgo.search.proxy.guest.bearer.token", token);
    }

    private static void applyIfAbsent(String systemPropertyKey, String fileValue) {
        if (fileValue == null || fileValue.isBlank()) {
            return;
        }
        String existing = System.getProperty(systemPropertyKey);
        if (existing != null && !existing.isBlank()) {
            return;
        }
        System.setProperty(systemPropertyKey, fileValue.strip());
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return null;
    }
}
