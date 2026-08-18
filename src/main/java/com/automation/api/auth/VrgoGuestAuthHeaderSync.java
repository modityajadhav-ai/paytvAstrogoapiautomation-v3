package com.automation.api.auth;

import com.automation.api.config.EnvironmentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps {@code vrgo.search.proxy.guest.header.*} aligned with the active guest bearer JWT.
 */
public final class VrgoGuestAuthHeaderSync {

    private static final Logger LOG = LoggerFactory.getLogger(VrgoGuestAuthHeaderSync.class);

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
