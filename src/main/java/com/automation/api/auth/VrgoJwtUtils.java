package com.automation.api.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * JWT helpers for VRGO access / refresh tokens (~300s TTL in test).
 */
public final class VrgoJwtUtils {

    private VrgoJwtUtils() {
    }

    public static boolean isExpiringSoon(String jwt, long bufferSeconds) {
        return secondsRemaining(jwt) <= bufferSeconds;
    }

    public static long secondsRemaining(String jwt) {
        Long exp = extractExp(jwt);
        if (exp == null) {
            return 0L;
        }
        return Math.max(0L, exp - System.currentTimeMillis() / 1000L);
    }

    public static boolean hasProfileId(String jwt) {
        String payload = decodePayloadJson(jwt);
        return payload != null && payload.contains("\"profileId\"");
    }

    public static boolean isRefreshTokenJwt(String jwt) {
        String payload = decodePayloadJson(jwt);
        return payload != null && payload.contains("\"refresh\"");
    }

    public static String extractClaimString(String jwt, String claimName) {
        String payload = decodePayloadJson(jwt);
        if (payload == null || claimName == null || claimName.isBlank()) {
            return null;
        }
        String key = "\"" + claimName + "\":\"";
        int start = payload.indexOf(key);
        if (start < 0) {
            return null;
        }
        start += key.length();
        int end = payload.indexOf('"', start);
        if (end < 0) {
            return null;
        }
        return payload.substring(start, end);
    }

    public static String normalizeRefreshTokenForGrant(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return refreshToken;
        }
        String trimmed = stripBearer(refreshToken);
        if (isRefreshTokenJwt(trimmed)) {
            String opaque = extractClaimString(trimmed, "refresh");
            if (opaque != null && !opaque.isBlank()) {
                return opaque;
            }
        }
        return trimmed;
    }

    private static Long extractExp(String jwt) {
        String payload = decodePayloadJson(jwt);
        if (payload == null) {
            return null;
        }
        int expIndex = payload.indexOf("\"exp\":");
        if (expIndex < 0) {
            return null;
        }
        int start = expIndex + 6;
        int end = start;
        while (end < payload.length() && Character.isDigit(payload.charAt(end))) {
            end++;
        }
        try {
            return Long.parseLong(payload.substring(start, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String decodePayloadJson(String jwt) {
        if (jwt == null || jwt.isBlank()) {
            return null;
        }
        String token = stripBearer(jwt);
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
            byte[] decoded = Base64.getUrlDecoder().decode(payload + "=".repeat(pad));
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String stripBearer(String token) {
        String t = token.strip();
        if (t.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return t.substring(7).strip();
        }
        return t;
    }
}
