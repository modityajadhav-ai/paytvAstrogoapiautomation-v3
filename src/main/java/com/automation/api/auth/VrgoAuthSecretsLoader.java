package com.automation.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Loads VRGO OAuth secrets from a gitignored local file (never committed to GitLab).
 * <p>
 * Copy {@code secrets/vrgo-auth.local.properties.example} → {@code secrets/vrgo-auth.local.properties}
 * and set {@code vrgo.refresh.token} once after browser login. Jenkins/GitLab CI should use
 * {@code VRGO_REFRESH_TOKEN} instead — no file needed on the server.
 */
public final class VrgoAuthSecretsLoader {

    private static final Logger LOG = LoggerFactory.getLogger(VrgoAuthSecretsLoader.class);

    public static final String LOCAL_SECRETS_PATH = "secrets/vrgo-auth.local.properties";

    private VrgoAuthSecretsLoader() {
    }

    /**
     * Loads local secrets into JVM system properties when the file exists.
     * Does not override values already set via environment variables.
     */
    public static void loadLocalSecretsIfPresent() {
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
        applyIfAbsent("vrgo.refresh.token", props.getProperty("vrgo.refresh.token"), "VRGO_REFRESH_TOKEN");
        applyIfAbsent("vrgo.profile.id", props.getProperty("vrgo.profile.id"), "VRGO_PROFILE_ID");
        applyIfAbsent("vrgo.bearer.token", props.getProperty("vrgo.bearer.token"), "VRGO_BEARER_TOKEN");
        applyIfAbsent("vrgo.auth.username", props.getProperty("vrgo.auth.username"), "VRGO_TEST_USERNAME");
        applyIfAbsent("vrgo.auth.password", props.getProperty("vrgo.auth.password"), "VRGO_TEST_PASSWORD");

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
            LOG.info("Loaded VRGO secrets from {}", path.toAbsolutePath());
        }
    }

    private static void applyIfAbsent(String systemPropertyKey, String fileValue, String envKey) {
        if (fileValue == null || fileValue.isBlank()) {
            return;
        }
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return;
        }
        String existing = System.getProperty(systemPropertyKey);
        if (existing != null && !existing.isBlank()) {
            return;
        }
        System.setProperty(systemPropertyKey, fileValue.strip());
    }

    static Path resolveLocalSecretsPath() {
        String override = firstNonBlank(
                System.getProperty("vrgo.secrets.file"),
                System.getenv("VRGO_SECRETS_FILE")
        );
        if (override != null) {
            return Paths.get(override.strip());
        }
        return Paths.get(LOCAL_SECRETS_PATH);
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
