package com.automation.api.tests;

import com.automation.api.base.BaseTest;
import com.automation.api.config.Environment;
import com.automation.api.util.AllureAttachmentUtils;
import com.automation.api.util.TokenGeneratorSessionSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * VRGO token-generator-service:
 * <ul>
 *   <li>{@code POST /token-generator-service/v2/ctg?isStatic=false}</li>
 *   <li>{@code POST /token-generator-service/v2/uwm?isStatic=false}</li>
 * </ul>
 * {@code contentId} / {@code contentType} for CTG come from {@code vrgo.token.generator.ctg.*}.
 * UWM uses {@code vrgo.token.generator.uwm.*} with fallback to CTG values.
 * resource configured by {@code vrgo.token.generator.session.info.resource}.
 * <p>
 * Intended for PROD ({@code -Denv=prod}). Bearer token and {@code vrgo.header.*} must match the
 * active account/session; refresh {@code VRGO_MANUAL_BEARER_TOKEN} when the JWT expires.
 * {@code sessionInfo.entitlements} in {@code token-generator/session-info.json} must match the
 * active account entitlements (copy from a working browser request when entitlements change).
 */
@Feature("Token Generator")
public class TokenGenerator extends BaseTest {

    private static final String CTG_SUCCESS_MESSAGE = "DRM Token Generated successfully";
    private static final String CTG_SUCCESS_CODE = "#SUC-120-002";
    private static final String UWM_SUCCESS_MESSAGE = "Watermark Token Generated successfully";
    private static final String UWM_SUCCESS_CODE = "#SUC-120-001";

    @Test(
            description = "POST /token-generator-service/v2/ctg — generates DRM token for content"
    )
    @Story("POST /token-generator-service/v2/ctg")
    public void tokenGenerator_postCtgForChannel_returns200() {
        requirePrerequisites();

        String contentId = resolveCtgContentId();
        String contentType = resolveCtgContentType();
        boolean isStatic = Boolean.parseBoolean(
                config.getProperty("vrgo.token.generator.is.static", "false")
        );

        Allure.parameter("contentId", contentId);
        Allure.parameter("contentType", contentType);
        Allure.parameter("isStatic", isStatic);
        Allure.parameter("environment", Environment.current().name());

        Map<String, Object> body = buildCtgBody(contentId, contentType);

        Response r = tokenGeneratorApi.postCtgRaw(isStatic, body);
        AllureAttachmentUtils.attachJson("token-generator-ctg-response", r.asString());

        assertCtgResponse(r);
    }

    @Test(
            description = "POST /token-generator-service/v2/uwm — generates UWM playback token for a channel"
    )
    @Story("POST /token-generator-service/v2/uwm")
    public void tokenGenerator_postUwmForChannel_returns200() {
        requirePrerequisites();

        String contentId = resolveUwmContentId();
        String contentType = resolveUwmContentType();
        boolean isStatic = Boolean.parseBoolean(
                config.getProperty("vrgo.token.generator.is.static", "false")
        );
        String laContentId = config.getProperty("vrgo.token.generator.uwm.la.content.id", "5027");

        Allure.parameter("contentId", contentId);
        Allure.parameter("contentType", contentType);
        Allure.parameter("laContentId", laContentId);
        Allure.parameter("isStatic", isStatic);
        Allure.parameter("environment", Environment.current().name());

        Map<String, Object> body = buildUwmBody(contentId, contentType, laContentId);

        Response r = tokenGeneratorApi.postUwmRaw(isStatic, body);
        AllureAttachmentUtils.attachJson("token-generator-uwm-response", r.asString());

        assertUwmResponse(r);
    }

    private void assertCtgResponse(Response r) {
        r.then()
                .statusCode(200)
                .body("status", equalTo(true))
                .body("message", equalTo(CTG_SUCCESS_MESSAGE))
                .body("successCode", equalTo(CTG_SUCCESS_CODE))
                .body("data", notNullValue())
                .body("data.drmToken", notNullValue())
                .body("data.drmToken.token", notNullValue())
                .body("data.drmToken.expiresIn", notNullValue());

        String drmToken = r.jsonPath().getString("data.drmToken.token");
        Assert.assertFalse(drmToken.isBlank(), "data.drmToken.token must not be blank.");
        Assert.assertTrue(isJwtFormat(drmToken), "data.drmToken.token must be a JWT (header.payload.signature).");

        long expiresIn = r.jsonPath().getLong("data.drmToken.expiresIn");
        Assert.assertTrue(
                expiresIn > System.currentTimeMillis() / 1000L,
                "data.drmToken.expiresIn must be a future epoch second, got " + expiresIn
        );
    }

    private void assertUwmResponse(Response r) {
        r.then()
                .statusCode(200)
                .body("status", equalTo(true))
                .body("message", equalTo(UWM_SUCCESS_MESSAGE))
                .body("successCode", equalTo(UWM_SUCCESS_CODE))
                .body("data", notNullValue())
                .body("data.cdnToken", notNullValue())
                .body("data.cdnToken.token", notNullValue())
                .body("data.cdnToken.expiresIn", notNullValue());

        String cdnToken = r.jsonPath().getString("data.cdnToken.token");
        Assert.assertFalse(cdnToken.isBlank(), "data.cdnToken.token must not be blank.");
        Assert.assertTrue(isJwtFormat(cdnToken), "data.cdnToken.token must be a JWT (header.payload.signature).");

        long expiresIn = r.jsonPath().getLong("data.cdnToken.expiresIn");
        Assert.assertTrue(
                expiresIn > System.currentTimeMillis() / 1000L,
                "data.cdnToken.expiresIn must be a future epoch second, got " + expiresIn
        );
    }

    private static boolean isJwtFormat(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String[] parts = token.split("\\.");
        return parts.length == 3 && !parts[0].isBlank() && !parts[1].isBlank() && !parts[2].isBlank();
    }

    private Map<String, Object> buildCtgBody(String channelId, String contentType) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contentType", contentType);
        body.put("contentId", channelId);
        body.put("sessionInfo", loadSessionInfo());
        return body;
    }

    private Map<String, Object> buildUwmBody(String channelId, String contentType, String laContentId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", readIntProperty("vrgo.token.generator.uwm.type", 0));
        body.put("cdnList", null);
        body.put("isWMAuthEnabled", Boolean.parseBoolean(
                config.getProperty("vrgo.token.generator.uwm.is.wm.auth.enabled", "false")
        ));
        body.put("isCDNAuthEnabled", Boolean.parseBoolean(
                config.getProperty("vrgo.token.generator.uwm.is.cdn.auth.enabled", "true")
        ));
        body.put("laContentId", laContentId);
        body.put("contentType", contentType);
        body.put("contentId", channelId);
        body.put("sessionInfo", loadSessionInfo());
        return body;
    }

    private Map<String, Object> loadSessionInfo() {
        String resource = config.getProperty(
                "vrgo.token.generator.session.info.resource",
                "token-generator/session-info.json"
        );
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new SkipException(
                        "Missing classpath resource '" + resource + "'. "
                                + "Set vrgo.token.generator.session.info.resource or add token-generator/session-info.json."
                );
            }
            return TokenGeneratorSessionSupport.enrich(
                    new ObjectMapper().readValue(in, new TypeReference<Map<String, Object>>() {}),
                    config
            );
        } catch (SkipException e) {
            throw e;
        } catch (Exception e) {
            throw new SkipException("Failed to load sessionInfo from '" + resource + "': " + e.getMessage());
        }
    }

    private String resolveCtgContentId() {
        String contentId = firstNonBlank(
                config.getProperty("vrgo.token.generator.ctg.content.id"),
                config.getProperty("vrgo.token.generator.content.id"),
                config.getProperty("vrgo.token.generator.channel.id")
        );
        if (!isConfiguredId(contentId)) {
            throw new SkipException(
                    "Set vrgo.token.generator.ctg.content.id in the active environment file."
            );
        }
        return contentId.strip();
    }

    private String resolveCtgContentType() {
        return config.getProperty("vrgo.token.generator.ctg.content.type",
                config.getProperty("vrgo.token.generator.content.type", "tv_show")).strip();
    }

    private String resolveUwmContentId() {
        String contentId = firstNonBlank(
                config.getProperty("vrgo.token.generator.uwm.content.id"),
                config.getProperty("vrgo.token.generator.ctg.content.id"),
                config.getProperty("vrgo.token.generator.channel.id"),
                config.getProperty("vrgo.content.detail.channel.id")
        );
        if (!isConfiguredId(contentId)) {
            throw new SkipException(
                    "Set vrgo.token.generator.uwm.content.id or vrgo.token.generator.ctg.content.id."
            );
        }
        return contentId.strip();
    }

    private String resolveUwmContentType() {
        return config.getProperty("vrgo.token.generator.uwm.content.type",
                config.getProperty("vrgo.token.generator.ctg.content.type",
                        config.getProperty("vrgo.token.generator.content.type", "channel"))).strip();
    }

    private void requirePrerequisites() {
        if (tokenGeneratorApi == null) {
            throw new SkipException("Configure vrgo.base.url in environments/<env>.properties to run this test.");
        }
        if (isBlank(System.getenv("VRGO_BEARER_TOKEN")) && isBlank(System.getProperty("vrgo.bearer.token"))) {
            throw new SkipException(
                    "Set BaseTest.VRGO_MANUAL_BEARER_TOKEN, or VRGO_BEARER_TOKEN / -Dvrgo.bearer.token, to call the VRGO API."
            );
        }
        if (isBlank(System.getenv("VRGO_X_API_KEY"))
                && isBlank(System.getProperty("vrgo.x.api.key"))
                && isBlank(config.getProperty("vrgo.x.api.key"))) {
            throw new SkipException(
                    "Set vrgo.x.api.key in environments/<env>.properties, BaseTest.VRGO_MANUAL_X_API_KEY, or VRGO_X_API_KEY / -Dvrgo.x.api.key."
            );
        }
    }

    private static int readIntProperty(String key, int defaultValue) {
        String s = config.getProperty(key);
        if (s == null || s.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean isConfiguredId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String upper = id.strip().toUpperCase(Locale.ROOT);
        return !upper.startsWith("REPLACE") && !upper.equals("NULL");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
