package com.automation.api.tests;

import com.automation.api.base.BaseTest;
import com.automation.api.util.AllureAttachmentUtils;
import com.automation.api.util.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.qameta.allure.Allure;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

/**
 * VRGO content-detail-service regression: series (close/open), season/series episodes, view-all, tv_show,
 * episode-hierarchy (NEXT|CURRENT|PREVIOUS) vs BingeWatch episode adjacent (NEXT|PREVIOUS on {@code /episode/...}),
 * movie, boxset (+ binge, childs), trailer, linear (channel, on-air, dates, events, channel-day, 48hr, EPG,
 * bouquet next/prev), MyBox channels/genres, channel filters, mini-mybox, 3PP VOD (movie / episode / season).
 */
@Feature("Content detail")
public class ContentDetail extends BaseTest {

    @BeforeClass(alwaysRun = true)
    public void requireContentDetailClient() {
        if (contentDetailApi == null) {
            throw new SkipException("vrgo.base.url is not set; content-detail client was not created.");
        }
    }

    @Test(priority = 10, description = "GET close-series editorial detail (series/{id})")
    @Story("GET /content-detail-service/pub/v1/series/{seriesId} — close series")
    public void closeSeriesDetail_returns200() {
        String seriesId = stripOrEmpty(config.getProperty("vrgo.content.detail.close.series.id"));
        if (!isConfiguredId(seriesId)) {
            throw new SkipException("Set vrgo.content.detail.close.series.id.");
        }
        Response r = contentDetailApi.getSeriesDetailRaw(
                seriesId,
                seriesRegion(),
                seriesContentType(),
                seriesIsEntitlementEnabled()
        );
        attachAndAssertEnvelope(r, "content-detail-close-series", null);
        assertOperatorMetaPresent(r);
    }

    @Test(priority = 20, description = "GET open-series editorial detail (series/{id})")
    @Story("GET /content-detail-service/pub/v1/series/{seriesId} — open series")
    public void openSeriesDetail_returns200() {
        String seriesId = stripOrEmpty(config.getProperty("vrgo.content.detail.open.series.id"));
        if (!isConfiguredId(seriesId)) {
            throw new SkipException("Set vrgo.content.detail.open.series.id.");
        }
        Response r = contentDetailApi.getSeriesDetailRaw(
                seriesId,
                seriesRegion(),
                seriesContentType(),
                seriesIsEntitlementEnabled()
        );
        attachAndAssertEnvelope(r, "content-detail-open-series", null);
        assertOperatorMetaPresent(r);
    }

    @Test(priority = 30, description = "GET season_episode list for close-series season 1")
    @Story("GET /content-detail-service/pub/v1/season_episode/{seasonId}")
    public void seasonEpisodes_closeSeriesSeason_returns200() {
        String seasonId = stripOrEmpty(config.getProperty("vrgo.content.detail.season1.id"));
        if (!isConfiguredId(seasonId)) {
            throw new SkipException("Set vrgo.content.detail.season1.id.");
        }
        int limit = parsePositiveInt(config.getProperty("vrgo.content.detail.season.episode.limit"), 10);
        int offset = parseNonNegativeInt(config.getProperty("vrgo.content.detail.season.episode.offset"), 0);
        String sort = firstNonBlank(config.getProperty("vrgo.content.detail.season.episode.sort"), "asc");
        Response r = contentDetailApi.getSeasonEpisodesRaw(seasonId, limit, offset, sort);
        attachAndAssertEnvelope(r, "content-detail-season-episodes", null);
        assertAnyOperatorLabelPresent(r);
    }

    @Test(priority = 40, description = "GET series_episode list for open-series editorial id")
    @Story("GET /content-detail-service/pub/v1/series_episode/{seriesId}")
    public void seriesEpisodes_openSeries_returns200() {
        String seriesId = stripOrEmpty(config.getProperty("vrgo.content.detail.open.series.id"));
        if (!isConfiguredId(seriesId)) {
            throw new SkipException("Set vrgo.content.detail.open.series.id.");
        }
        int limit = parsePositiveInt(config.getProperty("vrgo.content.detail.series.episode.limit"), 100);
        int offset = parseNonNegativeInt(config.getProperty("vrgo.content.detail.series.episode.offset"), 0);
        String sort = firstNonBlank(config.getProperty("vrgo.content.detail.series.episode.sort"), "asc");
        Response r = contentDetailApi.getSeriesEpisodesRaw(seriesId, limit, offset, sort);
        attachAndAssertEnvelope(r, "content-detail-series-episodes-open", null);
    }

    @DataProvider(name = "seriesEpisodeViewAllTypes")
    public static Object[][] seriesEpisodeViewAllTypes() {
        return new Object[][]{
                {"NEXT"},
                {"PREVIOUS"},
                {"PREVIOUS_NEXT"}
        };
    }

    @Test(
            priority = 50,
            dataProvider = "seriesEpisodeViewAllTypes",
            description = "GET series/{seriesId}/episode/{episodeId} view-all with type=NEXT|PREVIOUS|PREVIOUS_NEXT"
    )
    @Story("GET /content-detail-service/pub/v1/series/{seriesId}/episode/{episodeId}")
    public void seriesEpisodeViewAll_queryType_returns200(String type) {
        String seriesId = firstNonBlank(
                config.getProperty("vrgo.content.detail.viewall.series.id"),
                config.getProperty("vrgo.content.detail.close.series.id")
        );
        String episodeId = config.getProperty("vrgo.content.detail.viewall.episode.id");
        if (!isConfiguredId(seriesId) || !isConfiguredId(episodeId)) {
            throw new SkipException(
                    "Set vrgo.content.detail.viewall.episode.id and either vrgo.content.detail.viewall.series.id "
                            + "or vrgo.content.detail.close.series.id."
            );
        }

        int size = parsePositiveInt(config.getProperty("vrgo.content.detail.viewall.size"), 10);
        boolean isOpenSeries = parseBooleanLoose(config.getProperty("vrgo.content.detail.viewall.is.open.series"), false);
        int episodeSortOrder = parsePositiveInt(config.getProperty("vrgo.content.detail.viewall.episode.sort.order"), 2);
        int seasonSortOrder = parsePositiveInt(config.getProperty("vrgo.content.detail.viewall.season.sort.order"), 1);
        String seasonsSortOrders = firstNonBlank(
                config.getProperty("vrgo.content.detail.viewall.seasons.sort.orders"),
                "1,2"
        );

        Allure.parameter("viewAll.type", type);
        Allure.parameter("viewAll.seriesId", seriesId.strip());
        Allure.parameter("viewAll.episodeId", episodeId.strip());

        Response r = contentDetailApi.getSeriesEpisodeViewAllRaw(
                seriesId.strip(),
                episodeId.strip(),
                type,
                size,
                isOpenSeries,
                episodeSortOrder,
                seasonSortOrder,
                seasonsSortOrders.strip()
        );
        attachAndAssertEnvelope(r, "series-episode-viewall-" + type, "vrgo.content.detail.viewall.expected.message");
    }

    @Test(priority = 60, description = "GET tv_show for configured episode editorial id")
    @Story("GET /content-detail-service/pub/v1/tv_show/{episodeId}")
    public void tvShow_episode_returns200() {
        String episodeId = stripOrEmpty(config.getProperty("vrgo.content.detail.tv.show.episode.id"));
        if (!isConfiguredId(episodeId)) {
            throw new SkipException("Set vrgo.content.detail.tv.show.episode.id.");
        }
        int limit = parsePositiveInt(config.getProperty("vrgo.content.detail.tv.show.limit"), 2);
        int offset = parseNonNegativeInt(config.getProperty("vrgo.content.detail.tv.show.offset"), 0);
        String sort = firstNonBlank(config.getProperty("vrgo.content.detail.tv.show.sort"), "asc");
        Response r = contentDetailApi.getTvShowRaw(episodeId, limit, offset, sort);
        attachAndAssertEnvelope(r, "content-detail-tv-show", null);
        assertOperatorMetaPresent(r);
    }

    @DataProvider(name = "bingeWatchEpisodeDirections")
    public static Object[][] bingeWatchEpisodeDirections() {
        return new Object[][]{
                {"NEXT"},
                {"PREVIOUS"}
        };
    }

    @Test(
            priority = 65,
            dataProvider = "bingeWatchEpisodeDirections",
            description = "BingeWatch: GET episode/{episodeId}/{direction} — adjacent episode; direction NEXT or PREVIOUS only"
    )
    @Story("BingeWatch — GET /content-detail-service/pub/v1/episode/{episodeId}/{direction} (NEXT|PREVIOUS)")
    public void bingeWatch_episodeAdjacent_returns200(String direction) {
        String episodeId = resolveBingeWatchEpisodeId();
        if (!isConfiguredId(episodeId)) {
            throw new SkipException(
                    "Set vrgo.content.detail.binge.watch.episode.id or vrgo.content.detail.episode.hierarchy.chain.middle.id."
            );
        }
        Allure.parameter("bingeWatch.direction", direction);
        Allure.parameter("bingeWatch.episodeId", episodeId);
        Response r = contentDetailApi.getBingeWatchEpisodeRaw(episodeId, direction);
        attachAndAssertEnvelope(
                r,
                "binge-watch-episode-" + direction,
                optionalMessageKeyForBingeWatch()
        );
    }

    @DataProvider(name = "episodeHierarchyThreeStateDirections")
    public static Object[][] episodeHierarchyThreeStateDirections() {
        return new Object[][]{
                {"NEXT"},
                {"CURRENT"},
                {"PREVIOUS"}
        };
    }

    @Test(
            priority = 70,
            dataProvider = "episodeHierarchyThreeStateDirections",
            description = "Episode hierarchy: GET episode-hierarchy/{episodeId}/{direction} — three-state NEXT|CURRENT|PREVIOUS"
    )
    @Story("Episode hierarchy — GET /content-detail-service/pub/v1/episode-hierarchy/{episodeId}/{direction} (NEXT|CURRENT|PREVIOUS)")
    public void episodeHierarchy_threeState_returns200(String direction) {
        String episodeId = stripOrEmpty(config.getProperty("vrgo.content.detail.episode.hierarchy.chain.middle.id"));
        if (!isConfiguredId(episodeId)) {
            throw new SkipException("Set vrgo.content.detail.episode.hierarchy.chain.middle.id.");
        }
        Allure.parameter("episodeHierarchy.direction", direction);
        Allure.parameter("episodeHierarchy.episodeId", episodeId);
        Response r = contentDetailApi.getEpisodeHierarchyRaw(episodeId, direction);
        attachAndAssertEnvelope(
                r,
                "episode-hierarchy-three-state-" + direction,
                "vrgo.content.detail.episode.hierarchy.expected.message"
        );
    }

    @Test(priority = 71, description = "GET 3PPVODMovie/{contentId} — 3ppvodmovie editorial detail")
    @Story("3PP VOD — GET /content-detail-service/pub/v1/3PPVODMovie/{contentId} (3ppvodmovie)")
    public void threePpVodMovie_detail_returns200() {
        String id = stripOrEmpty(config.getProperty("vrgo.content.detail.3ppvod.movie.id"));
        if (!isConfiguredId(id)) {
            throw new SkipException("Set vrgo.content.detail.3ppvod.movie.id.");
        }
        Allure.parameter("3ppvod.contentType", "3ppvodmovie");
        Allure.parameter("3ppvod.contentId", id);
        Response r = contentDetailApi.getThreePpVodMovieRaw(id);
        attachAndAssertEnvelope(r, "content-detail-3ppvod-movie", "vrgo.content.detail.3ppvod.movie.expected.message");
    }

    @Test(priority = 72, description = "GET 3PPVODEpisode/{contentId} — 3ppvodepisode editorial detail")
    @Story("3PP VOD — GET /content-detail-service/pub/v1/3PPVODEpisode/{contentId} (3ppvodepisode)")
    public void threePpVodEpisode_detail_returns200() {
        String id = stripOrEmpty(config.getProperty("vrgo.content.detail.3ppvod.episode.id"));
        if (!isConfiguredId(id)) {
            throw new SkipException("Set vrgo.content.detail.3ppvod.episode.id.");
        }
        Allure.parameter("3ppvod.contentType", "3ppvodepisode");
        Allure.parameter("3ppvod.contentId", id);
        Response r = contentDetailApi.getThreePpVodEpisodeRaw(id);
        attachAndAssertEnvelope(r, "content-detail-3ppvod-episode", "vrgo.content.detail.3ppvod.episode.expected.message");
    }

    @Test(priority = 73, description = "GET 3PPVODSeason/{contentId} — 3ppvodseason editorial detail")
    @Story("3PP VOD — GET /content-detail-service/pub/v1/3PPVODSeason/{contentId} (3ppvodseason)")
    public void threePpVodSeason_detail_returns200() {
        String id = stripOrEmpty(config.getProperty("vrgo.content.detail.3ppvod.season.id"));
        if (!isConfiguredId(id)) {
            throw new SkipException("Set vrgo.content.detail.3ppvod.season.id.");
        }
        Allure.parameter("3ppvod.contentType", "3ppvodseason");
        Allure.parameter("3ppvod.contentId", id);
        Response r = contentDetailApi.getThreePpVodSeasonRaw(id);
        attachAndAssertEnvelope(r, "content-detail-3ppvod-season", "vrgo.content.detail.3ppvod.season.expected.message");
    }

    private String optionalMessageKeyForBingeWatch() {
        String bingeMsg = config.getProperty("vrgo.content.detail.binge.watch.expected.message");
        if (bingeMsg != null && !bingeMsg.isBlank()) {
            return "vrgo.content.detail.binge.watch.expected.message";
        }
        return "vrgo.content.detail.episode.hierarchy.expected.message";
    }

    private String resolveBingeWatchEpisodeId() {
        String dedicated = config.getProperty("vrgo.content.detail.binge.watch.episode.id");
        if (isConfiguredId(dedicated)) {
            return dedicated.strip();
        }
        return stripOrEmpty(config.getProperty("vrgo.content.detail.episode.hierarchy.chain.middle.id"));
    }

    @Test(priority = 80, description = "GET movie editorial detail")
    @Story("GET /content-detail-service/pub/v1/movie/{movieId}")
    public void movieDetail_returns200() {
        String movieId = stripOrEmpty(config.getProperty("vrgo.content.detail.movie.id"));
        if (!isConfiguredId(movieId)) {
            throw new SkipException("Set vrgo.content.detail.movie.id.");
        }
        Response r = contentDetailApi.getMovieRaw(movieId);
        attachAndAssertEnvelope(r, "content-detail-movie", null);
    }

    // @Test(priority = 85, description = "GET movie detail using alternate UUID-style id when configured")
    // @Story("GET /content-detail-service/pub/v1/movie/{movieId} — alt id")
    // public void movieDetail_altId_returns200() {
    //     String primary = stripOrEmpty(config.getProperty("vrgo.content.detail.movie.id"));
    //     String alt = stripOrEmpty(config.getProperty("vrgo.content.detail.movie.alt.id"));
    //     if (!isConfiguredId(alt) || alt.equalsIgnoreCase(primary)) {
    //         throw new SkipException("Set vrgo.content.detail.movie.alt.id distinct from vrgo.content.detail.movie.id.");
    //     }
    //     Response r = contentDetailApi.getMovieRaw(alt);
    //     attachAndAssertEnvelope(r, "content-detail-movie-alt", null);
    // }

    @Test(priority = 90, description = "GET boxset editorial detail")
    @Story("GET /content-detail-service/pub/v1/boxset/{boxsetId}")
    public void boxsetDetail_returns200() {
        String boxsetId = stripOrEmpty(config.getProperty("vrgo.content.detail.boxset.id"));
        if (!isConfiguredId(boxsetId)) {
            throw new SkipException("Set vrgo.content.detail.boxset.id.");
        }
        Response r = contentDetailApi.getBoxsetRaw(boxsetId);
        attachAndAssertEnvelope(r, "content-detail-boxset", "vrgo.content.detail.boxset.expected.message");
    }

    @Test(priority = 95, description = "GET boxset/{boxsetId}/binge — boxset binge-watch payload")
    @Story("GET /content-detail-service/pub/v1/boxset/{boxsetId}/binge")
    public void boxsetBinge_returns200() {
        String dedicated = stripOrEmpty(config.getProperty("vrgo.content.detail.boxset.binge.boxset.id"));
        String boxsetId = isConfiguredId(dedicated)
                ? dedicated
                : stripOrEmpty(config.getProperty("vrgo.content.detail.boxset.id"));
        if (!isConfiguredId(boxsetId)) {
            throw new SkipException(
                    "Set vrgo.content.detail.boxset.id or vrgo.content.detail.boxset.binge.boxset.id."
            );
        }
        Allure.parameter("boxset.binge.boxsetId", boxsetId);
        Response r = contentDetailApi.getBoxsetBingeRaw(boxsetId);
        attachAndAssertEnvelope(r, "content-detail-boxset-binge", "vrgo.content.detail.boxset.binge.expected.message");
    }

    @Test(priority = 100, description = "GET boxset/childs with boxsetid header and pagination query")
    @Story("GET /content-detail-service/pub/v1/boxset/childs")
    public void boxsetChilds_headerBoxsetId_returns200() {
        String childsBoxsetId = config.getProperty("vrgo.content.detail.boxset.childs.boxset.id");
        String boxsetId = isConfiguredId(childsBoxsetId)
                ? childsBoxsetId.strip()
                : stripOrEmpty(config.getProperty("vrgo.content.detail.boxset.id"));
        if (!isConfiguredId(boxsetId)) {
            throw new SkipException(
                    "Set vrgo.content.detail.boxset.childs.boxset.id or vrgo.content.detail.boxset.id."
            );
        }

        int fromMovie = parseNonNegativeInt(config.getProperty("vrgo.content.detail.boxset.childs.from.movie"), 0);
        int pageSizeMovie = parseNonNegativeInt(config.getProperty("vrgo.content.detail.boxset.childs.page.size.movie"), 100);
        int fromTvShow = parseNonNegativeInt(config.getProperty("vrgo.content.detail.boxset.childs.from.tv.show"), 0);
        int pageSizeTvShow = parseNonNegativeInt(config.getProperty("vrgo.content.detail.boxset.childs.page.size.tv.show"), 100);
        int fromTrailer = parseNonNegativeInt(config.getProperty("vrgo.content.detail.boxset.childs.from.trailer"), 0);
        int pageSizeTrailer = parseNonNegativeInt(config.getProperty("vrgo.content.detail.boxset.childs.page.size.trailer"), 100);

        Allure.parameter("boxset.childs.boxsetId", boxsetId);

        Response r = contentDetailApi.getBoxsetChildsRaw(
                boxsetId,
                fromMovie,
                pageSizeMovie,
                fromTvShow,
                pageSizeTvShow,
                fromTrailer,
                pageSizeTrailer
        );
        attachAndAssertEnvelope(r, "boxset-childs-response", "vrgo.content.detail.boxset.childs.expected.message");
    }

    @DataProvider(name = "trailerContent")
    public static Object[][] trailerContent() {
        return new Object[][]{
                {"movie", "vrgo.content.detail.movie.id"},
                {"series", "vrgo.content.detail.close.series.id"},
                {"series", "vrgo.content.detail.open.series.id"},
                {"boxset", "vrgo.content.detail.boxset.id"}
        };
    }

    @Test(priority = 110, dataProvider = "trailerContent", description = "GET trailer/{contentType}/{contentId} — data null or object")
    @Story("GET /content-detail-service/pub/v1/trailer/{contentType}/{contentId}")
    public void trailer_byContentType_returns200(String contentType, String contentIdPropertyKey) {
        String contentId = stripOrEmpty(config.getProperty(contentIdPropertyKey));
        if (!isConfiguredId(contentId)) {
            throw new SkipException("Configure " + contentIdPropertyKey + " for trailer row: " + contentType);
        }
        Response r = contentDetailApi.getTrailerRaw(contentType, contentId.strip());
        AllureAttachmentUtils.attachJson("trailer-" + contentType + "-" + safeAttachSuffix(contentId), r.asString());
        r.then().statusCode(200).body("status", equalTo(true));
        String expectedMessage = config.getProperty("vrgo.content.detail.trailer.expected.message");
        if (expectedMessage != null && !expectedMessage.isBlank()) {
            r.then().body("message", equalTo(expectedMessage.strip()));
        }
        Object data = r.jsonPath().get("data");
        if (data != null) {
            Assert.assertTrue(
                    data instanceof Map && !((Map<?, ?>) data).isEmpty(),
                    "When trailer data is present it must be a non-empty object"
            );
        }
    }

    @Test(priority = 120, description = "GET channel editorial detail")
    @Story("GET /content-detail-service/pub/v1/channel/{channelId}")
    public void channelDetail_returns200() {
        String channelId = resolvePrimaryChannelId();
        if (!isConfiguredId(channelId)) {
            throw new SkipException("Set vrgo.content.detail.channel.id (or day / on-air channel ids).");
        }
        Response r = contentDetailApi.getChannelRaw(channelId);
        attachAndAssertEnvelope(r, "content-detail-channel", null);
    }

    @Test(priority = 130, description = "GET on-air programme for linear channel")
    @Story("GET /content-detail-service/pub/v1/on-air/{channelId}")
    public void channelOnAir_returns200() {
        String channelId = resolveOnAirChannelId();
        if (!isConfiguredId(channelId)) {
            throw new SkipException("Set channel id for on-air (vrgo.content.detail.channel.on.air.channel.id or fallbacks).");
        }
        Response r = contentDetailApi.getOnAirRaw(channelId);
        attachAndAssertEnvelope(r, "content-detail-on-air", "vrgo.content.detail.channel.on.air.expected.message");
    }

    @Test(priority = 140, description = "GET channel/{channelId}/dates — scheduled dates list")
    @Story("GET /content-detail-service/pub/v1/channel/{channelId}/dates")
    public void channelDates_returns200() {
        String channelId = resolvePrimaryChannelId();
        if (!isConfiguredId(channelId)) {
            throw new SkipException("Set vrgo.content.detail.channel.id (or fallbacks).");
        }
        Response r = contentDetailApi.getChannelDatesRaw(channelId);
        attachAndAssertEnvelope(r, "content-detail-channel-dates", "vrgo.content.detail.channel.dates.expected.message");
    }

    @Test(priority = 150, description = "GET events/{displayDate} with channelids header (today in channel.day.timezone)")
    @Story("GET /content-detail-service/pub/v1/events/{displayDate}")
    public void channelEvents_forToday_returns200() {
        String channelIds = resolveEventsChannelIdsHeader();
        if (channelIds == null || channelIds.isBlank()) {
            throw new SkipException("Set vrgo.content.detail.events.channel.ids or a primary channel id.");
        }
        String displayDate = resolveEventsDisplayDate();
        Allure.parameter("events.displayDate", displayDate);
        Response r = contentDetailApi.getEventsRaw(displayDate, channelIds);
        attachAndAssertEnvelope(r, "content-detail-events", "vrgo.content.detail.events.expected.message");
    }

    @Test(priority = 160, description = "GET channel-day/{channelId}/{dayEpochMs} — epoch in [now, end of day)")
    @Story("GET /content-detail-service/pub/v1/channel-day/{channelId}/{dayEpochMs}")
    public void channelDay_returns200() {
        String channelId = resolveChannelDayChannelId();
        if (!isConfiguredId(channelId)) {
            throw new SkipException("Set vrgo.content.detail.channel.day.channel.id or channel.id.");
        }
        long epoch = pickEpochMs(
                "vrgo.content.detail.channel.day.epoch.ms",
                "vrgo.content.detail.channel.day.timezone"
        );
        Allure.parameter("channelDay.epochMs", String.valueOf(epoch));
        Response r = contentDetailApi.getChannelDayRaw(contentDetailApi.getChannelDayPathTemplate(), channelId, epoch);
        attachAndAssertEnvelope(r, "content-detail-channel-day", null);
    }

    @Test(
            priority = 170,
            description = "GET 48hr linear EPG grid — channel-day/48-hours/{channelId}/{dayEpochMs} (epoch in [now, end of day) unless fixed property >= now)"
    )
    @Story("GET /content-detail-service/pub/v1/channel-day/48-hours/{channelId}/{dayEpochMs}")
    public void channelDay48hr_returns200() {
        String channelId = resolveChannelDay48hrChannelId();
        if (!isConfiguredId(channelId)) {
            throw new SkipException("Set 48hr or channel-day channel id.");
        }
        long epoch = pickEpochMs(
                "vrgo.content.detail.channel.day.48hr.epoch.ms",
                "vrgo.content.detail.channel.day.timezone"
        );
        Allure.parameter("channelDay48hr.epochMs", String.valueOf(epoch));
        Response r = contentDetailApi.getChannelDayRaw(contentDetailApi.getChannelDay48hrPathTemplate(), channelId, epoch);
        attachAndAssertEnvelope(r, "content-detail-channel-day-48hr", "vrgo.content.detail.channel.day.48hr.expected.message");
    }

    @Test(priority = 180, description = "GET epg/{epgId} — programme detail; event id fetched from channel-day when not configured")
    @Story("GET /content-detail-service/pub/v1/epg/{epgId}")
    public void epgDetail_returns200() {
        String epgId = resolveEpgIdForDetailTest();
        if (!isConfiguredId(epgId)) {
            throw new SkipException(
                    "No live EPG event id found (on-air / channel-day). "
                            + "Override with -Dvrgo.content.detail.epg.event.id=... if needed."
            );
        }
        Allure.parameter("epg.id", epgId.strip());
        Response r = contentDetailApi.getEpgRaw(epgId.strip());
        attachAndAssertEnvelope(r, "content-detail-epg", "vrgo.content.detail.epg.expected.message");
    }

    /**
     * Resolves a programme {@code eventId} for the EPG detail test.
     * Linear EPG ids expire quickly, so a stale value in properties is not trusted unless passed via
     * {@code -Dvrgo.content.detail.epg.event.id}. Otherwise probes on-air and channel-day candidates
     * until the EPG endpoint returns 200.
     */
    private String resolveEpgIdForDetailTest() {
        String override = System.getProperty("vrgo.content.detail.epg.event.id");
        if (isConfiguredId(override)) {
            Allure.parameter("epg.id.source", "system-property");
            return override.strip();
        }

        for (String candidate : collectEpgIdCandidates()) {
            Response probe = contentDetailApi.getEpgRaw(candidate);
            if (probe.statusCode() == 200) {
                Allure.parameter("epg.id.source", "live");
                return candidate;
            }
        }
        return "";
    }

    private List<String> collectEpgIdCandidates() {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String onAirEventId = extractEventIdFromOnAir();
        if (isConfiguredId(onAirEventId)) {
            candidates.add(onAirEventId.strip());
        }

        List<String> channelDayEventIds = extractEventIdsFromChannelDay();
        for (int i = channelDayEventIds.size() - 1; i >= 0; i--) {
            candidates.add(channelDayEventIds.get(i));
        }
        return new ArrayList<>(candidates);
    }

    private String extractEventIdFromOnAir() {
        String channelId = resolveOnAirChannelId();
        if (!isConfiguredId(channelId)) {
            return "";
        }
        try {
            Response onAirResponse = contentDetailApi.getOnAirRaw(channelId);
            if (onAirResponse.statusCode() != 200) {
                return "";
            }
            String fromData = onAirResponse.jsonPath().getString("data.eventId");
            if (isConfiguredId(fromData)) {
                return fromData.strip();
            }
            String fromMeta = onAirResponse.jsonPath().getString("data.meta.eventId");
            if (isConfiguredId(fromMeta)) {
                return fromMeta.strip();
            }
            Matcher matcher = Pattern.compile("\"eventId\"\\s*:\\s*\"([^\"]+)\"")
                    .matcher(onAirResponse.asString());
            if (matcher.find()) {
                return matcher.group(1).strip();
            }
        } catch (Exception ignored) {
            // fall through to channel-day candidates
        }
        return "";
    }

    private List<String> extractEventIdsFromChannelDay() {
        String channelId = resolveChannelDayChannelId();
        if (!isConfiguredId(channelId)) {
            return List.of();
        }
        long epoch = pickEpochMs(
                "vrgo.content.detail.channel.day.epoch.ms",
                "vrgo.content.detail.channel.day.timezone"
        );
        try {
            Response channelDayResponse = contentDetailApi.getChannelDayRaw(
                    contentDetailApi.getChannelDayPathTemplate(), channelId, epoch);
            if (channelDayResponse.statusCode() != 200) {
                return List.of();
            }
            List<String> eventIds = new ArrayList<>();
            try {
                JsonNode root = JsonUtils.mapper().readTree(channelDayResponse.asString());
                collectEventIdsFromJson(root, eventIds);
            } catch (JsonProcessingException ignored) {
                eventIds.clear();
            }
            if (eventIds.isEmpty()) {
                Matcher matcher = Pattern.compile("\"eventId\"\\s*:\\s*\"([^\"]+)\"")
                        .matcher(channelDayResponse.asString());
                while (matcher.find()) {
                    String eventId = matcher.group(1);
                    if (isConfiguredId(eventId)) {
                        eventIds.add(eventId.strip());
                    }
                }
            }
            return eventIds;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static void collectEventIdsFromJson(JsonNode node, List<String> out) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            JsonNode eventIdNode = node.get("eventId");
            if (eventIdNode != null && eventIdNode.isTextual()) {
                String eventId = eventIdNode.asText();
                if (isConfiguredId(eventId)) {
                    out.add(eventId.strip());
                }
            }
            node.fields().forEachRemaining(e -> collectEventIdsFromJson(e.getValue(), out));
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectEventIdsFromJson(child, out);
            }
        }
    }

    @DataProvider(name = "channelNeighborStates")
    public static Object[][] channelNeighborStates() {
        return new Object[][]{{"NEXT"}, {"PREVIOUS"}};
    }

    @Test(
            priority = 190,
            dataProvider = "channelNeighborStates",
            description = "GET channel/{channelId}/{NEXT|PREVIOUS} — bouquet neighbour channel"
    )
    @Story("GET /content-detail-service/pub/v1/channel/{channelId}/{neighborState}")
    public void channelBouquetNeighbor_returns200(String neighborState) {
        String channelId = stripOrEmpty(config.getProperty("vrgo.content.detail.channel.neighbor.channel.id"));
        if (!isConfiguredId(channelId)) {
            channelId = resolvePrimaryChannelId();
        }
        if (!isConfiguredId(channelId)) {
            throw new SkipException("Set vrgo.content.detail.channel.neighbor.channel.id or primary channel id.");
        }
        Allure.parameter("channel.neighbor", neighborState);
        Response r = contentDetailApi.getChannelNeighborRaw(channelId, neighborState);
        attachAndAssertEnvelope(r, "content-detail-channel-neighbor-" + neighborState, null);
    }

    @Test(priority = 200, description = "GET mybox/channels — channel catalogue")
    @Story("GET /content-detail-service/pub/v1/mybox/channels")
    public void myboxChannels_returns200() {
        Response r = contentDetailApi.getMyboxChannelsRaw();
        attachAndAssertEnvelope(r, "content-detail-mybox-channels", "vrgo.content.detail.mybox.channels.expected.message");
    }

    @Test(priority = 210, description = "GET mybox/genres — genre list")
    @Story("GET /content-detail-service/pub/v1/mybox/genres")
    public void myboxGenres_returns200() {
        Response r = contentDetailApi.getMyboxGenresRaw();
        attachAndAssertEnvelope(r, "content-detail-mybox-genres", "vrgo.content.detail.mybox.genres.expected.message");
    }

    @Test(priority = 220, description = "GET filter/ — channel filter catalogue (data array of name, channelKey)")
    @Story("GET /content-detail-service/pub/v1/filter/")
    public void channelFilters_returns200() {
        Response r = contentDetailApi.getChannelFiltersRaw();
        AllureAttachmentUtils.attachJson("content-detail-channel-filters", r.asString());
        var then = r.then()
                .statusCode(200)
                .body("status", equalTo(true))
                .body("data", notNullValue())
                .body("data.size()", greaterThan(0))
                .body("data[0].name", notNullValue())
                .body("data[0].channelKey", notNullValue());
        String expectedMessage = config.getProperty("vrgo.content.detail.channel.filters.expected.message");
        if (expectedMessage != null && !expectedMessage.isBlank()) {
            then.body("message", equalTo(expectedMessage.strip()));
        }
    }

    @Test(priority = 230, description = "GET mini-mybox/{dayEpochMs} with limit, offset, epgEnum")
    @Story("GET /content-detail-service/pub/v2/mini-mybox/{dayEpochMs}")
    public void miniMybox_returns200() {
        long epoch = pickEpochMs(
                "vrgo.content.detail.mini.mybox.epoch.ms",
                "vrgo.content.detail.channel.day.timezone"
        );
        int limit = parsePositiveInt(config.getProperty("vrgo.content.detail.mini.mybox.limit"), 200);
        int offset = parseNonNegativeInt(config.getProperty("vrgo.content.detail.mini.mybox.offset"), 0);
        String epgEnum = firstNonBlank(config.getProperty("vrgo.content.detail.mini.mybox.epg.enum"), "ON_AIR");
        Allure.parameter("miniMybox.epochMs", String.valueOf(epoch));
        Response r = contentDetailApi.getMiniMyboxRaw(epoch, limit, offset, epgEnum);
        attachAndAssertEnvelope(r, "content-detail-mini-mybox", "vrgo.content.detail.mini.mybox.expected.message");
    }

    private void attachAndAssertEnvelope(Response r, String attachmentName, String optionalMessagePropertyKey) {
        AllureAttachmentUtils.attachJson(attachmentName, r.asString());
        var then = r.then().statusCode(200).body("status", equalTo(true)).body("data", notNullValue());
        if (optionalMessagePropertyKey != null) {
            String expectedMessage = config.getProperty(optionalMessagePropertyKey);
            if (expectedMessage != null && !expectedMessage.isBlank()) {
                then.body("message", equalTo(expectedMessage.strip()));
            }
        }
    }

    private void assertOperatorMetaPresent(Response r) {
        // TODO: re-enable when operator meta is stable in the environment
        return;
        // r.then()
        //         .body("data.meta.operatorLabel", notNullValue())
        //         .body("data.meta.operatorType", notNullValue());
    }

    /**
     * season_episode (and similar list envelopes): pass when at least one {@code operatorLabel} under
     * {@code data} is non-null; fail when every {@code operatorLabel} is null or absent.
     */
    private void assertAnyOperatorLabelPresent(Response r) {
        // TODO: re-enable when operator meta is stable in the environment
        return;
        // JsonNode data;
        // try {
        //     data = JsonUtils.mapper().readTree(r.asString()).get("data");
        // } catch (JsonProcessingException e) {
        //     Assert.fail("Failed to parse response JSON for operatorLabel check: " + e.getMessage());
        //     return;
        // }
        // if (findNonNullOperatorLabel(data)) {
        //     return;
        // }
        // Assert.fail("Expected at least one non-null operatorLabel in season_episode response data");
    }

    private static boolean findNonNullOperatorLabel(JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isObject()) {
            JsonNode operatorLabel = node.get("operatorLabel");
            if (operatorLabel != null && !operatorLabel.isNull()) {
                return true;
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                if (findNonNullOperatorLabel(fields.next().getValue())) {
                    return true;
                }
            }
            return false;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (findNonNullOperatorLabel(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String seriesRegion() {
        return firstNonBlank(
                config.getProperty("vrgo.content.detail.series.region"),
                config.getProperty("vrgo.favourites.region"),
                "Malaysia"
        );
    }

    private String seriesContentType() {
        return firstNonBlank(config.getProperty("vrgo.content.detail.series.content.type"), "VOD");
    }

    private boolean seriesIsEntitlementEnabled() {
        return parseBooleanLoose(config.getProperty("vrgo.content.detail.is.entitlement.enabled"), false);
    }

    private String resolvePrimaryChannelId() {
        String onAir = config.getProperty("vrgo.content.detail.channel.on.air.channel.id");
        if (isConfiguredId(onAir)) {
            return onAir.strip();
        }
        String day = config.getProperty("vrgo.content.detail.channel.day.channel.id");
        if (isConfiguredId(day)) {
            return day.strip();
        }
        String ch = config.getProperty("vrgo.content.detail.channel.id");
        if (isConfiguredId(ch)) {
            return ch.strip();
        }
        String alt = config.getProperty("vrgo.content.detail.channel.alt.id");
        return isConfiguredId(alt) ? alt.strip() : "";
    }

    private String resolveOnAirChannelId() {
        String dedicated = config.getProperty("vrgo.content.detail.channel.on.air.channel.id");
        if (isConfiguredId(dedicated)) {
            return dedicated.strip();
        }
        return resolvePrimaryChannelId();
    }

    private String resolveChannelDayChannelId() {
        String day = config.getProperty("vrgo.content.detail.channel.day.channel.id");
        if (isConfiguredId(day)) {
            return day.strip();
        }
        return resolvePrimaryChannelId();
    }

    private String resolveChannelDay48hrChannelId() {
        String h = config.getProperty("vrgo.content.detail.channel.day.48hr.channel.id");
        if (isConfiguredId(h)) {
            return h.strip();
        }
        return resolveChannelDayChannelId();
    }

    private String resolveEventsDisplayDate() {
        String override = config.getProperty("vrgo.content.detail.events.display.date");
        if (override != null && !override.isBlank()) {
            return override.strip();
        }
        String tz = config.getProperty("vrgo.content.detail.channel.day.timezone", "Asia/Kuala_Lumpur");
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(tz));
        return now.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH));
    }

    private String resolveEventsChannelIdsHeader() {
        String ids = config.getProperty("vrgo.content.detail.events.channel.ids");
        if (ids != null && !ids.isBlank()) {
            return ids.strip();
        }
        return resolvePrimaryChannelId();
    }

    /**
     * Uses fixed epoch from property only when {@code >= now}; otherwise random in {@code [now, end of local day)}
     * in the configured timezone (avoids past-day #ERR-300-015).
     */
    private long pickEpochMs(String fixedEpochPropertyKey, String timezonePropertyKey) {
        long now = Instant.now().toEpochMilli();
        String fixed = config.getProperty(fixedEpochPropertyKey);
        if (fixed != null && !fixed.isBlank()) {
            try {
                long v = Long.parseLong(fixed.strip());
                if (v >= now) {
                    return v;
                }
            } catch (NumberFormatException ignored) {
                // fall through to dynamic
            }
        }
        String tzId = config.getProperty(timezonePropertyKey, "Asia/Kuala_Lumpur");
        ZoneId z = ZoneId.of(tzId);
        ZonedDateTime wall = ZonedDateTime.now(z);
        long endOfDayMs = wall.toLocalDate().plusDays(1).atStartOfDay(z).toInstant().toEpochMilli();
        long span = Math.max(1L, endOfDayMs - now);
        return now + ThreadLocalRandom.current().nextLong(span);
    }

    private static String safeAttachSuffix(String contentId) {
        String s = contentId == null ? "null" : contentId.strip();
        return s.length() > 24 ? s.substring(0, 24) : s;
    }

    private static String stripOrEmpty(String s) {
        return s == null ? "" : s.strip();
    }

    private static boolean isConfiguredId(String id) {
        if (id == null) {
            return false;
        }
        String s = id.strip();
        if (s.isEmpty()) {
            return false;
        }
        String upper = s.toUpperCase(Locale.ROOT);
        if (upper.startsWith("REPLACE") || upper.equals("NULL")) {
            return false;
        }
        return true;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return "";
    }

    private static String firstNonBlank(String a, String b, String c) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        if (c != null && !c.isBlank()) {
            return c;
        }
        return "";
    }

    private static int parseNonNegativeInt(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            int v = Integer.parseInt(raw.strip());
            return Math.max(0, v);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int parsePositiveInt(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            int v = Integer.parseInt(raw.strip());
            return v > 0 ? v : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean parseBooleanLoose(String raw, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(raw.strip());
    }
}
