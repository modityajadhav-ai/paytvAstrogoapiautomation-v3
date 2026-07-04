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
import java.util.Locale;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Happy-flow coverage for the VRGO subscriber-event Search History APIs (v1):
 * <ol>
 *   <li>DELETE all  — prepare a clean slate</li>
 *   <li>GET         — verify empty after clear</li>
 *   <li>POST        — add first searchQuery ({@code vrgo.search.history.add.search.query})</li>
 *   <li>GET         — verify first searchQuery is present</li>
 *   <li>POST        — add second searchQuery ({@code vrgo.search.history.add.search.query2}, optional)</li>
 *   <li>GET         — verify both searchQuerys are present</li>
 *   <li>DELETE /{keyword} — delete first searchQuery by path param</li>
 *   <li>GET         — verify first searchQuery is gone; second is still present</li>
 *   <li>DELETE all  — teardown / clean up</li>
 * </ol>
 * All paths are configurable via {@code vrgo.search.history.*} in the active environment file.
 */
@Feature("Search History")
public class SearchHistory extends BaseTest {

    /**
     * Whether the second POST was executed; guards assertions in step 6 and 8.
     */
    private boolean secondQueryPosted;

    // ── Step 1 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 0,
            description = "DELETE all search history — empty slate before the flow"
    )
    @Story("DELETE /subscriber-event-service/v1/search-history (clear all)")
    public void searchHistory_prepareClearAll() {
        requirePrerequisites();
        secondQueryPosted = false;

        Response r = searchHistoryApi.deleteAllSearchHistoryRaw();
        AllureAttachmentUtils.attachJson("search-history-prepare-clear", r.asString());
        // 400 is acceptable when no search history exists yet ("No search history found").
        r.then().statusCode(anyOf(is(200), is(204), is(400)));

        Allure.parameter("environment", Environment.current().name());
    }

    // ── Step 2 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 5,
            dependsOnMethods = "searchHistory_prepareClearAll",
            description = "GET search history after DELETE all — list must be empty"
    )
    @Story("GET /subscriber-event-service/v1/search-history")
    public void searchHistory_getAfterClearIsEmpty() {
        requirePrerequisites();

        Response r = searchHistoryApi.getSearchHistoryRaw();
        AllureAttachmentUtils.attachJson("search-history-get-after-clear", r.asString());
        // 400 ("No search history found") is treated as empty — same outcome as 200 with an empty list.
        r.then().statusCode(anyOf(is(200), is(400)));

        int count = 0;
        if (r.statusCode() == 200) {
            List<?> items = r.jsonPath().getList("data");
            count = (items == null) ? 0 : items.size();
        }
        Allure.parameter("search.history.countAfterClear", String.valueOf(count));

        Assert.assertEquals(count, 0,
                "Search history must be empty after DELETE all; found " + count + " item(s).");
    }

    // ── Step 3 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 10,
            dependsOnMethods = "searchHistory_getAfterClearIsEmpty",
            description = "POST first searchQuery — adds one entry to search history"
    )
    @Story("POST /subscriber-event-service/v1/search-history")
    public void searchHistory_postFirstSearchQuery() {
        requirePrerequisites();

        String query1 = resolveQuery1();
        Allure.parameter("search.history.searchQuery1", query1);

        Response r = searchHistoryApi.postSearchHistoryRaw(query1);
        AllureAttachmentUtils.attachJson("search-history-post-query1", r.asString());
        r.then().statusCode(200);
    }

    // ── Step 4 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 15,
            dependsOnMethods = "searchHistory_postFirstSearchQuery",
            description = "GET search history — first searchQuery must be present after POST"
    )
    @Story("GET /subscriber-event-service/v1/search-history")
    public void searchHistory_getVerifyFirstQueryPresent() {
        requirePrerequisites();

        Response r = searchHistoryApi.getSearchHistoryRaw();
        AllureAttachmentUtils.attachJson("search-history-get-after-post-query1", r.asString());

        r.then()
                .statusCode(200)
                .body("data", notNullValue());

        String query1 = resolveQuery1();
        Assert.assertTrue(
                responseContainsQuery(r, query1),
                "GET search history must contain '" + query1 + "' after POST."
        );
    }

    // ── Step 5 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 20,
            dependsOnMethods = "searchHistory_getVerifyFirstQueryPresent",
            description = "POST second searchQuery — adds a second entry to search history (skipped when not configured)"
    )
    @Story("POST /subscriber-event-service/v1/search-history")
    public void searchHistory_postSecondSearchQuery() {
        requirePrerequisites();
        secondQueryPosted = false;

        String query2 = config.getProperty("vrgo.search.history.add.search.query2");
        String query1 = resolveQuery1();

        if (!isConfiguredQuery(query2) || query2.strip().equalsIgnoreCase(query1)) {
            Allure.parameter("search.history.secondQuerySkipped", "true");
            return;
        }

        sleepBetweenPosts();
        Allure.parameter("search.history.searchQuery2", query2.strip());

        Response r = searchHistoryApi.postSearchHistoryRaw(query2.strip());
        AllureAttachmentUtils.attachJson("search-history-post-query2", r.asString());
        r.then().statusCode(200);

        secondQueryPosted = true;
    }

    // ── Step 6 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 25,
            dependsOnMethods = "searchHistory_postSecondSearchQuery",
            description = "GET search history — both searchQuerys must be present (second only when posted)"
    )
    @Story("GET /subscriber-event-service/v1/search-history")
    public void searchHistory_getVerifyBothQueriesPresent() {
        requirePrerequisites();

        Response r = searchHistoryApi.getSearchHistoryRaw();
        AllureAttachmentUtils.attachJson("search-history-get-after-both-posts", r.asString());

        r.then()
                .statusCode(200)
                .body("data", notNullValue());

        String query1 = resolveQuery1();
        Assert.assertTrue(
                responseContainsQuery(r, query1),
                "GET search history must still contain first query '" + query1 + "'."
        );

        if (secondQueryPosted) {
            String query2 = config.getProperty("vrgo.search.history.add.search.query2");
            Assert.assertTrue(
                    responseContainsQuery(r, query2.strip()),
                    "GET search history must contain second query '" + query2.strip() + "' after POST."
            );
        }
    }

    // ── Step 7 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 30,
            dependsOnMethods = "searchHistory_getVerifyBothQueriesPresent",
            description = "DELETE specific keyword — removes first searchQuery by path param"
    )
    @Story("DELETE /subscriber-event-service/v1/search-history/{keyword}")
    public void searchHistory_deleteFirstSearchQuery() {
        requirePrerequisites();

        String query1 = resolveQuery1();
        Allure.parameter("search.history.deletedKeyword", query1);

        Response r = searchHistoryApi.deleteSearchHistoryKeywordRaw(query1);
        AllureAttachmentUtils.attachJson("search-history-delete-query1", r.asString());
        r.then().statusCode(anyOf(is(200), is(204)));
    }

    // ── Step 8 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 35,
            dependsOnMethods = "searchHistory_deleteFirstSearchQuery",
            description = "GET search history — deleted query must be absent; second query still present when posted"
    )
    @Story("GET /subscriber-event-service/v1/search-history")
    public void searchHistory_getVerifyDeletedQueryAbsent() {
        requirePrerequisites();

        Response r = searchHistoryApi.getSearchHistoryRaw();
        AllureAttachmentUtils.attachJson("search-history-get-after-delete-query1", r.asString());
        // 400 ("No search history found") is acceptable when the list is now empty (only one query was posted).
        r.then().statusCode(anyOf(is(200), is(400)));

        String query1 = resolveQuery1();
        Assert.assertFalse(
                responseContainsQuery(r, query1),
                "Deleted searchQuery '" + query1 + "' must not appear in GET search history after DELETE."
        );

        if (secondQueryPosted) {
            String query2 = config.getProperty("vrgo.search.history.add.search.query2");
            Assert.assertTrue(
                    responseContainsQuery(r, query2.strip()),
                    "Second searchQuery '" + query2.strip() + "' must still be present after deleting only the first."
            );
        }
    }

    // ── Step 9 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 40,
            dependsOnMethods = "searchHistory_getVerifyDeletedQueryAbsent",
            alwaysRun = true,
            description = "Teardown: DELETE all search history"
    )
    @Story("DELETE /subscriber-event-service/v1/search-history (teardown)")
    public void searchHistory_teardownClearAll() {
        requirePrerequisites();

        Response r = searchHistoryApi.deleteAllSearchHistoryRaw();
        AllureAttachmentUtils.attachJson("search-history-teardown-clear", r.asString());
        // 400 is acceptable when no history remains to delete ("No search history found").
        r.then().statusCode(anyOf(is(200), is(204), is(400)));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String resolveQuery1() {
        String q = config.getProperty("vrgo.search.history.add.search.query");
        if (!isConfiguredQuery(q)) {
            throw new SkipException(
                    "Set vrgo.search.history.add.search.query in environments/<env>.properties (non-blank, not REPLACE*).");
        }
        return q.strip();
    }

    /**
     * Returns {@code true} when the response body contains the given searchQuery inside
     * {@code data[*].searchQuery}. Falls back to a raw substring scan if the JSON path is absent,
     * which keeps the check working even if the server wraps data differently.
     */
    private static boolean responseContainsQuery(Response r, String searchQuery) {
        List<String> queries = r.jsonPath().getList("data.searchQuery");
        if (queries != null) {
            for (String q : queries) {
                if (searchQuery.equalsIgnoreCase(q)) {
                    return true;
                }
            }
            return false;
        }
        return r.asString().contains(searchQuery);
    }

    private static boolean isConfiguredQuery(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return !value.strip().toUpperCase(Locale.ROOT).startsWith("REPLACE");
    }

    private void sleepBetweenPosts() {
        int ms = readIntProperty("vrgo.search.history.between.posts.sleep.ms", 0);
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SkipException("Interrupted during vrgo.search.history.between.posts.sleep.ms", e);
        }
    }

    private void requirePrerequisites() {
        if (searchHistoryApi == null) {
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
