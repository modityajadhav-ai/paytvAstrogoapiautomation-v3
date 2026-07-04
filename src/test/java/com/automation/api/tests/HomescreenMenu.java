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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * VRGO homescreen-service: menu, rail-hierarchy, rail-hierarchy details, rail by id, see-all, purchased-rail.
 * Uses {@link com.automation.api.client.HomescreenApiClient} and {@code vrgo.homescreen.*} in environment files.
 */
@Feature("Homescreen")
public class HomescreenMenu extends BaseTest {

    /** Tab names whose {@code linkToPage} from the menu is passed to rail-hierarchy (order preserved). */
    private static final String[] RAIL_HIERARCHY_TAB_NAMES = {"HOME", "Movies", "TV Shows"};

    private static final String RAIL_CATEGORY_NEW = "NEW";

    private static final Set<String> RAIL_TYPES_FOR_BY_ID_DISCOVERY = Set.of(
            "RAIL",
            "BRAND_RAIL",
            "HERO_BANNER",
            "DYNAMIC_RAIL"
    );

    @Test(description = "GET /homescreen-service/pub/v2/menu/list/platformId — 200 and non-empty JSON")
    @Story("GET /homescreen-service/pub/v2/menu/list/platformId")
    public void homescreen_getMenuListByPlatform_returnsOk() {
        requireHomescreenPrerequisites();

        String platformId = config.getProperty("vrgo.homescreen.menu.platform.id");
        if (platformId == null || platformId.isBlank()) {
            throw new SkipException("Set vrgo.homescreen.menu.platform.id in environments/<env>.properties.");
        }

        Allure.parameter("environment", Environment.current().name());
        Allure.parameter("homescreen.platformId", platformId.strip());

        Response r = homescreenApi.getMenuListRaw(null);
        AllureAttachmentUtils.attachJson("homescreen-menu-list-response", r.asString());

        r.then()
                .statusCode(200)
                .body("status", equalTo(true))
                .body("data", notNullValue())
                .body("data.menuDataList", notNullValue())
                .body("data.menuDataList", hasSize(greaterThan(0)));
    }

    @Test(
            description = "GET rail-hierarchy per menu tab (HOME, Movies, TV Shows) using each row's linkToPage; results not empty"
    )
    @Story("GET /homescreen-service/pub/v1/rail-hierarchy/{linkToPageId}")
    public void homescreen_railHierarchy_forHomeMoviesTvShows_resultsNotEmpty() {
        requireHomescreenPrerequisites();

        String platformId = config.getProperty("vrgo.homescreen.menu.platform.id");
        if (platformId == null || platformId.isBlank()) {
            throw new SkipException("Set vrgo.homescreen.menu.platform.id in environments/<env>.properties.");
        }

        int limit = readIntProperty("vrgo.homescreen.rail.hierarchy.limit", 5);
        int offset = readIntProperty("vrgo.homescreen.rail.hierarchy.offset", 20);
        int offsetFallback = readIntProperty("vrgo.homescreen.rail.hierarchy.offset.fallback", 0);

        Allure.parameter("environment", Environment.current().name());
        Allure.parameter("homescreen.rail.limit", String.valueOf(limit));
        Allure.parameter("homescreen.rail.offset", String.valueOf(offset));
        if (offsetFallback != offset) {
            Allure.parameter("homescreen.rail.offset.fallback", String.valueOf(offsetFallback));
        }

        Response menu = homescreenApi.getMenuListRaw(null);
        menu.then().statusCode(200).body("status", equalTo(true));
        AllureAttachmentUtils.attachJson("homescreen-menu-for-rail-tabs", menu.asString());

        List<Map<String, Object>> menuRows = menu.jsonPath().getList("data.menuDataList");
        if (menuRows == null || menuRows.isEmpty()) {
            throw new SkipException("Menu data.menuDataList was empty; cannot resolve linkToPage for tabs.");
        }

        Map<String, String> tabToLink = linkToPageByTabName(menuRows, RAIL_HIERARCHY_TAB_NAMES);
        for (String tab : RAIL_HIERARCHY_TAB_NAMES) {
            String linkId = tabToLink.get(tab);
            if (linkId == null || linkId.isBlank()) {
                throw new SkipException(
                        "No menu row with tabName \"" + tab + "\" and non-blank linkToPage. Found tabs: " + tabToLink.keySet()
                );
            }
        }

        for (String tab : RAIL_HIERARCHY_TAB_NAMES) {
            String linkId = tabToLink.get(tab);
            Allure.parameter("rail.tab." + tab.replace(' ', '_'), linkId);

            Response rail = homescreenApi.getRailHierarchyRaw(linkId, limit, offset);
            AllureAttachmentUtils.attachJson("rail-hierarchy-" + tab.replace(' ', '-') + "-" + linkId + "-o" + offset, rail.asString());

            rail.then().statusCode(200);
            List<?> results = resultsListFromRailResponse(rail);
            if ((results == null || results.isEmpty()) && offsetFallback != offset) {
                rail = homescreenApi.getRailHierarchyRaw(linkId, limit, offsetFallback);
                AllureAttachmentUtils.attachJson(
                        "rail-hierarchy-" + tab.replace(' ', '-') + "-" + linkId + "-o" + offsetFallback + "-fallback",
                        rail.asString()
                );
                rail.then().statusCode(200);
                results = resultsListFromRailResponse(rail);
            }
            Assert.assertNotNull(
                    results,
                    "Expected non-null results list (data.results or results) for tab=" + tab + " linkToPage=" + linkId
            );
            Assert.assertFalse(
                    results.isEmpty(),
                    "results must not be empty for tab=" + tab + " linkToPage=" + linkId + " limit=" + limit + " offset=" + offset
                            + (offsetFallback != offset ? " (and after fallback offset=" + offsetFallback + ")" : "")
            );
        }
    }

    @Test(
            description = "Collect rail ids from rail-hierarchy (railCategory=NEW, railType=RAIL|BRAND_RAIL|HERO_BANNER|DYNAMIC_RAIL), GET /rail/{id}"
    )
    @Story("GET /homescreen-service/pub/v1/rail/{railId}")
    public void homescreen_railById_forNewCategoryRailsFromHierarchy_ok() {
        requireHomescreenPrerequisites();

        int railOffset = readIntProperty("vrgo.homescreen.rail.by.id.offset", 0);
        int railLimit = readIntProperty("vrgo.homescreen.rail.by.id.limit", 100);
        boolean entitlementFiltering = readBooleanProperty(
                "vrgo.homescreen.rail.by.id.entitlement.filtering.enabled",
                false
        );

        Allure.parameter("environment", Environment.current().name());

        LinkedHashSet<String> railIds = collectNewCategoryRailIdsFromHomeTabsOrSkip(true);

        Allure.parameter("homescreen.rail.by.id.count", String.valueOf(railIds.size()));

        int success200 = 0;
        for (String railId : railIds) {
            Response rail = homescreenApi.getRailByIdRaw(railId, railOffset, railLimit, entitlementFiltering);
            AllureAttachmentUtils.attachJson("rail-by-id-" + railId, rail.asString());
            int code = rail.getStatusCode();
            if (code == 200) {
                success200++;
                Object status = rail.path("status");
                if (status instanceof Boolean) {
                    Assert.assertTrue((Boolean) status, "GET /rail/" + railId + " expected status true when present.");
                }
            } else if (code == 404) {
                Allure.parameter("rail.by.id.notFound." + railId, "404 (listed in hierarchy but no detail payload)");
            } else {
                Assert.fail("GET /rail/" + railId + " unexpected HTTP " + code + ": " + rail.asString());
            }
        }
        Assert.assertTrue(
                success200 >= 1,
                "Expected at least one GET /rail/{id} to return 200; checked " + railIds.size()
                        + " rail(s) matching NEW + RAIL|BRAND_RAIL|HERO_BANNER|DYNAMIC_RAIL (others may legitimately return 404)."
        );
    }

    @Test(
            description = "GET see-all for each discovered NEW-category rail id (offset/limit/sort from properties)"
    )
    @Story("GET /homescreen-service/pub/v1/see-all/{railId}")
    public void homescreen_seeAll_forNewCategoryRailIds_ok() {
        requireHomescreenPrerequisites();

        int seeOffset = readIntProperty("vrgo.homescreen.see.all.offset", 0);
        int seeLimit = readIntProperty("vrgo.homescreen.see.all.limit", 20);
        String sortProp = config.getProperty("vrgo.homescreen.see.all.sort");
        String sort = (sortProp == null || sortProp.isBlank()) ? "Recommended" : sortProp.strip();

        Allure.parameter("environment", Environment.current().name());
        Allure.parameter("homescreen.see.all.offset", String.valueOf(seeOffset));
        Allure.parameter("homescreen.see.all.limit", String.valueOf(seeLimit));
        Allure.parameter("homescreen.see.all.sort", sort);

        LinkedHashSet<String> railIds = collectNewCategoryRailIdsFromHomeTabsOrSkip(true);

        Allure.parameter("homescreen.see.all.rail.count", String.valueOf(railIds.size()));

        int success200 = 0;
        for (String railId : railIds) {
            Response see = homescreenApi.getSeeAllRaw(railId, seeOffset, seeLimit, sort);
            AllureAttachmentUtils.attachJson("see-all-" + railId, see.asString());
            int code = see.getStatusCode();
            if (code == 200) {
                success200++;
                Object status = see.path("status");
                if (status instanceof Boolean) {
                    Assert.assertTrue((Boolean) status, "GET see-all/" + railId + " expected status true when present.");
                }
            } else if (code == 404) {
                Allure.parameter("see.all.notFound." + railId, "404 (no see-all payload for this rail id)");
            } else {
                Assert.fail("GET see-all/" + railId + " unexpected HTTP " + code + ": " + see.asString());
            }
        }
        Assert.assertTrue(
                success200 >= 1,
                "Expected at least one GET see-all/{railId} to return 200; checked " + railIds.size()
                        + " rail id(s) from NEW + RAIL|BRAND_RAIL|HERO_BANNER|DYNAMIC_RAIL discovery (others may return 404)."
        );
    }

    @Test(description = "GET /pub/v1/purchased-rail — 200, status true, data present (results may be empty)")
    @Story("GET /homescreen-service/pub/v1/purchased-rail")
    public void homescreen_purchasedRail_returnsOk() {
        requireHomescreenPrerequisites();

        int limit = readIntProperty("vrgo.homescreen.purchased.rail.limit", 100);
        int offset = readIntProperty("vrgo.homescreen.purchased.rail.offset", 0);
        String contentType = config.getProperty("vrgo.homescreen.purchased.rail.content.type", "VOD,BOXSET");
        boolean isEntitlementEnabled = readBooleanProperty(
                "vrgo.homescreen.purchased.rail.is.entitlement.enabled",
                false
        );

        Allure.parameter("environment", Environment.current().name());
        Allure.parameter("homescreen.purchased.rail.limit", String.valueOf(limit));
        Allure.parameter("homescreen.purchased.rail.offset", String.valueOf(offset));
        Allure.parameter("homescreen.purchased.rail.contentType", contentType);
        Allure.parameter("homescreen.purchased.rail.isEntitlementEnabled", String.valueOf(isEntitlementEnabled));

        Response r = homescreenApi.getPurchasedRailRaw(limit, offset, contentType, isEntitlementEnabled);
        AllureAttachmentUtils.attachJson("purchased-rail-response", r.asString());

        r.then()
                .statusCode(200)
                .body("status", equalTo(true))
                .body("data", notNullValue());
    }

    @Test(
            description = "GET /pub/v1/rail-hierarchy/{railId}/details for each discovered NEW-category rail id"
    )
    @Story("GET /homescreen-service/pub/v1/rail-hierarchy/{railId}/details")
    public void homescreen_railHierarchyDetails_forDiscoveredRailIds_ok() {
        requireHomescreenPrerequisites();

        Allure.parameter("environment", Environment.current().name());

        LinkedHashSet<String> railIds = collectNewCategoryRailIdsFromHomeTabsOrSkip(false);

        Allure.parameter("homescreen.rail.hierarchy.details.rail.count", String.valueOf(railIds.size()));

        int success200 = 0;
        for (String railId : railIds) {
            Response d = homescreenApi.getRailHierarchyDetailsRaw(railId);
            AllureAttachmentUtils.attachJson("rail-hierarchy-details-" + railId, d.asString());
            int code = d.getStatusCode();
            if (code == 200) {
                success200++;
                Object status = d.path("status");
                if (status instanceof Boolean) {
                    Assert.assertTrue(
                            (Boolean) status,
                            "GET rail-hierarchy/" + railId + "/details expected status true when present."
                    );
                }
            } else if (code == 404) {
                Allure.parameter("rail.hierarchy.details.notFound." + railId, "404 (no details for this rail id)");
            } else {
                Assert.fail(
                        "GET rail-hierarchy/" + railId + "/details unexpected HTTP " + code + ": " + d.asString()
                );
            }
        }
        Assert.assertTrue(
                success200 >= 1,
                "Expected at least one GET rail-hierarchy/{railId}/details to return 200; checked " + railIds.size()
                        + " rail id(s) from NEW + RAIL|BRAND_RAIL|HERO_BANNER|DYNAMIC_RAIL discovery (others may return 404)."
        );
    }

    /**
     * Loads menu + HOME/Movies/TV Shows rail-hierarchy pages and returns unique rail ids matching
     * {@link #RAIL_CATEGORY_NEW} and {@link #RAIL_TYPES_FOR_BY_ID_DISCOVERY}.
     */
    private LinkedHashSet<String> collectNewCategoryRailIdsFromHomeTabsOrSkip(boolean attachHierarchyJson) {
        String platformId = config.getProperty("vrgo.homescreen.menu.platform.id");
        if (platformId == null || platformId.isBlank()) {
            throw new SkipException("Set vrgo.homescreen.menu.platform.id in environments/<env>.properties.");
        }

        int discoveryLimit = readIntProperty("vrgo.homescreen.rail.hierarchy.discovery.limit", 100);
        int discoveryOffset = readIntProperty("vrgo.homescreen.rail.hierarchy.discovery.offset", 0);
        Allure.parameter("homescreen.rail.discovery.limit", String.valueOf(discoveryLimit));
        Allure.parameter("homescreen.rail.discovery.offset", String.valueOf(discoveryOffset));

        Response menu = homescreenApi.getMenuListRaw(null);
        menu.then().statusCode(200).body("status", equalTo(true));

        List<Map<String, Object>> menuRows = menu.jsonPath().getList("data.menuDataList");
        if (menuRows == null || menuRows.isEmpty()) {
            throw new SkipException("Menu data.menuDataList was empty; cannot resolve linkToPage for tabs.");
        }

        Map<String, String> tabToLink = linkToPageByTabName(menuRows, RAIL_HIERARCHY_TAB_NAMES);
        for (String tab : RAIL_HIERARCHY_TAB_NAMES) {
            String linkId = tabToLink.get(tab);
            if (linkId == null || linkId.isBlank()) {
                throw new SkipException(
                        "No menu row with tabName \"" + tab + "\" and non-blank linkToPage. Found tabs: " + tabToLink.keySet()
                );
            }
        }

        LinkedHashSet<String> railIds = new LinkedHashSet<>();
        for (String tab : RAIL_HIERARCHY_TAB_NAMES) {
            String linkId = tabToLink.get(tab);
            Response hierarchy = homescreenApi.getRailHierarchyRaw(linkId, discoveryLimit, discoveryOffset);
            if (attachHierarchyJson) {
                AllureAttachmentUtils.attachJson(
                        "rail-hierarchy-discovery-" + tab.replace(' ', '-') + "-" + linkId,
                        hierarchy.asString()
                );
            }
            hierarchy.then().statusCode(200);
            List<?> results = resultsListFromRailResponse(hierarchy);
            Assert.assertNotNull(
                    results,
                    "Expected results list from rail-hierarchy for tab=" + tab + " linkToPage=" + linkId
            );
            for (String id : railIdsMatchingNewCategory(results)) {
                railIds.add(id);
            }
        }

        if (railIds.isEmpty()) {
            throw new SkipException(
                    "No rails matched railCategory=" + RAIL_CATEGORY_NEW + " and railType in "
                            + RAIL_TYPES_FOR_BY_ID_DISCOVERY
                            + " across HOME/Movies/TV Shows hierarchy pages (limit=" + discoveryLimit + ", offset="
                            + discoveryOffset + ")."
            );
        }
        return railIds;
    }

    private static List<String> railIdsMatchingNewCategory(List<?> results) {
        List<String> out = new ArrayList<>();
        if (results == null) {
            return out;
        }
        for (Object o : results) {
            if (!(o instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) o;
            if (!RAIL_CATEGORY_NEW.equals(stringField(row, "railCategory"))) {
                continue;
            }
            String railType = stringField(row, "railType");
            if (railType == null || !RAIL_TYPES_FOR_BY_ID_DISCOVERY.contains(railType)) {
                continue;
            }
            String id = railIdFromHierarchyRow(row);
            if (id != null && !id.isBlank()) {
                out.add(id.strip());
            }
        }
        return out;
    }

    private static String railIdFromHierarchyRow(Map<String, Object> row) {
        String id = stringField(row, "id");
        if (id != null && !id.isBlank()) {
            return id;
        }
        return stringField(row, "railId");
    }

    private static String stringField(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).strip();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    /**
     * Maps each requested tab name (case-insensitive match on trimmed {@code tabName}) to {@code linkToPage} string.
     */
    private static Map<String, String> linkToPageByTabName(List<Map<String, Object>> menuRows, String[] tabNames) {
        Map<String, String> wanted = new LinkedHashMap<>();
        Map<String, String> canonicalByLower = new LinkedHashMap<>();
        for (String t : tabNames) {
            String canonical = t.strip();
            wanted.put(canonical, null);
            canonicalByLower.put(canonical.toLowerCase(), canonical);
        }
        for (Map<String, Object> row : menuRows) {
            if (row == null) {
                continue;
            }
            Object nameObj = row.get("tabName");
            if (nameObj == null) {
                continue;
            }
            String tabName = String.valueOf(nameObj).strip();
            String canonical = canonicalByLower.get(tabName.toLowerCase());
            if (canonical == null) {
                continue;
            }
            if (wanted.get(canonical) != null) {
                continue;
            }
            String link = stringifyLinkToPage(row.get("linkToPage"));
            if (link != null && !link.isBlank()) {
                wanted.put(canonical, link);
            }
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (String t : tabNames) {
            String key = t.strip();
            out.put(key, wanted.get(key));
        }
        return out;
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

    private static boolean readBooleanProperty(String key, boolean defaultValue) {
        String s = config.getProperty(key);
        if (s == null || s.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(s.strip());
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
