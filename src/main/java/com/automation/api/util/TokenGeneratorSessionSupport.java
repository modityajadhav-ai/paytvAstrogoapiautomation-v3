package com.automation.api.util;

import com.automation.api.auth.VrgoTokenHolder;
import com.automation.api.config.EnvironmentConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aligns token-generator {@code sessionInfo} with the active bearer JWT.
 * {@code deviceId} / {@code deviceFamilyId} and session ids are taken from the JWT so the body
 * matches {@code device_id} and other synced headers. Entitlements come from
 * {@code vrgo.token.generator.entitlements}, then {@code vrgo.header.entitlements}, then the
 * session-info resource file.
 */
public final class TokenGeneratorSessionSupport {

    private static final ObjectMapper MAPPER = JsonUtils.mapper();

    private TokenGeneratorSessionSupport() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> enrich(Map<String, Object> sessionInfo, EnvironmentConfig config) {
        Map<String, Object> enriched = new LinkedHashMap<>(sessionInfo);

        Map<String, Object> claims = parseJwtClaims(resolveBearerToken());
        if (claims != null) {
            Object loginSession = claims.get("loginSession");
            if (loginSession != null && !loginSession.toString().isBlank()) {
                enriched.put("sessionId", loginSession.toString());
            }

            Map<String, Object> profile = (Map<String, Object>) enriched.computeIfAbsent(
                    "profile", key -> new LinkedHashMap<>()
            );
            Object profileSessionId = claims.get("profileSessionId");
            if (profileSessionId != null && !profileSessionId.toString().isBlank()) {
                profile.put("profileSessionId", profileSessionId.toString());
            }
            Object profileId = claims.get("profileId");
            if (profileId != null && !profileId.toString().isBlank()) {
                profile.put("profileId", profileId.toString());
            }

            Map<String, Object> account = (Map<String, Object>) enriched.computeIfAbsent(
                    "account", key -> new LinkedHashMap<>()
            );
            Object accountId = claims.get("accountId");
            if (accountId != null && !accountId.toString().isBlank()) {
                account.put("accountId", accountId.toString());
            }

            Map<String, Object> device = (Map<String, Object>) enriched.computeIfAbsent(
                    "device", key -> new LinkedHashMap<>()
            );
            Object deviceId = claims.get("deviceId");
            if (deviceId != null && !deviceId.toString().isBlank()) {
                device.put("deviceId", deviceId.toString());
            }
            Object deviceFamilyId = claims.get("deviceFamilyId");
            if (deviceFamilyId != null && !deviceFamilyId.toString().isBlank()) {
                device.put("deviceFamilyId", deviceFamilyId.toString());
            }
        }

        applyConfiguredEntitlements(enriched, config);
        return enriched;
    }

    private static void applyConfiguredEntitlements(Map<String, Object> enriched, EnvironmentConfig config) {
        String entitlementsJson = firstNonBlank(
                System.getProperty("vrgo.token.generator.entitlements"),
                config.getProperty("vrgo.token.generator.entitlements")
        );
        if (entitlementsJson == null || entitlementsJson.isBlank()) {
            return;
        }
        try {
            JsonNode entitlements = MAPPER.readTree(entitlementsJson.strip());
            enriched.put("entitlements", MAPPER.convertValue(entitlements, Object.class));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse vrgo.token.generator.entitlements: " + e.getMessage(), e
            );
        }
    }

    public static String entitlementHashFromBearer() {
        Map<String, Object> claims = parseJwtClaims(resolveBearerToken());
        if (claims == null) {
            return null;
        }
        Object hash = claims.get("entitlementHash");
        return hash != null ? hash.toString() : null;
    }

    /**
     * Returns a skip reason when the active JWT was not issued for the static session-info snapshot.
     */
    public static String describeSessionMismatch(EnvironmentConfig config) {
        String jwtHash = entitlementHashFromBearer();
        if (jwtHash == null || jwtHash.isBlank()) {
            return null;
        }
        if (firstNonBlank(
                System.getProperty("vrgo.token.generator.entitlements"),
                config.getProperty("vrgo.token.generator.entitlements")
        ) != null) {
            return null;
        }
        String capturedHash = config.getProperty("vrgo.token.generator.session.entitlementhash");
        if (capturedHash == null || capturedHash.isBlank()) {
            return null;
        }
        if (capturedHash.equalsIgnoreCase(jwtHash)) {
            return null;
        }
        String jwtDeviceId = claimString("deviceId");
        String capturedDeviceId = config.getProperty("vrgo.token.generator.session.device.id");
        String deviceHint = "";
        if (capturedDeviceId != null && jwtDeviceId != null
                && !capturedDeviceId.equalsIgnoreCase(jwtDeviceId)) {
            deviceHint = " JWT deviceId=" + jwtDeviceId + " but session-info was captured for device "
                    + capturedDeviceId + ".";
        }
        return "Token-generator session snapshot does not match the active bearer JWT: JWT entitlementHash="
                + jwtHash + " but session-info was captured with " + capturedHash + "." + deviceHint
                + " Postman works when bearer, entitlementhash, and sessionInfo.entitlements all come from the"
                + " same browser request. Update secrets/vrgo-auth.<env>.local.properties with a refresh_token"
                + " from that browser login, copy sessionInfo.entitlements into vrgo.token.generator.entitlements"
                + " in the secrets file, then run scripts/reset-auth-cache.bat.";
    }

    private static String claimString(String claimName) {
        Map<String, Object> claims = parseJwtClaims(resolveBearerToken());
        if (claims == null) {
            return null;
        }
        Object value = claims.get(claimName);
        return value != null ? value.toString() : null;
    }

    private static Map<String, Object> parseJwtClaims(String jwt) {
        String payloadJson = decodeJwtPayloadJson(jwt);
        if (payloadJson == null) {
            return null;
        }
        try {
            return MAPPER.readValue(payloadJson, new TypeReference<>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private static String decodeJwtPayloadJson(String jwt) {
        if (jwt == null || jwt.isBlank()) {
            return null;
        }
        String token = jwt.strip();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).strip();
        }
        int firstDot = token.indexOf('.');
        if (firstDot < 0) {
            return null;
        }
        int secondDot = token.indexOf('.', firstDot + 1);
        if (secondDot < 0) {
            return null;
        }
        String payload = token.substring(firstDot + 1, secondDot);
        try {
            int pad = (4 - payload.length() % 4) % 4;
            byte[] decoded = java.util.Base64.getUrlDecoder().decode(payload + "=".repeat(pad));
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String resolveBearerToken() {
        if (VrgoTokenHolder.isInitialized()) {
            return VrgoTokenHolder.getInstance().getBearerToken();
        }
        String token = System.getProperty("vrgo.bearer.token");
        if (token != null && !token.isBlank()) {
            return token;
        }
        token = System.getenv("VRGO_BEARER_TOKEN");
        return token != null && !token.isBlank() ? token : null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
