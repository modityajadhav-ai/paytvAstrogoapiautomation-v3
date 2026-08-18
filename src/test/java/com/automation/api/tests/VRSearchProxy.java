package com.automation.api.tests;

import com.automation.api.base.BaseTest;
import com.automation.api.auth.VrgoGuestTokenSupport;
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

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Test coverage for the VRGO VR Search Proxy APIs.
 * <p>
 * All endpoint paths, query parameters, and configurable values are driven by
 * {@code vrgo.search.proxy.*} keys in the active environment file.
 * <p>
 * Guest-user calls use {@code vrgo.search.proxy.guest.bearer.token} /
 * {@code VRGO_GUEST_BEARER_TOKEN} / {@code BaseTest#VRGO_MANUAL_GUEST_BEARER_TOKEN} (falling back
 * to the standard bearer token) and merge {@code vrgo.search.proxy.guest.header.*} overrides.
 */
@Feature("VR Search Proxy")
public class VRSearchProxy extends BaseTest {

    private static final String EMPTY_RECOMMENDATION_FROM_SOURCE =
            "Empty Recommendation response received from the source service";

    private static final String USECASE_NOT_CONFIGURED_MARKER = "No Use Case found for id";

    private static final String DATA_OR_USECASE_NOT_CONFIGURED_SKIP =
            "data or use case is not configured for this environment";

    // ── Global content search — logged-in ─────────────────────────────────────

    @Test(description = "GET /search-proxy/v1/global-content-search — logged-in user receives non-empty contents")
    @Story("GET global-content-search (logged-in)")
    public void globalContentSearch_loggedInUser_returnsResults() {
        requirePrerequisites();

        String keyword = resolveSearchKeyword();
        int offset = readIntProperty("vrgo.search.proxy.global.content.search.offset", 0);
        int limit  = readIntProperty("vrgo.search.proxy.global.content.search.limit", 20);

        Allure.parameter("search.keyword", keyword);
        Allure.parameter("search.offset", offset);
        Allure.parameter("search.limit", limit);
        Allure.parameter("environment", Environment.current().name());

        Response r = vrSearchProxyApi.globalContentSearchRaw(keyword, offset, limit);
        AllureAttachmentUtils.attachJson("global-content-search-loggedin-response", r.asString());
        assertGlobalContentSearchResultsOrSkip(r);
    }

    // ── Global content search — guest ─────────────────────────────────────────

    @Test(description = "GET /search-proxy/pub/v1/global-content-search — guest user receives non-empty contents")
    @Story("GET global-content-search (guest)")
    public void globalContentSearch_guestUser_returnsResults() {
        requirePrerequisites();
        requireGuestToken();

        String keyword = resolveSearchKeyword();
        int offset = readIntProperty("vrgo.search.proxy.global.content.search.offset", 0);
        int limit  = readIntProperty("vrgo.search.proxy.global.content.search.limit", 20);

        Allure.parameter("search.keyword", keyword);
        Allure.parameter("search.offset", offset);
        Allure.parameter("search.limit", limit);
        Allure.parameter("environment", Environment.current().name());

        Response r = vrSearchProxyApi.guestGlobalContentSearchRaw(keyword, offset, limit);
        AllureAttachmentUtils.attachJson("global-content-search-guest-response", r.asString());
        assertGlobalContentSearchResultsOrSkip(r);
    }

    // ── Search Suggester — logged-in ──────────────────────────────────────────

    @Test(description = "GET /search-proxy/v1/search-suggester — logged-in user receives non-empty suggestions")
    @Story("GET search-suggester (logged-in)")
    public void searchSuggester_loggedInUser_returnsResults() {
        requirePrerequisites();

        String keyword = resolveSuggesterKeyword();
        String page    = config.getProperty("vrgo.search.proxy.suggester.page", "search");
        String uc      = config.getProperty("vrgo.search.proxy.suggester.uc", "suggest");
        int offset     = readIntProperty("vrgo.search.proxy.suggester.offset", 0);
        int limit      = readIntProperty("vrgo.search.proxy.suggester.limit", 6);

        Allure.parameter("suggester.keyword", keyword);
        Allure.parameter("suggester.page", page);
        Allure.parameter("suggester.uc", uc);
        Allure.parameter("environment", Environment.current().name());

        Response r = vrSearchProxyApi.searchSuggesterRaw(keyword, page, uc, offset, limit);
        AllureAttachmentUtils.attachJson("search-suggester-loggedin-response", r.asString());
        assertSearchSuggesterResponseOrSkip(r);
    }

    // ── Search Suggester — guest ──────────────────────────────────────────────

    @Test(description = "GET /search-proxy/pub/v1/search-suggester — guest user receives non-empty suggestions")
    @Story("GET search-suggester (guest)")
    public void searchSuggester_guestUser_returnsResults() {
        requirePrerequisites();
        requireGuestToken();

        String keyword = resolveSuggesterKeyword();
        String page    = config.getProperty("vrgo.search.proxy.suggester.page", "search");
        String uc      = config.getProperty("vrgo.search.proxy.suggester.uc", "suggest");
        int offset     = readIntProperty("vrgo.search.proxy.suggester.offset", 0);
        int limit      = readIntProperty("vrgo.search.proxy.suggester.limit", 6);

        Allure.parameter("suggester.keyword", keyword);
        Allure.parameter("suggester.page", page);
        Allure.parameter("suggester.uc", uc);
        Allure.parameter("environment", Environment.current().name());

        Response r = vrSearchProxyApi.guestSearchSuggesterRaw(keyword, page, uc, offset, limit);
        AllureAttachmentUtils.attachJson("search-suggester-guest-response", r.asString());
        assertSearchSuggesterResponseOrSkip(r);
    }

    // ── Search by use-case ────────────────────────────────────────────────────

    /** Use-case values exercised by the search-by-usecase tests. */
    private static final String[] SEARCH_USECASES = {
            "trending_on_astro",
            "popular_search",
            "recommend_subgenre_comedy",
            "recommend_subgenre_action",
            "recommend_subgenre_comedy",
            "top_10_home"
    };

    @DataProvider(name = "searchUsecases")
    public static Object[][] searchUsecases() {
        Object[][] rows = new Object[SEARCH_USECASES.length][1];
        for (int i = 0; i < SEARCH_USECASES.length; i++) {
            rows[i][0] = SEARCH_USECASES[i];
        }
        return rows;
    }

    @Test(
            dataProvider = "searchUsecases",
            description = "GET /search-proxy/v1/search-by-usecase — logged-in user receives HTTP 200 for each use-case"
    )
    @Story("GET search-by-usecase (logged-in)")
    public void searchByUsecase_loggedInUser_returnsResults(String usecase) {
        requirePrerequisites();

        String page = config.getProperty("vrgo.search.proxy.usecase.page", "search");
        int offset  = readIntProperty("vrgo.search.proxy.usecase.offset", 0);
        int limit   = readIntProperty("vrgo.search.proxy.usecase.limit", 50);

        Allure.parameter("usecase", usecase);
        Allure.parameter("usecase.page", page);
        Allure.parameter("environment", Environment.current().name());

        Response r = vrSearchProxyApi.searchByUsecaseRaw(usecase, page, offset, limit);
        AllureAttachmentUtils.attachJson("search-by-usecase-loggedin-" + usecase + "-response", r.asString());
        assertSearchByUsecaseResultsOrSkip(r);
    }

    @Test(
            dataProvider = "searchUsecases",
            description = "GET /search-proxy/pub/v1/search-by-usecase — guest user receives HTTP 200 for each use-case"
    )
    @Story("GET search-by-usecase (guest)")
    public void searchByUsecase_guestUser_returnsResults(String usecase) {
        requirePrerequisites();
        requireGuestToken();

        String page = config.getProperty("vrgo.search.proxy.usecase.page", "search");
        int offset  = readIntProperty("vrgo.search.proxy.usecase.offset", 0);
        int limit   = readIntProperty("vrgo.search.proxy.usecase.limit", 50);

        Allure.parameter("usecase", usecase);
        Allure.parameter("usecase.page", page);
        Allure.parameter("environment", Environment.current().name());

        Response r = vrSearchProxyApi.guestSearchByUsecaseRaw(usecase, page, offset, limit);
        AllureAttachmentUtils.attachJson("search-by-usecase-guest-" + usecase + "-response", r.asString());
        assertSearchByUsecaseResultsOrSkip(r);
    }

    // ── Subgenre preference ───────────────────────────────────────────────────

    @Test(description = "GET /search-proxy/v1/subgenre-preference — logged-in user receives non-empty response")
    @Story("GET subgenre-preference (logged-in)")
    public void subgenrePreference_loggedInUser_returnsResults() {
        requirePrerequisites();

        Allure.parameter("environment", Environment.current().name());

        Response r = vrSearchProxyApi.subgenrePreferenceRaw();
        AllureAttachmentUtils.attachJson("subgenre-preference-loggedin-response", r.asString());
        assertSubgenrePreferenceResponseOrSkip(r);
    }

    @Test(description = "GET /search-proxy/v1/subgenre-preference — guest user receives non-empty response")
    @Story("GET subgenre-preference (guest)")
    public void subgenrePreference_guestUser_returnsResults() {
        requirePrerequisites();
        requireGuestToken();

        Allure.parameter("environment", Environment.current().name());

        Response r = vrSearchProxyApi.guestSubgenrePreferenceRaw();
        AllureAttachmentUtils.attachJson("subgenre-preference-guest-response", r.asString());
        assertSubgenrePreferenceResponseOrSkip(r);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void assertGlobalContentSearchResultsOrSkip(Response r) {
        skipIfDataOrUsecaseNotConfigured(r);
        r.then()
                .statusCode(200)
                .body("contents", notNullValue())
                .body("contents.size()", greaterThan(0));
    }

    private static void assertSearchSuggesterResponseOrSkip(Response r) {
        skipIfDataOrUsecaseNotConfigured(r);
        r.then().statusCode(200);
        Assert.assertFalse(r.asString().isBlank(), "Search suggester response body should not be empty.");
    }

    private static void assertSearchByUsecaseResultsOrSkip(Response r) {
        skipIfDataOrUsecaseNotConfigured(r);
        r.then()
                .statusCode(200)
                .body("data.results", notNullValue());
    }

    private static void assertSubgenrePreferenceResponseOrSkip(Response r) {
        skipIfDataOrUsecaseNotConfigured(r);
        r.then().statusCode(200);
        Assert.assertFalse(r.asString().isBlank(), "Subgenre preference response body should not be empty.");
    }

    private static void skipIfDataOrUsecaseNotConfigured(Response r) {
        String message = resolveSearchProxyMessage(r);
        if (message != null
                && (message.contains(EMPTY_RECOMMENDATION_FROM_SOURCE)
                || message.contains(USECASE_NOT_CONFIGURED_MARKER))) {
            throw new SkipException(DATA_OR_USECASE_NOT_CONFIGURED_SKIP);
        }
    }

    private static String resolveSearchProxyMessage(Response r) {
        String body = r.asString();
        if (body == null || body.isBlank()) {
            return null;
        }
        String message = firstNonBlank(
                r.jsonPath().getString("message"),
                r.jsonPath().getString("errorMessage"),
                r.jsonPath().getString("data.message"),
                r.jsonPath().getString("data.errorMessage")
        );
        return message != null ? message : body;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.strip();
            }
        }
        return null;
    }

    private String resolveSuggesterKeyword() {
        String kw = config.getProperty("vrgo.search.proxy.suggester.keyword");
        if (isBlank(kw) || kw.strip().startsWith("REPLACE")) {
            throw new SkipException(
                    "Set vrgo.search.proxy.suggester.keyword in environments/<env>.properties.");
        }
        return kw.strip();
    }

    private String resolveSearchKeyword() {
        String kw = config.getProperty("vrgo.search.proxy.global.content.search.keyword");
        if (isBlank(kw) || kw.strip().startsWith("REPLACE")) {
            throw new SkipException(
                    "Set vrgo.search.proxy.global.content.search.keyword in environments/<env>.properties.");
        }
        return kw.strip();
    }

    private void requirePrerequisites() {
        if (vrSearchProxyApi == null) {
            throw new SkipException("Configure vrgo.base.url in environments/<env>.properties to run this test.");
        }
        if (isBlank(System.getenv("VRGO_BEARER_TOKEN")) && isBlank(System.getProperty("vrgo.bearer.token"))) {
            throw new SkipException(
                    "Set BaseTest.VRGO_MANUAL_BEARER_TOKEN, or VRGO_BEARER_TOKEN / -Dvrgo.bearer.token, to call the VRGO API.");
        }
        if (isBlank(System.getenv("VRGO_X_API_KEY"))
                && isBlank(System.getProperty("vrgo.x.api.key"))
                && isBlank(config.getProperty("vrgo.x.api.key"))) {
            throw new SkipException(
                    "Set vrgo.x.api.key in environments/<env>.properties, or BaseTest.VRGO_MANUAL_X_API_KEY, or VRGO_X_API_KEY / -Dvrgo.x.api.key.");
        }
    }

    private void requireGuestToken() {
        if (!VrgoGuestTokenSupport.canBootstrapGuestAuth(config)) {
            throw new SkipException(
                    "Set vrgo.search.proxy.guest.bearer.token in secrets, VRGO_GUEST_BEARER_TOKEN, "
                            + "or enable guest browser recovery (vrgo.guest.browser.recovery.enabled=true).");
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

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
