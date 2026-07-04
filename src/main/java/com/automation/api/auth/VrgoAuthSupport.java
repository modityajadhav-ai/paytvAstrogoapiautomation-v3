package com.automation.api.auth;

import com.automation.api.config.EnvironmentConfig;

/**
 * Central bearer resolution for all VRGO API clients.
 */
public final class VrgoAuthSupport {

    private VrgoAuthSupport() {
    }

    /**
     * Returns a valid bearer JWT (without {@code Bearer} prefix).
     * Uses {@link VrgoTokenHolder} when initialized; otherwise falls back to static env / system properties.
     */
    public static String getBearerToken(EnvironmentConfig config) {
        if (VrgoTokenHolder.isInitialized()) {
            return VrgoTokenHolder.getInstance().getBearerToken();
        }
        return firstNonBlank(
                System.getProperty("vrgo.bearer.token"),
                System.getenv("VRGO_BEARER_TOKEN")
        );
    }

    public static boolean hasBearerCredential(EnvironmentConfig config) {
        if (VrgoTokenHolder.isInitialized()) {
            return VrgoTokenHolder.getInstance().canProvideBearerToken();
        }
        String token = firstNonBlank(
                System.getProperty("vrgo.bearer.token"),
                System.getenv("VRGO_BEARER_TOKEN")
        );
        return token != null && !token.isBlank();
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
