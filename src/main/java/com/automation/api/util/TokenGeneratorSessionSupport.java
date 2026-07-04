package com.automation.api.util;

import com.automation.api.auth.VrgoTokenHolder;
import com.automation.api.config.EnvironmentConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aligns token-generator {@code sessionInfo} with the active bearer JWT.
 * Entitlements are kept from {@code session-info.json} unless
 * {@code vrgo.token.generator.entitlements} is explicitly set.
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
        }

        String entitlementsJson = config.getProperty("vrgo.token.generator.entitlements");
        if (entitlementsJson != null && !entitlementsJson.isBlank()) {
            try {
                List<Map<String, Object>> entitlements = MAPPER.readValue(
                        entitlementsJson.strip(), new TypeReference<>() {}
                );
                enriched.put("entitlements", entitlements);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to parse vrgo.token.generator.entitlements: " + e.getMessage(), e
                );
            }
        }
        return enriched;
    }

    public static String entitlementHashFromBearer() {
        Map<String, Object> claims = parseJwtClaims(resolveBearerToken());
        if (claims == null) {
            return null;
        }
        Object hash = claims.get("entitlementHash");
        return hash != null ? hash.toString() : null;
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
}
