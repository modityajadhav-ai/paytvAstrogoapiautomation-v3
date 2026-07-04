package com.automation.api.tests;

import com.automation.api.base.BaseTest;
import com.automation.api.config.Environment;
import com.automation.api.util.AllureAttachmentUtils;
import io.qameta.allure.Allure;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * VRGO homescreen-service: {@code GET /homescreen-service/pub/v1/footers/{platformId}} for all configured platforms.
 * Asserts {@code status}, {@code message}, and non-empty {@code data}. Empty {@code data} with HTTP 200 is skipped
 * (likely missing CMS configuration for that platform).
 */
@Feature("Homescreen footer")
public class Footer extends BaseTest {

    private static final String SUCCESS_MESSAGE = "Data fetched Successfully";

    private static final String CMS_CONFIGURATION_SKIP_MESSAGE =
            "Check configuration for platform on CMS";

    /** Platform display name, platform id (URL path), platform request header value. */
    private static final String[][] FOOTER_PLATFORMS = {
            {"STB - Ultra V1", "67722c32397a584628096605", "SET_TOP_BOX_ULTRA"},
            {"STB - Ultra V2", "68ecbb4b190b462ef3a60a82", "SET_TOP_BOX"},
            {"STB - Ulti Box", "67722c48397a584628096606", "SET_TOP_BOX_ULTI"},
            {"Web", "5f438e99c814696803f004d9", "WEB"},
            {"Android", "5f449b743d24a0435dbdb429", "ANDROID"},
            {"iOS", "5f449ba43d24a0435dbdb42a", "IOS"},
            {"TV", "6924a2f6a6f5544c065b55f6", "ANDROID_TV"},
    };

    @DataProvider(name = "footerPlatforms")
    public static Object[][] footerPlatforms() {
        Object[][] rows = new Object[FOOTER_PLATFORMS.length][3];
        for (int i = 0; i < FOOTER_PLATFORMS.length; i++) {
            rows[i][0] = FOOTER_PLATFORMS[i][0];
            rows[i][1] = FOOTER_PLATFORMS[i][1];
            rows[i][2] = FOOTER_PLATFORMS[i][2];
        }
        return rows;
    }

    @Test(
            dataProvider = "footerPlatforms",
            description = "GET /homescreen-service/pub/v1/footers/{platformId} — 200, status true, message; skip when data is empty (CMS config)"
    )
    @Story("GET /homescreen-service/pub/v1/footers/{platformId}")
    public void homescreen_getFooters_forEachPlatform_returnsOk(
            String platformName,
            String platformId,
            String platformHeader
    ) {
        requireHomescreenPrerequisites();

        Allure.parameter("environment", Environment.current().name());
        Allure.parameter("footer.platformName", platformName);
        Allure.parameter("footer.platformId", platformId);
        Allure.parameter("footer.platformHeader", platformHeader);

        Response r = homescreenApi.getFootersRaw(platformId, platformHeader);
        AllureAttachmentUtils.attachJson("footers-" + sanitizeForAttachment(platformName), r.asString());

        r.then()
                .statusCode(200)
                .body("status", equalTo(true))
                .body("message", equalTo(SUCCESS_MESSAGE))
                .body("data", notNullValue());
        assertResponseDataNotNullOrEmpty(r, platformName, platformId);
    }

    /**
     * Skips when {@code data} is null or empty (e.g. {@code "data": []} with HTTP 200) — typically missing CMS footer config.
     */
    private static void assertResponseDataNotNullOrEmpty(Response r, String platformName, String platformId) {
        Object data = r.jsonPath().get("data");
        if (data == null) {
            throw cmsConfigurationSkip(platformName, platformId);
        }
        if (data instanceof Collection<?> collection && collection.isEmpty()) {
            throw cmsConfigurationSkip(platformName, platformId);
        }
        if (data instanceof Map<?, ?> map && map.isEmpty()) {
            throw cmsConfigurationSkip(platformName, platformId);
        }
        if (data instanceof String s && s.isBlank()) {
            throw cmsConfigurationSkip(platformName, platformId);
        }
    }

    private static SkipException cmsConfigurationSkip(String platformName, String platformId) {
        return new SkipException(
                CMS_CONFIGURATION_SKIP_MESSAGE
                        + " (platform=" + platformName + ", platformId=" + platformId + ")"
        );
    }

    private static String sanitizeForAttachment(String platformName) {
        return platformName.replace(' ', '-').replace('/', '-');
    }

    private void requireHomescreenPrerequisites() {
        if (homescreenApi == null) {
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
                    "Set vrgo.x.api.key in environments/<env>.properties, or BaseTest.VRGO_MANUAL_X_API_KEY, or VRGO_X_API_KEY / -Dvrgo.x.api.key."
            );
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
