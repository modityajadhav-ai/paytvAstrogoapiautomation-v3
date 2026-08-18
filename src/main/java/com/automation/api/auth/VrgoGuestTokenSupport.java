package com.automation.api.auth;

import com.automation.api.config.EnvironmentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the guest-user bearer JWT for pub/guest API flows.
 * <p>
 * Guest sessions do not use {@code POST /v1/auth/token} refresh (subscriber login only).
 * When {@link VrgoGuestTokenHolder} is initialized it reuses a valid cached access JWT (~300s)
 * or captures a fresh one via headless browser guest browse.
 */
public final class VrgoGuestTokenSupport {

    private static final Logger LOG = LoggerFactory.getLogger(VrgoGuestTokenSupport.class);

    private static final String GUEST_ACCESS_PROPERTY = "vrgo.search.proxy.guest.bearer.token";

    private VrgoGuestTokenSupport() {
    }

    /**
     * Guest bearer JWT without {@code Bearer} prefix, or {@code null} when not configured.
     */
    public static String getGuestBearerToken(EnvironmentConfig config) {
        if (VrgoGuestTokenHolder.isInitialized()) {
            try {
                return stripBearer(VrgoGuestTokenHolder.getInstance().getBearerToken());
            } catch (IllegalStateException e) {
                LOG.warn("Guest token holder could not provide bearer token: {}", e.getMessage());
            }
        }
        String token = resolveStaticGuestAccessToken();
        if (token == null || token.isBlank()) {
            return null;
        }
        String normalized = stripBearer(token);
        if (VrgoJwtUtils.isExpiringSoon(normalized, resolveRefreshBufferSeconds(config))) {
            LOG.error(
                    "Guest bearer token in secrets/env is expired or expiring soon (~{}s left). "
                            + "Paste a fresh guest JWT from Browse as Guest, or fix guest browser recovery.",
                    VrgoJwtUtils.secondsRemaining(normalized)
            );
            return null;
        }
        warnIfIssuerMismatch(config, normalized);
        return normalized;
    }

    private static long resolveRefreshBufferSeconds(EnvironmentConfig config) {
        String value = firstNonBlank(
                System.getProperty("vrgo.auth.refresh.buffer.seconds"),
                System.getenv("VRGO_REFRESH_BUFFER_SECONDS"),
                config != null ? config.getProperty("vrgo.auth.refresh.buffer.seconds") : null
        );
        if (value == null || value.isBlank()) {
            return 90L;
        }
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException e) {
            return 90L;
        }
    }

    /**
     * Guest token when configured; otherwise the logged-in bearer from {@link VrgoAuthSupport}.
     */
    public static String getGuestBearerTokenOrLoggedIn(EnvironmentConfig config) {
        String guest = getGuestBearerToken(config);
        if (guest != null && !guest.isBlank()) {
            return guest;
        }
        return VrgoAuthSupport.getBearerToken(config);
    }

    public static boolean hasGuestBearerToken() {
        if (VrgoGuestTokenHolder.isInitialized() && VrgoGuestTokenHolder.getInstance().canProvideBearerToken()) {
            return true;
        }
        return resolveStaticGuestAccessToken() != null
                || VrgoGuestBrowserAuthSupport.isConfigured();
    }

    public static boolean canBootstrapGuestAuth(EnvironmentConfig config) {
        return hasGuestBearerToken();
    }

    private static String resolveStaticGuestAccessToken() {
        return firstNonBlank(
                System.getProperty(GUEST_ACCESS_PROPERTY),
                VrgoAuthSecretsLoader.resolveEnvironmentVariable("VRGO_GUEST_BEARER_TOKEN"),
                System.getenv("VRGO_GUEST_BEARER_TOKEN")
        );
    }

    private static void warnIfIssuerMismatch(EnvironmentConfig config, String token) {
        if (config == null || token == null || token.isBlank()) {
            return;
        }
        String issuer = VrgoJwtUtils.extractClaimString(token, "iss");
        String expected = config.getProperty("vrgo.auth.token.issuer");
        if (expected == null || expected.isBlank() || issuer == null || issuer.isBlank()) {
            return;
        }
        if (!expected.strip().equalsIgnoreCase(issuer.strip())) {
            LOG.error(
                    "Guest bearer token issuer '{}' does not match active environment issuer '{}' "
                            + "(env={}). Paste a fresh guest Bearer JWT from browser — APIs may return #ERR-000-003.",
                    issuer,
                    expected.strip(),
                    com.automation.api.config.Environment.current().name().toLowerCase()
            );
        }
    }

    private static String stripBearer(String token) {
        String t = token.strip();
        if (t.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return t.substring(7).strip();
        }
        return t;
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
