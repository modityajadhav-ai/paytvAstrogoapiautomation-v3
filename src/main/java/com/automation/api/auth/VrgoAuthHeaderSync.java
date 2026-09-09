package com.automation.api.auth;

import com.automation.api.config.EnvironmentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Keeps {@code vrgo.header.*} values aligned with the active bearer JWT.
 * Static environment files often contain stale {@code device_id} / {@code entitlementhash}
 * from an old browser session — the API returns {@code Invalid credentials} when they mismatch.
 * <p>
 * Test overrides such as {@code isentitlementenabled} and {@code ottbouquetid} configured in the
 * environment file are restored from the bootstrap snapshot after every token refresh.
 */
public final class VrgoAuthHeaderSync {

    private static final Logger LOG = LoggerFactory.getLogger(VrgoAuthHeaderSync.class);

    private static final List<String> BOOTSTRAP_HEADER_OVERRIDES = List.of(
            "vrgo.header.isentitlementenabled",
            "vrgo.header.ottbouquetid",
            "vrgo.header.entitlements",
            "vrgo.header.entitlementvalues"
    );

    private VrgoAuthHeaderSync() {
    }

    public static void syncSessionHeaders(EnvironmentConfig config, String accessToken) {
        if (config == null || accessToken == null || accessToken.isBlank()) {
            return;
        }

        syncClaim(config, "vrgo.header.device_id", "deviceId", accessToken);
        syncClaim(config, "vrgo.header.profileid", "profileId", accessToken);
        syncClaim(config, "vrgo.header.entitlementhash", "entitlementHash", accessToken);
        syncClaim(config, "vrgo.header.cp_id", "accountId", accessToken);
        syncClaim(config, "vrgo.header.profiletype", "profileType", accessToken);
        syncClaimIfNotBootstrap(config, "vrgo.header.ottbouquetid", "ottBouquetId", accessToken);

        config.restoreBootstrapProperties(BOOTSTRAP_HEADER_OVERRIDES);
        LOG.debug("Restored bootstrap header overrides after JWT sync");
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
        String normalized = "profileType".equals(jwtClaim) ? value.toUpperCase() : value;
        config.setProperty(propertyKey, normalized);
        LOG.debug("Synced {} from JWT claim {}", propertyKey, jwtClaim);
    }
}
