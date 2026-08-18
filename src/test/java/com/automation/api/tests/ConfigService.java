package com.automation.api.tests;

import com.automation.api.base.BaseTest;
import com.automation.api.config.Environment;
import com.automation.api.util.AllureAttachmentUtils;
import io.qameta.allure.Allure;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * VRGO config-service: {@code GET .../platform-configs}, {@code GET .../avatars}, {@code GET .../operator-configs},
 * and {@code GET .../image-configs}. Each successful test asserts {@code data} is non-null and non-empty (list/map) where applicable.
 * Static headers come from {@code vrgo.header.*} in the active environment file.
 *
 * <p>Run order uses {@code @Test(priority = ...)} so platform-configs runs before avatars; avatars returns 401 with
 * {@code Token Expired} when the bearer JWT is stale — refresh {@code VRGO_BEARER_TOKEN} / {@code BaseTest} manual token.
 */
@Feature("Config service")
public class ConfigService extends BaseTest {

    private static final String OPC_CMS_CONFIGURATION_SKIP_MESSAGE =
            "Please configure platform at OPC/CMS";

    private static final String PLATFORM_NOT_FOUND_MARKER = "platform not found";

    /** Values for the {@code platform} request header (config-service / platform-configs and avatars). */
    private static final String[] PLATFORM_CONFIG_HEADER_VALUES = {
            "SET_TOP_BOX_ULTRA",
            "SET_TOP_BOX",
            "SET_TOP_BOX_ULTI",
            "ANDROID",
            "IOS",
            "TABLET",
            "IPAD",
            "WEB",
            "ANDROID_TV",
            "LG_HTML_TV",
            "SAMSUNG_HTML_TV"
          //  "GOOGLE_TV",
          //  "VIDAA_TV",
    };

    @DataProvider(name = "platformConfigPlatforms")
    public static Object[][] platformConfigPlatforms() {
        Object[][] rows = new Object[PLATFORM_CONFIG_HEADER_VALUES.length][1];
        for (int i = 0; i < PLATFORM_CONFIG_HEADER_VALUES.length; i++) {
            rows[i][0] = PLATFORM_CONFIG_HEADER_VALUES[i];
        }
        return rows;
    }

    /** Pagination slices for {@code GET .../avatars} (limit, offset). */
    @DataProvider(name = "avatarPagination")
    public static Object[][] avatarPagination() {
        return new Object[][] {
                {15, 0},
                {10, 0},
                {20, 0},
        };
    }

    @Test(
            priority = 0,
            dataProvider = "platformConfigPlatforms",
            description = "GET /config-service/pub/v1/platform-configs — 200 and status true for each platform header value"
    )
    @Story("GET /config-service/pub/v1/platform-configs")
    public void configService_getPlatformConfigs_forEachPlatform_returnsOk(String platform) {
        requireConfigServicePrerequisites();

        Allure.parameter("environment", Environment.current().name());
        Allure.parameter("platform", platform);

        Response r = configServiceApi.getPlatformConfigsRaw(platform);
        AllureAttachmentUtils.attachJson("platform-configs-" + platform, r.asString());
        assertConfigServiceResponseOrSkip(r, platform);
    }

    @Test(
            priority = 2,
            description = "GET /config-service/pub/v1/operator-configs — 200 and status true (single call; profileid from properties)"
    )
    @Story("GET /config-service/pub/v1/operator-configs")
    public void configService_getOperatorConfigs_returnsOk() {
        requireConfigServicePrerequisites();

        String operatorProfileId = config.getProperty("vrgo.config.service.operator.profile.id");
        if (operatorProfileId == null || operatorProfileId.isBlank()) {
            throw new SkipException(
                    "Set vrgo.config.service.operator.profile.id in environments/<env>.properties (operator-configs profile)."
            );
        }
        if (operatorProfileId.contains("REPLACE")) {
            throw new SkipException(
                    "Replace vrgo.config.service.operator.profile.id with a real profile UUID for this environment."
            );
        }

        Allure.parameter("environment", Environment.current().name());
        Allure.parameter("operator.profileid", operatorProfileId.strip());

        Response r = configServiceApi.getOperatorConfigsRaw();
        AllureAttachmentUtils.attachJson("operator-configs-response", r.asString());
        assertConfigServiceResponseOrSkip(r, null);
    }

    @Test(
            priority = 3,
            description = "GET /config-service/pub/v1/image-configs — 200 and status true (single call; profileid from properties)"
    )
    @Story("GET /config-service/pub/v1/image-configs")
    public void configService_getImageConfigs_returnsOk() {
        requireConfigServicePrerequisites();

        String imageProfileId = config.getProperty("vrgo.config.service.image.profile.id");
        if (imageProfileId == null || imageProfileId.isBlank()) {
            throw new SkipException(
                    "Set vrgo.config.service.image.profile.id in environments/<env>.properties (image-configs profile)."
            );
        }
        if (imageProfileId.contains("REPLACE")) {
            throw new SkipException(
                    "Replace vrgo.config.service.image.profile.id with a real profile UUID for this environment."
            );
        }

        Allure.parameter("environment", Environment.current().name());
        Allure.parameter("image.profileid", imageProfileId.strip());

        Response r = configServiceApi.getImageConfigsRaw();
        AllureAttachmentUtils.attachJson("image-configs-response", r.asString());
        assertConfigServiceResponseOrSkip(r, null);
    }

    @Test(
            priority = 10,
            dataProvider = "platformConfigPlatforms",
            description = "GET /config-service/v1/avatars — 200 and status true for each platform (limit/offset from properties)"
    )
    @Story("GET /config-service/v1/avatars (per platform)")
    public void configService_getAvatars_forEachPlatform_returnsOk(String platform) {
        requireConfigServicePrerequisites();

        int limit = readIntProperty("vrgo.config.service.avatars.default.limit", 15);
        int offset = readIntProperty("vrgo.config.service.avatars.default.offset", 0);

        Allure.parameter("environment", Environment.current().name());
        Allure.parameter("platform", platform);
        Allure.parameter("avatars.limit", String.valueOf(limit));
        Allure.parameter("avatars.offset", String.valueOf(offset));

        Response r = configServiceApi.getAvatarsRaw(platform, limit, offset);
        AllureAttachmentUtils.attachJson("avatars-" + platform + "-l" + limit + "-o" + offset, r.asString());
        assertConfigServiceResponseOrSkip(r, platform);
    }

    @Test(
            priority = 5,
            dataProvider = "avatarPagination",
            description = "GET /config-service/v1/avatars — 200 and status true for each limit/offset (platform from vrgo.header.platform)"
    )
    @Story("GET /config-service/v1/avatars (pagination)")
    public void configService_getAvatars_pagination_returnsOk(int limit, int offset) {
        requireConfigServicePrerequisites();

        Allure.parameter("environment", Environment.current().name());
        Allure.parameter("avatars.limit", String.valueOf(limit));
        Allure.parameter("avatars.offset", String.valueOf(offset));

        Response r = configServiceApi.getAvatarsRaw(null, limit, offset);
        AllureAttachmentUtils.attachJson("avatars-pagination-l" + limit + "-o" + offset, r.asString());
        assertConfigServiceResponseOrSkip(r, null);
    }

    /**
     * Skips when the API reports platform is missing in OPC/CMS (often HTTP 400 with
     * {@code "message": "Platform not found"}), otherwise asserts a successful config-service payload.
     */
    private static void assertConfigServiceResponseOrSkip(Response r, String platform) {
        skipIfPlatformNotFound(r, platform);
        r.then()
                .statusCode(200)
                .body("status", equalTo(true))
                .body("data", notNullValue());
        assertResponseDataNotNullOrEmpty(r);
    }

    /**
     * Skips when the response body contains {@code Platform not found}. Uses a plain body scan so malformed
     * or non-JSON error pages do not throw during skip detection (which Allure would report as broken).
     */
    private static void skipIfPlatformNotFound(Response r, String platform) {
        String body = r.asString();
        if (body == null || !body.toLowerCase(Locale.ROOT).contains(PLATFORM_NOT_FOUND_MARKER)) {
            return;
        }
        String detail = platform != null ? " (platform=" + platform + ")" : "";
        throw new SkipException(OPC_CMS_CONFIGURATION_SKIP_MESSAGE + detail);
    }

    /**
     * Asserts JSON {@code data} is non-null and, when a JSON array or object, has at least one entry.
     */
    private static void assertResponseDataNotNullOrEmpty(Response r) {
        Object data = r.jsonPath().get("data");
        Assert.assertNotNull(data, "response data must not be null");
        if (data instanceof Collection<?>) {
            Assert.assertFalse(((Collection<?>) data).isEmpty(), "response data must not be an empty array");
        } else if (data instanceof Map<?, ?>) {
            Assert.assertFalse(((Map<?, ?>) data).isEmpty(), "response data must not be an empty object");
        } else if (data instanceof String) {
            Assert.assertFalse(((String) data).isBlank(), "response data must not be an empty string");
        }
    }

    private void requireConfigServicePrerequisites() {
        if (configServiceApi == null) {
            throw new SkipException("Configure vrgo.base.url in environments/<env>.properties to run this test.");
        }
        if (!isVrgoAuthConfigured()) {
            throw new SkipException(VRGO_AUTH_SKIP_MESSAGE);
        }
        if (isBlank(System.getenv("VRGO_X_API_KEY"))
                && isBlank(System.getProperty("vrgo.x.api.key"))
                && isBlank(config.getProperty("vrgo.x.api.key"))) {
            throw new SkipException(
                    "Set vrgo.x.api.key in environments/<env>.properties, or BaseTest.VRGO_MANUAL_X_API_KEY, or VRGO_X_API_KEY / -Dvrgo.x.api.key."
            );
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
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
}
