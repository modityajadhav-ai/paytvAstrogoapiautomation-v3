package com.automation.api.tests;

import com.automation.api.base.BaseTest;
import com.automation.api.config.Environment;
import com.automation.api.util.AllureAttachmentUtils;
import com.automation.api.util.FavouritesListJsonSupport;
import io.qameta.allure.Allure;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * VRGO favourites / watchlist: clear list, POST structured content (movie, series, channel, boxset),
 * optional negative season POST (expect 400), GET favourite-channels list, negative TV-show POST (expect 400),
 * GET full favourites verification, teardown clear.
 * <p>
 * POST {@code /v3/favourites?region=...} body uses API content types: {@code MOVIE}, {@code VOD} (series),
 * {@code LIVE} (channel), {@code BOXSET}. Season and TV episode favourite attempts must return {@code 400} and
 * must not appear on GET. Uses {@link com.automation.api.client.FavouritesApiClient} and
 * {@code vrgo.favourites.add.*} / {@code vrgo.favourites.negative.*} in environment files.
 */
@Feature("Watchlist / favourites")
public class WatchlistFavourite extends BaseTest {

    @Test(
            priority = 0,
            description = "GET favourites first page and DELETE each item (empty slate before POST flow)"
    )
    @Story("DELETE /subscriber-event-service/v3/favourites/{contentId}")
    public void favourites_flow_prepareClearFirstPage() {
        requireFavouritesPrerequisites();
        clearFavouritesFirstPageInternal("favourites-prepare-clear");
    }

    @Test(
            priority = 5,
            dependsOnMethods = "favourites_flow_prepareClearFirstPage",
            description = "POST favourites — MOVIE, VOD (series), LIVE (channel), BOXSET; optional VOD (season, expect 400)"
    )
    @Story("POST /subscriber-event-service/v3/favourites")
    public void favourites_flow_postMovieSeriesChannelBoxsetSeason() {
        requireFavouritesPrerequisites();

        String region = favouritesRegion();
        String movieId = config.getProperty("vrgo.favourites.add.movie.content.id");
        String seriesId = config.getProperty("vrgo.favourites.add.series.content.id");
        String channelId = config.getProperty("vrgo.favourites.add.channel.content.id");
        String boxsetId = config.getProperty("vrgo.favourites.add.boxset.content.id");
        String seasonId = config.getProperty("vrgo.favourites.add.season.content.id");

        if (!isConfiguredId(movieId) || !isConfiguredId(seriesId) || !isConfiguredId(channelId) || !isConfiguredId(boxsetId)) {
            throw new SkipException(
                    "Set vrgo.favourites.add.movie.content.id, .series.content.id, .channel.content.id, and .boxset.content.id "
                            + "in environments/<env>.properties (non-blank, not REPLACE*)."
            );
        }

        postFavouriteExpect200(region, movieId.strip(), "MOVIE", "favourites-post-movie");
        sleepBetweenFavouritePosts();
        postFavouriteExpect200(region, seriesId.strip(), "VOD", "favourites-post-series");
        sleepBetweenFavouritePosts();
        postFavouriteExpect200(region, channelId.strip(), "LIVE", "favourites-post-channel");
        sleepBetweenFavouritePosts();
        postFavouriteExpect200(region, boxsetId.strip(), "BOXSET", "favourites-post-boxset");

        if (isConfiguredId(seasonId)) {
            String s = seasonId.strip();
            if (!s.equalsIgnoreCase(seriesId.strip())) {
                sleepBetweenFavouritePosts();
                postFavouriteExpect400(region, s, "VOD", "favourites-post-season-negative");
            }
        }
    }

    @Test(
            priority = 7,
            dependsOnMethods = "favourites_flow_postMovieSeriesChannelBoxsetSeason",
            description = "GET /favourites/channels — response lists configured favourite channel id"
    )
    @Story("GET /subscriber-event-service/v3/favourites/channels")
    public void favourites_flow_getFavouriteChannelsListIncludesConfiguredChannel() {
        requireFavouritesPrerequisites();

        String channelId = config.getProperty("vrgo.favourites.add.channel.content.id");
        if (!isConfiguredId(channelId)) {
            throw new SkipException("Set vrgo.favourites.add.channel.content.id for the favourite-channels GET test.");
        }

        Response r = favouritesApi.getFavouritesChannelsRaw();
        AllureAttachmentUtils.attachJson("favourites-channels-response", r.asString());

        r.then()
                .statusCode(200)
                .body("status", equalTo(true))
                .body("data", notNullValue());

        List<Map<String, Object>> channelRows = FavouritesListJsonSupport.listRowsFromChannelsGetResponse(r);
        Allure.parameter("favourites.channels.parsedRowCount", String.valueOf(channelRows.size()));

        Assert.assertTrue(
                FavouritesListJsonSupport.channelsResponseContainsContentId(r, channelId.strip()),
                "GET favourite channels should include configured channel id " + channelId.strip()
                        + "; parsedRows=" + channelRows.size()
        );
    }

    @Test(
            priority = 10,
            dependsOnMethods = "favourites_flow_getFavouriteChannelsListIncludesConfiguredChannel",
            description = "POST TV show (episode) to favourites — expect HTTP 400"
    )
    @Story("POST /subscriber-event-service/v3/favourites (negative)")
    public void favourites_flow_postTvShow_expectHttp400() {
        requireFavouritesPrerequisites();

        String region = favouritesRegion();
        String tvId = config.getProperty("vrgo.favourites.negative.tvshow.content.id");
        if (!isConfiguredId(tvId)) {
            throw new SkipException("Set vrgo.favourites.negative.tvshow.content.id for the negative TV test.");
        }
        String tvType = firstNonBlank(config.getProperty("vrgo.favourites.negative.tvshow.content.type"), "TV_SHOW");
        postFavouriteExpect400(region, tvId.strip(), tvType.strip(), "favourites-negative-tvshow-response");
    }

    @Test(
            priority = 15,
            dependsOnMethods = "favourites_flow_postTvShow_expectHttp400",
            description = "GET favourites — each successful POST id present; negative TV episode and season ids absent"
    )
    @Story("GET /subscriber-event-service/v3/favourites")
    public void favourites_flow_getVerifyAddedIdsExcludeTvShow() {
        requireFavouritesPrerequisites();

        Response r = getFavouritesListAndAttachAllure("favourites-verify-after-posts");
        r.then()
                .statusCode(200)
                .body("status", equalTo(true))
                .body("data", notNullValue());

        assertPresentIfConfigured(r, "movie", "vrgo.favourites.add.movie.content.id");
        assertPresentIfConfigured(r, "series", "vrgo.favourites.add.series.content.id");
        assertPresentIfConfigured(r, "channel", "vrgo.favourites.add.channel.content.id");
        assertPresentIfConfigured(r, "boxset", "vrgo.favourites.add.boxset.content.id");

        assertAbsentIfConfigured(r, "TV episode", "vrgo.favourites.negative.tvshow.content.id");

        String seasonId = config.getProperty("vrgo.favourites.add.season.content.id");
        String seriesId = config.getProperty("vrgo.favourites.add.series.content.id");
        if (isConfiguredId(seasonId) && isConfiguredId(seriesId)
                && !seasonId.strip().equalsIgnoreCase(seriesId.strip())) {
            assertAbsentIfConfigured(r, "season", "vrgo.favourites.add.season.content.id");
        }
    }

    @Test(
            priority = 20,
            dependsOnMethods = "favourites_flow_getVerifyAddedIdsExcludeTvShow",
            alwaysRun = true,
            description = "Teardown: GET favourites first page and DELETE each item"
    )
    @Story("DELETE /subscriber-event-service/v3/favourites/{contentId}")
    public void favourites_flow_teardownClearFirstPage() {
        requireFavouritesPrerequisites();
        clearFavouritesFirstPageInternal("favourites-teardown-clear");
    }

    private void postFavouriteExpect200(String region, String contentId, String contentType, String attachmentLabel) {
        postFavouriteAndAssertStatus(region, contentId, contentType, attachmentLabel, 200);
    }

    private void postFavouriteExpect400(String region, String contentId, String contentType, String attachmentLabel) {
        postFavouriteAndAssertStatus(region, contentId, contentType, attachmentLabel, 400);
    }

    private void postFavouriteAndAssertStatus(
            String region, String contentId, String contentType, String attachmentLabel, int expectedStatus) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("contentId", contentId);
        body.put("contentType", contentType);

        Allure.parameter(attachmentLabel + ".contentId", contentId);
        Allure.parameter(attachmentLabel + ".contentType", contentType);

        Response r = favouritesApi.postFavouriteRaw(region, body);
        AllureAttachmentUtils.attachJson(attachmentLabel, r.asString());
        r.then().statusCode(expectedStatus);
    }

    private void assertPresentIfConfigured(Response r, String label, String propertyKey) {
        String id = config.getProperty(propertyKey);
        if (!isConfiguredId(id)) {
            return;
        }
        Assert.assertTrue(
                FavouritesListJsonSupport.responseContainsContentId(r, id.strip()),
                "Favourites GET should list " + label + " content id from " + propertyKey + ": " + id.strip()
        );
    }

    private void assertAbsentIfConfigured(Response r, String label, String propertyKey) {
        String id = config.getProperty(propertyKey);
        if (!isConfiguredId(id)) {
            return;
        }
        Assert.assertTrue(
                FavouritesListJsonSupport.responseExcludesContentId(r, id.strip()),
                "Favourites list must not include " + label + " content id from " + propertyKey + ": " + id.strip()
        );
    }

    private void clearFavouritesFirstPageInternal(String attachmentPrefix) {
        Response getResponse = getFavouritesListAndAttachAllure(attachmentPrefix);
        getResponse.then().statusCode(200).body("status", equalTo(true));

        List<Map<String, Object>> rows = FavouritesListJsonSupport.listRowsFromGetResponse(getResponse);
        if (rows.isEmpty()) {
            Allure.parameter("favourites.delete.attempted", "0");
            return;
        }

        Set<String> uniqueIds = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String id = FavouritesListJsonSupport.rowContentId(row);
            if (id != null && !id.isBlank()) {
                uniqueIds.add(id.strip());
            }
        }

        int attempted = 0;
        for (String contentId : uniqueIds) {
            Response del = favouritesApi.deleteFavouriteRaw(contentId);
            del.then().statusCode(anyOf(is(200), is(204)));
            attempted++;
        }
        Allure.parameter("favourites.delete.attempted", String.valueOf(attempted));
        Allure.parameter("favourites.delete.uniqueContentIds", String.valueOf(uniqueIds.size()));
    }

    private void sleepBetweenFavouritePosts() {
        int ms = readIntProperty("vrgo.favourites.between.posts.sleep.ms", 0);
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SkipException("Interrupted during vrgo.favourites.between.posts.sleep.ms", e);
        }
    }

    private String favouritesRegion() {
        return firstNonBlank(config.getProperty("vrgo.favourites.region"), "Malaysia");
    }

    private Response getFavouritesListAndAttachAllure(String attachmentName) {
        int offset = readIntProperty("vrgo.favourites.offset", 0);
        int limit = readIntProperty("vrgo.favourites.limit", 100);
        String contentTypes = firstNonBlank(config.getProperty("vrgo.favourites.content.types"), "LIVE,VOD");
        String region = favouritesRegion();
        boolean ent = readBooleanProperty("vrgo.favourites.is.entitlement.enabled", false);

        Allure.parameter("environment", Environment.current().name());
        Allure.parameter("favourites.offset", String.valueOf(offset));
        Allure.parameter("favourites.limit", String.valueOf(limit));
        Allure.parameter("favourites.contentTypes", contentTypes);
        Allure.parameter("favourites.region", region);
        Allure.parameter("favourites.isEntitlementEnabled", String.valueOf(ent));

        Response r = favouritesApi.getFavouritesRaw(offset, limit, contentTypes, region, ent);
        AllureAttachmentUtils.attachJson(attachmentName, r.asString());
        return r;
    }

    private void requireFavouritesPrerequisites() {
        if (favouritesApi == null) {
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

    private static boolean isConfiguredId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return !id.strip().toUpperCase(Locale.ROOT).contains("REPLACE");
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

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.strip();
        }
        return b;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
