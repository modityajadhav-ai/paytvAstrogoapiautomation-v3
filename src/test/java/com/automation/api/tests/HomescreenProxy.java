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
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * VRGO homescreen-proxy: rail-hierarchy for the HOME tab when DRP is enabled.
 * <p>
 * Resolves {@code pageId} from the menu API ({@code tabName=HOME} → {@code linkToPage}) and
 * calls {@code /homescreen-proxy/pub/v1/rail-hierarchy} only when {@code drpEnabled} is true.
 */
@Feature("Homescreen-proxy API")
public class HomescreenProxy extends BaseTest {

    @Test(description = "GET /homescreen-proxy/pub/v1/rail-hierarchy — fetch rail for HOME tab when drpEnabled is true")
    @Story("GET /homescreen-proxy/pub/v1/rail-hierarchy (HOME tab, drpEnabled)")
    public void fetchRail_homeTabWhenDrpEnabled_returnsResults() {
        requirePrerequisites();

        String homeTabName = config.getProperty("vrgo.homescreen.proxy.menu.home.tab.name", "HOME");
        String page = config.getProperty("vrgo.homescreen.proxy.rail.hierarchy.page", "home");
        int offset = readIntProperty("vrgo.homescreen.proxy.rail.hierarchy.offset", 0);
        int limit = readIntProperty("vrgo.homescreen.proxy.rail.hierarchy.limit", 10);

        Allure.parameter("environment", Environment.current().name());
        Allure.parameter("homescreen.proxy.home.tab", homeTabName);
        Allure.parameter("homescreen.proxy.page", page);
        Allure.parameter("homescreen.proxy.offset", String.valueOf(offset));
        Allure.parameter("homescreen.proxy.limit", String.valueOf(limit));

        Response menu = homescreenApi.getMenuListRaw(null);
        menu.then().statusCode(200).body("status", equalTo(true));
        AllureAttachmentUtils.attachJson("homescreen-menu-for-proxy-rail", menu.asString());

        List<Map<String, Object>> menuRows = menu.jsonPath().getList("data.menuDataList");
        if (menuRows == null || menuRows.isEmpty()) {
            throw new SkipException("Menu data.menuDataList was empty; cannot resolve HOME tab pageId.");
        }

        Map<String, Object> homeRow = findMenuRowByTabName(menuRows, homeTabName);
        if (homeRow == null) {
            throw new SkipException(
                    "No menu row with tabName \"" + homeTabName + "\"; cannot resolve pageId for homescreen-proxy rail."
            );
        }

        if (!isDrpEnabled(homeRow.get("drpEnabled"))) {
            throw new SkipException(
                    "HOME tab drpEnabled is not true; skipping homescreen-proxy rail-hierarchy test."
            );
        }

        String pageId = stringifyLinkToPage(homeRow.get("linkToPage"));
        if (pageId == null || pageId.isBlank()) {
            throw new SkipException(
                    "HOME tab has drpEnabled=true but linkToPage is blank; cannot call rail-hierarchy."
            );
        }

        Allure.parameter("homescreen.proxy.pageId", pageId);
        Allure.parameter("homescreen.proxy.drpEnabled", "true");

        Response rail = homescreenProxyApi.getRailHierarchyRaw(pageId, page, offset, limit);
        AllureAttachmentUtils.attachJson("homescreen-proxy-rail-hierarchy-response", rail.asString());

        rail.then()
                .statusCode(200)
                .body(notNullValue());

        Assert.assertFalse(rail.asString().isBlank(),
                "homescreen-proxy rail-hierarchy response body must not be empty.");

        List<?> results = resultsListFromRailResponse(rail);
        Assert.assertNotNull(
                results,
                "Expected non-null results list (data.results or results) for pageId=" + pageId + " page=" + page
        );
        Assert.assertFalse(
                results.isEmpty(),
                "results must not be empty for pageId=" + pageId + " page=" + page
                        + " offset=" + offset + " limit=" + limit
        );
    }

    private static Map<String, Object> findMenuRowByTabName(List<Map<String, Object>> menuRows, String tabName) {
        String wanted = tabName == null ? "" : tabName.strip();
        for (Map<String, Object> row : menuRows) {
            if (row == null) {
                continue;
            }
            Object nameObj = row.get("tabName");
            if (nameObj == null) {
                continue;
            }
            if (wanted.equals(String.valueOf(nameObj).strip())) {
                return row;
            }
        }
        return null;
    }

    private static boolean isDrpEnabled(Object drpEnabled) {
        if (drpEnabled == null) {
            return false;
        }
        if (drpEnabled instanceof Boolean) {
            return (Boolean) drpEnabled;
        }
        return Boolean.parseBoolean(String.valueOf(drpEnabled).strip());
    }

    private static String stringifyLinkToPage(Object linkToPage) {
        if (linkToPage == null) {
            return null;
        }
        String s = String.valueOf(linkToPage).strip();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    private static List<?> resultsListFromRailResponse(Response r) {
        Object dataResults = r.path("data.results");
        if (dataResults instanceof List) {
            return (List<?>) dataResults;
        }
        Object rootResults = r.path("results");
        if (rootResults instanceof List) {
            return (List<?>) rootResults;
        }
        return null;
    }

    private void requirePrerequisites() {
        if (homescreenApi == null || homescreenProxyApi == null) {
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
        String platformId = config.getProperty("vrgo.homescreen.menu.platform.id");
        if (platformId == null || platformId.isBlank()) {
            throw new SkipException("Set vrgo.homescreen.menu.platform.id in environments/<env>.properties.");
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
