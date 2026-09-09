package com.automation.api.auth;

import com.automation.api.config.EnvironmentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Keeps {@code vrgo.search.proxy.guest.header.*} aligned with the active guest bearer JWT.
 * <p>
 * Auth-critical claims ({@code deviceId}, {@code profileId}, etc.) are refreshed from the token
 * after browser recovery. Test overrides such as {@code ottbouquetid} configured in the
 * environment file are restored from the bootstrap snapshot after every guest token refresh.
 */
public final class VrgoGuestAuthHeaderSync {

    private static final Logger LOG = LoggerFactory.getLogger(VrgoGuestAuthHeaderSync.class);

    private static final List<String> BOOTSTRAP_GUEST_HEADER_OVERRIDES = List.of(
            "vrgo.search.proxy.guest.header.ottbouquetid",
            "vrgo.search.proxy.guest.header.entitlements",
            "vrgo.search.proxy.guest.header.entitlementvalues"
    );

    private VrgoGuestAuthHeaderSync() {
    }

    public static void syncGuestSessionHeaders(EnvironmentConfig config, String accessToken) {
        if (config == null || accessToken == null || accessToken.isBlank()) {
            return;
        }

        syncClaim(config, "vrgo.search.proxy.guest.header.device_id", "deviceId", accessToken);
        syncClaim(config, "vrgo.search.proxy.guest.header.profileid", "profileId", accessToken);
        syncClaim(config, "vrgo.search.proxy.guest.header.entitlementhash", "entitlementHash", accessToken);
        syncClaim(config, "vrgo.search.proxy.guest.header.cp_id", "accountId", accessToken);
        syncClaimIfNotBootstrap(config, "vrgo.search.proxy.guest.header.ottbouquetid", "ottBouquetId", accessToken);

        config.restoreBootstrapProperties(BOOTSTRAP_GUEST_HEADER_OVERRIDES);
        LOG.debug("Restored bootstrap guest header overrides after JWT sync");
    }

    private static void syncClaimIfNotBootstrap(
            EnvironmentConfig config,
            String propertyKey,
            String jwtClaim,
            String token
    ) {
        if (config.isBootstrapConfigured(propertyKey)) {
            return;
        }
        syncClaim(config, propertyKey, jwtClaim, token);
    }

    private static void syncClaim(EnvironmentConfig config, String propertyKey, String jwtClaim, String token) {
        String value = VrgoJwtUtils.extractClaimString(token, jwtClaim);
        if (value == null || value.isBlank()) {
            return;
        }
        config.setProperty(propertyKey, value.strip());
        LOG.debug("Synced {} from guest JWT claim {}", propertyKey, jwtClaim);
    }
}
