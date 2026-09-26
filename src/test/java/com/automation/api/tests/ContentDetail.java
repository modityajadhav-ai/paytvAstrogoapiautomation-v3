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
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

/**
 * VRGO content-detail-service regression: series (close/open), season/series episodes, view-all, tv_show,
 * episode-hierarchy (NEXT|CURRENT|PREVIOUS) vs BingeWatch episode adjacent (NEXT|PREVIOUS on {@code /episode/...}),
 * movie, boxset (+ binge, childs), trailer, linear (channel, on-air, dates, events, channel-day, 48hr, EPG,
 * bouquet next/prev), MyBox (channels, genres, v1 day grid), channel filters, mini-mybox, 3PP VOD (movie / episode / season).
 */
@Feature("Content detail")
public class ContentDetail extends BaseTest {

    @BeforeClass(alwaysRun = true)
    public void requireContentDetailClient() {
        if (contentDetailApi == null) {
            throw new SkipException("vrgo.base.url is not set; content-detail client was not created.");
        }
    }

    @Test(priority = 10, description = "CD_2.1_v1_closedSeriesDetail_Valid")
    @Story("CD_2.1_v1_closedSeriesDetail_Valid — GET /content-detail-service/pub/v1/series/{seriesId}")
    public void CD_2_1_v1_closedSeriesDetail_Valid() {
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

    @Test(priority = 11, description = "CD_2.1_v1_closedSeriesDetail_seriesIdInvalid_Invalid")
    @Story("CD_2.1_v1_closedSeriesDetail_seriesIdInvalid_Invalid — GET /content-detail-service/pub/v1/series/{seriesId} — invalid seriesId")
    public void CD_2_1_v1_closedSeriesDetail_seriesIdInvalid_Invalid() {
        Allure.parameter("seriesId", "INVALID");
        Response r = contentDetailApi.getSeriesDetailRaw(
                "INVALID",
                seriesRegion(),
                seriesContentType(),
                seriesIsEntitlementEnabled()
        );
        attachAndAssertSeriesDetailBadRequest(r, "content-detail-close-series-series-id-invalid");
    }

    @Test(priority = 12, description = "CD_2.1_v1_closedSeriesDetail_isOpenFalse_Valid")
    @Story("CD_2.1_v1_closedSeriesDetail_isOpenFalse_Valid — GET /content-detail-service/pub/v1/series/{seriesId} — isOpen false")
    public void CD_2_1_v1_closedSeriesDetail_isOpenFalse_Valid() {
        String seriesId = stripOrEmpty(config.getProperty("vrgo.content.detail.close.series.id"));
        if (!isConfiguredId(seriesId)) {
            throw new SkipException("Set vrgo.content.detail.close.series.id.");
        }
        Allure.parameter("seriesId", seriesId);
        Response r = contentDetailApi.getSeriesDetailRaw(
                seriesId,
                seriesRegion(),
                seriesContentType(),
                seriesIsEntitlementEnabled()
        );
        attachAndAssertEnvelope(r, "content-detail-close-series-is-open", null);
        assertJsonBooleanFieldFalseWherePresent(r, "isOpen");
    }

    @Test(priority = 20, description = "CD_2.3_v1_openSeriesDetail_Valid")
    @Story("CD_2.3_v1_openSeriesDetail_Valid — GET /content-detail-service/pub/v1/series/{seriesId}")
    public void CD_2_3_v1_openSeriesDetail_Valid() {
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

    @Test(priority = 30, description = "CD_2.2_v1_seasonEpisodeDetail_Valid")
    @Story("CD_2.2_v1_seasonEpisodeDetail_Valid — GET /content-detail-service/pub/v1/season_episode/{seasonId}")
    public void CD_2_2_v1_seasonEpisodeDetail_Valid() {
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

    @Test(priority = 40, description = "CD_2.4_v1_seriesEpisodeDetail_Valid")
    @Story("CD_2.4_v1_seriesEpisodeDetail_Valid — GET /content-detail-service/pub/v1/series_episode/{seriesId}")
    public void CD_2_4_v1_seriesEpisodeDetail_Valid() {
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

    @Test(priority = 50, description = "CD_2.21_v1_viewAll_Next_Valid")
    @Story("CD_2.21_v1_viewAll_Next_Valid — GET /content-detail-service/pub/v1/series/{seriesId}/episode/{episodeId}")
    public void CD_2_21_v1_viewAll_Next_Valid() {
        assertSeriesEpisodeViewAll("NEXT");
    }

    @Test(priority = 51, description = "CD_2.21_v1_viewAll_Previous_Valid")
    @Story("CD_2.21_v1_viewAll_Previous_Valid — GET /content-detail-service/pub/v1/series/{seriesId}/episode/{episodeId}")
    public void CD_2_21_v1_viewAll_Previous_Valid() {
        assertSeriesEpisodeViewAll("PREVIOUS");
    }

    @Test(priority = 52, description = "CD_2.21_v1_viewAll_PreviousNext_Valid")
    @Story("CD_2.21_v1_viewAll_PreviousNext_Valid — GET /content-detail-service/pub/v1/series/{seriesId}/episode/{episodeId}")
    public void CD_2_21_v1_viewAll_PreviousNext_Valid() {
        assertSeriesEpisodeViewAll("PREVIOUS_NEXT");
    }

    private void assertSeriesEpisodeViewAll(String type) {
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

    @Test(priority = 60, description = "CD_2.5_v1_episodeDetail_Valid")
    @Story("CD_2.5_v1_episodeDetail_Valid — GET /content-detail-service/pub/v1/tv_show/{episodeId}")
    public void CD_2_5_v1_episodeDetail_Valid() {
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

    @Test(priority = 65, description = "CD_2.14_v1_bingeWatch_Next_Valid")
    @Story("CD_2.14_v1_bingeWatch_Next_Valid — GET /content-detail-service/pub/v1/episode/{episodeId}/NEXT")
    public void CD_2_14_v1_bingeWatch_Next_Valid() {
        assertBingeWatchEpisode("NEXT");
    }

    @Test(priority = 66, description = "CD_2.14_v1_bingeWatch_Previous_Valid")
    @Story("CD_2.14_v1_bingeWatch_Previous_Valid — GET /content-detail-service/pub/v1/episode/{episodeId}/PREVIOUS")
    public void CD_2_14_v1_bingeWatch_Previous_Valid() {
        assertBingeWatchEpisode("PREVIOUS");
    }

    private void assertBingeWatchEpisode(String direction) {
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

    @Test(priority = 70, description = "CD_2.20_v1_episodeHierarchy_Next_Valid")
    @Story("CD_2.20_v1_episodeHierarchy_Next_Valid — GET /content-detail-service/pub/v1/episode-hierarchy/{episodeId}/NEXT")
    public void CD_2_20_v1_episodeHierarchy_Next_Valid() {
        assertEpisodeHierarchy("NEXT");
    }

    @Test(priority = 71, description = "CD_2.20_v1_episodeHierarchy_Current_Valid")
    @Story("CD_2.20_v1_episodeHierarchy_Current_Valid — GET /content-detail-service/pub/v1/episode-hierarchy/{episodeId}/CURRENT")
    public void CD_2_20_v1_episodeHierarchy_Current_Valid() {
        assertEpisodeHierarchy("CURRENT");
    }

    @Test(priority = 72, description = "CD_2.20_v1_episodeHierarchy_Previous_Valid")
    @Story("CD_2.20_v1_episodeHierarchy_Previous_Valid — GET /content-detail-service/pub/v1/episode-hierarchy/{episodeId}/PREVIOUS")
    public void CD_2_20_v1_episodeHierarchy_Previous_Valid() {
        assertEpisodeHierarchy("PREVIOUS");
    }

    private void assertEpisodeHierarchy(String direction) {
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

    @Test(priority = 73, description = "CD_2.14_v1_episodeHierarchy_catalogueidsBlank_Invalid")
    @Story("CD_2.14_v1_episodeHierarchy_catalogueidsBlank_Invalid — GET /content-detail-service/pub/v1/episode-hierarchy/{episodeId}/{direction} — blank catalogueids")
    public void CD_2_14_v1_episodeHierarchy_catalogueidsBlank_Invalid() {
        String episodeId = resolveEpisodeHierarchyMiddleEpisodeId();
        Allure.parameter("episodeHierarchy.direction", "NEXT");
        Allure.parameter("episodeHierarchy.episodeId", episodeId);
        Response r = contentDetailApi.getEpisodeHierarchyRaw(
                episodeId,
                "NEXT",
                Map.of("catalogueids", ""),
                null
        );
        attachAndAssertEpisodeHierarchyBadRequest(
                r,
                "episode-hierarchy-catalogueids-blank",
                "#ERR-300-102"
        );
    }

    @Test(priority = 74, description = "CD_2.14_v1_episodeHierarchy_nextEpisodeNotAvailable_Invalid")
    @Story("CD_2.14_v1_episodeHierarchy_nextEpisodeNotAvailable_Invalid — GET /content-detail-service/pub/v1/episode-hierarchy/{episodeId}/NEXT — next unavailable")
    public void CD_2_14_v1_episodeHierarchy_nextEpisodeNotAvailable_Invalid() {
        String episodeId = resolveEpisodeHierarchyNextUnavailableEpisodeId();
        Allure.parameter("episodeHierarchy.direction", "NEXT");
        Allure.parameter("episodeHierarchy.episodeId", episodeId);
        Response r = contentDetailApi.getEpisodeHierarchyRaw(episodeId, "NEXT");
        attachAndAssertEpisodeHierarchyBadRequest(
                r,
                "episode-hierarchy-next-unavailable",
                "#ERR-300-131"
        );
    }

    @Test(priority = 75, description = "CD_2.14_v1_episodeHierarchy_previousEpisodeNotAvailable_Invalid")
    @Story("CD_2.14_v1_episodeHierarchy_previousEpisodeNotAvailable_Invalid — GET /content-detail-service/pub/v1/episode-hierarchy/{episodeId}/PREVIOUS — previous unavailable")
    public void CD_2_14_v1_episodeHierarchy_previousEpisodeNotAvailable_Invalid() {
        String episodeId = resolveEpisodeHierarchyPreviousUnavailableEpisodeId();
        Allure.parameter("episodeHierarchy.direction", "PREVIOUS");
        Allure.parameter("episodeHierarchy.episodeId", episodeId);
        Response r = contentDetailApi.getEpisodeHierarchyRaw(episodeId, "PREVIOUS");
        attachAndAssertEpisodeHierarchyBadRequest(
                r,
                "episode-hierarchy-previous-unavailable",
                "#ERR-300-131"
        );
    }

    @Test(priority = 76, description = "CD_2.14_v1_episodeHierarchy_episodeIdInvalid_Invalid")
    @Story("CD_2.14_v1_episodeHierarchy_episodeIdInvalid_Invalid — GET /content-detail-service/pub/v1/episode-hierarchy/{episodeId}/NEXT — invalid episodeId")
    public void CD_2_14_v1_episodeHierarchy_episodeIdInvalid_Invalid() {
        String episodeId = resolveEpisodeHierarchyInvalidEpisodeId();
        Allure.parameter("episodeHierarchy.direction", "NEXT");
        Allure.parameter("episodeHierarchy.episodeId", episodeId);
        Response r = contentDetailApi.getEpisodeHierarchyRaw(episodeId, "NEXT");
        attachAndAssertEpisodeHierarchyBadRequest(
                r,
                "episode-hierarchy-episode-id-invalid",
                "#ERR-300-131"
        );
    }

    @Test(priority = 77, description = "CD_2.14_v1_episodeHierarchy_episodeIdNull_Invalid")
    @Story("CD_2.14_v1_episodeHierarchy_episodeIdNull_Invalid — GET /content-detail-service/pub/v1/episode-hierarchy/{episodeId}/NEXT — null episodeId path")
    public void CD_2_14_v1_episodeHierarchy_episodeIdNull_Invalid() {
        Allure.parameter("episodeHierarchy.direction", "NEXT");
        Allure.parameter("episodeHierarchy.episodeId", "null");
        Response r = contentDetailApi.getEpisodeHierarchyRaw("null", "NEXT");
        attachAndAssertEpisodeHierarchyBadRequest(
                r,
                "episode-hierarchy-episode-id-null",
                "#ERR-300-131"
        );
    }

    @Test(priority = 78, description = "CD_2.23_v1_3ppMovieDetail_Valid")
    @Story("CD_2.23_v1_3ppMovieDetail_Valid — GET /content-detail-service/pub/v1/3PPVODMovie/{contentId}")
    public void CD_2_23_v1_3ppMovieDetail_Valid() {
        String id = stripOrEmpty(config.getProperty("vrgo.content.detail.3ppvod.movie.id"));
        if (!isConfiguredId(id)) {
            throw new SkipException("Set vrgo.content.detail.3ppvod.movie.id.");
        }
        Allure.parameter("3ppvod.contentType", "3ppvodmovie");
        Allure.parameter("3ppvod.contentId", id);
        Response r = contentDetailApi.getThreePpVodMovieRaw(id);
        attachAndAssertEnvelope(r, "content-detail-3ppvod-movie", "vrgo.content.detail.3ppvod.movie.expected.message");
    }

    @Test(priority = 79, description = "CD_2.23_v1_3ppEpisodeDetail_Valid")
    @Story("CD_2.23_v1_3ppEpisodeDetail_Valid — GET /content-detail-service/pub/v1/3PPVODEpisode/{contentId}")
    public void CD_2_23_v1_3ppEpisodeDetail_Valid() {
        String id = stripOrEmpty(config.getProperty("vrgo.content.detail.3ppvod.episode.id"));
        if (!isConfiguredId(id)) {
            throw new SkipException("Set vrgo.content.detail.3ppvod.episode.id.");
        }
        Allure.parameter("3ppvod.contentType", "3ppvodepisode");
        Allure.parameter("3ppvod.contentId", id);
        Response r = contentDetailApi.getThreePpVodEpisodeRaw(id);
        attachAndAssertEnvelope(r, "content-detail-3ppvod-episode", "vrgo.content.detail.3ppvod.episode.expected.message");
    }

    @Test(priority = 80, description = "CD_2.23_v1_3ppSeasonDetail_Valid")
    @Story("CD_2.23_v1_3ppSeasonDetail_Valid — GET /content-detail-service/pub/v1/3PPVODSeason/{contentId}")
    public void CD_2_23_v1_3ppSeasonDetail_Valid() {
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

    private String resolveEpisodeHierarchyMiddleEpisodeId() {
        String episodeId = stripOrEmpty(config.getProperty("vrgo.content.detail.episode.hierarchy.chain.middle.id"));
        if (!isConfiguredId(episodeId)) {
            throw new SkipException("Set vrgo.content.detail.episode.hierarchy.chain.middle.id.");
        }
        return episodeId;
    }

    private String resolveEpisodeHierarchyNextUnavailableEpisodeId() {
        String dedicated = config.getProperty("vrgo.content.detail.episode.hierarchy.next.unavailable.episode.id");
        if (isConfiguredId(dedicated)) {
            return dedicated.strip();
        }
        String chainNext = config.getProperty("vrgo.content.detail.episode.hierarchy.chain.next.id");
        if (isConfiguredId(chainNext)) {
            return chainNext.strip();
        }
        throw new SkipException(
                "Set vrgo.content.detail.episode.hierarchy.next.unavailable.episode.id "
                        + "or vrgo.content.detail.episode.hierarchy.chain.next.id."
        );
    }

    private String resolveEpisodeHierarchyPreviousUnavailableEpisodeId() {
        String dedicated = config.getProperty("vrgo.content.detail.episode.hierarchy.previous.unavailable.episode.id");
        if (isConfiguredId(dedicated)) {
            return dedicated.strip();
        }
        String first = config.getProperty("vrgo.content.detail.episode.hierarchy.first.episode.id");
        if (isConfiguredId(first)) {
            return first.strip();
        }
        throw new SkipException(
                "Set vrgo.content.detail.episode.hierarchy.previous.unavailable.episode.id "
                        + "or vrgo.content.detail.episode.hierarchy.first.episode.id."
        );
    }

    private String resolveEpisodeHierarchyInvalidEpisodeId() {
        String dedicated = config.getProperty("vrgo.content.detail.episode.hierarchy.invalid.episode.id");
        if (isConfiguredId(dedicated)) {
            return dedicated.strip();
        }
        return "INVALID";
    }

    @Test(priority = 81, description = "CD_2.6_v1_moviesDetail_Valid")
    @Story("CD_2.6_v1_moviesDetail_Valid — GET /content-detail-service/pub/v1/movie/{movieId}")
    public void CD_2_6_v1_moviesDetail_Valid() {
        String movieId = stripOrEmpty(config.getProperty("vrgo.content.detail.movie.id"));
        if (!isConfiguredId(movieId)) {
            throw new SkipException("Set vrgo.content.detail.movie.id.");
        }
        Response r = contentDetailApi.getMovieRaw(movieId);
        attachAndAssertEnvelope(r, "content-detail-movie", null);
    }

    // @Test(priority = 85, description = "CD_2.6_v1_moviesDetail_Valid — alt id")
    // @Story("CD_2.6_v1_moviesDetail_Valid — GET /content-detail-service/pub/v1/movie/{movieId} — alt id")
    // public void CD_2_6_v1_moviesDetail_altId_Valid() {
    //     String primary = stripOrEmpty(config.getProperty("vrgo.content.detail.movie.id"));
    //     String alt = stripOrEmpty(config.getProperty("vrgo.content.detail.movie.alt.id"));
    //     if (!isConfiguredId(alt) || alt.equalsIgnoreCase(primary)) {
    //         throw new SkipException("Set vrgo.content.detail.movie.alt.id distinct from vrgo.content.detail.movie.id.");
    //     }
    //     Response r = contentDetailApi.getMovieRaw(alt);
    //     attachAndAssertEnvelope(r, "content-detail-movie-alt", null);
    // }

    @Test(priority = 90, description = "CD_2.15_v1_boxsetDetail_Valid")
    @Story("CD_2.15_v1_boxsetDetail_Valid — GET /content-detail-service/pub/v1/boxset/{boxsetId}")
    public void CD_2_15_v1_boxsetDetail_Valid() {
        String boxsetId = stripOrEmpty(config.getProperty("vrgo.content.detail.boxset.id"));
        if (!isConfiguredId(boxsetId)) {
            throw new SkipException("Set vrgo.content.detail.boxset.id.");
        }
        Response r = contentDetailApi.getBoxsetRaw(boxsetId);
        attachAndAssertEnvelope(r, "content-detail-boxset", "vrgo.content.detail.boxset.expected.message");
    }

    @Test(priority = 95, description = "CD_2.24_v1_boxsetBingeWatch_Valid")
    @Story("CD_2.24_v1_boxsetBingeWatch_Valid — GET /content-detail-service/pub/v1/boxset/{boxsetId}/binge")
    public void CD_2_24_v1_boxsetBingeWatch_Valid() {
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

    @Test(priority = 100, description = "CD_2.22_v1_boxsetChild_Valid")
    @Story("CD_2.22_v1_boxsetChild_Valid — GET /content-detail-service/pub/v1/boxset/childs")
    public void CD_2_22_v1_boxsetChild_Valid() {
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

    @Test(priority = 110, description = "CD_2.18_v1_trailer_Movie_Valid")
    @Story("CD_2.18_v1_trailer_Movie_Valid — GET /content-detail-service/pub/v1/trailer/movie/{contentId}")
    public void CD_2_18_v1_trailer_Movie_Valid() {
        assertTrailer("movie", "vrgo.content.detail.movie.id");
    }

    @Test(priority = 111, description = "CD_2.18_v1_trailer_CloseSeries_Valid")
    @Story("CD_2.18_v1_trailer_CloseSeries_Valid — GET /content-detail-service/pub/v1/trailer/series/{contentId}")
    public void CD_2_18_v1_trailer_CloseSeries_Valid() {
        assertTrailer("series", "vrgo.content.detail.close.series.id");
    }

    @Test(priority = 112, description = "CD_2.18_v1_trailer_OpenSeries_Valid")
    @Story("CD_2.18_v1_trailer_OpenSeries_Valid — GET /content-detail-service/pub/v1/trailer/series/{contentId}")
    public void CD_2_18_v1_trailer_OpenSeries_Valid() {
        assertTrailer("series", "vrgo.content.detail.open.series.id");
    }

    @Test(priority = 113, description = "CD_2.18_v1_trailer_Boxset_Valid")
    @Story("CD_2.18_v1_trailer_Boxset_Valid — GET /content-detail-service/pub/v1/trailer/boxset/{contentId}")
    public void CD_2_18_v1_trailer_Boxset_Valid() {
        assertTrailer("boxset", "vrgo.content.detail.boxset.id");
    }

    private void assertTrailer(String contentType, String contentIdPropertyKey) {
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

    @Test(priority = 120, description = "CD_2.7_v1_channelDetail_Valid")
    @Story("CD_2.7_v1_channelDetail_Valid — GET /content-detail-service/pub/v1/channel/{channelId}")
    public void CD_2_7_v1_channelDetail_Valid() {
        String channelId = resolvePrimaryChannelId();
        if (!isConfiguredId(channelId)) {
            throw new SkipException("Set vrgo.content.detail.channel.id (or day / on-air channel ids).");
        }
        Response r = contentDetailApi.getChannelRaw(channelId);
        attachAndAssertEnvelope(r, "content-detail-channel", null);
    }

    @Test(priority = 130, description = "CD_2.19_v1_onAirEpg_Valid")
    @Story("CD_2.19_v1_onAirEpg_Valid — GET /content-detail-service/pub/v1/on-air/{channelId}")
    public void CD_2_19_v1_onAirEpg_Valid() {
        String channelId = resolveOnAirChannelId();
        if (!isConfiguredId(channelId)) {
            throw new SkipException("Set channel id for on-air (vrgo.content.detail.channel.on.air.channel.id or fallbacks).");
        }
        Response r = contentDetailApi.getOnAirRaw(channelId);
        attachAndAssertEnvelope(r, "content-detail-on-air", "vrgo.content.detail.channel.on.air.expected.message");
    }

    @Test(priority = 140, description = "CD_2.16_v1_channelEpgDates_Valid")
    @Story("CD_2.16_v1_channelEpgDates_Valid — GET /content-detail-service/pub/v1/channel/{channelId}/dates")
    public void CD_2_16_v1_channelEpgDates_Valid() {
        String channelId = resolvePrimaryChannelId();
        if (!isConfiguredId(channelId)) {
            throw new SkipException("Set vrgo.content.detail.channel.id (or fallbacks).");
        }
        Response r = contentDetailApi.getChannelDatesRaw(channelId);
        attachAndAssertEnvelope(r, "content-detail-channel-dates", "vrgo.content.detail.channel.dates.expected.message");
    }

    @Test(priority = 150, description = "CD_2.26_v1_events_Valid")
    @Story("CD_2.26_v1_events_Valid — GET /content-detail-service/pub/v1/events/{displayDate}")
    public void CD_2_26_v1_events_Valid() {
        String channelIds = resolveEventsChannelIdsHeader();
        if (channelIds == null || channelIds.isBlank()) {
            throw new SkipException("Set vrgo.content.detail.events.channel.ids or a primary channel id.");
        }
        String displayDate = resolveEventsDisplayDate();
        Allure.parameter("events.displayDate", displayDate);
        Response r = contentDetailApi.getEventsRaw(displayDate, channelIds);
        attachAndAssertEnvelope(r, "content-detail-events", "vrgo.content.detail.events.expected.message");
    }

    @Test(priority = 151, description = "CD_2.26_v1_events_metaChannelDaysSevenDays_Valid")
    @Story("CD_2.26_v1_events_metaChannelDaysSevenDays_Valid — GET /content-detail-service/pub/v1/events/{displayDate} — meta.channelDays")
    public void CD_2_26_v1_events_metaChannelDaysSevenDays_Valid() {
        String channelIds = resolveEventsChannelIdsHeader();
        if (channelIds == null || channelIds.isBlank()) {
            throw new SkipException("Set vrgo.content.detail.events.channel.ids or a primary channel id.");
        }
        String displayDate = resolveEventsDisplayDate();
        Allure.parameter("events.displayDate", displayDate);
        Allure.parameter("events.channelids", channelIds);
        Response r = contentDetailApi.getEventsRaw(displayDate, channelIds);
        AllureAttachmentUtils.attachJson("content-detail-events-meta-channel-days", r.asString());
        r.then()
                .statusCode(200)
                .body("status", equalTo(true))
                .body("data.meta.channelDays", notNullValue())
                .body("data.meta.channelDays.size()", equalTo(7));
    }

    @Test(priority = 152, description = "CD_2.26_v1_events_channelidsBlank_Invalid")
    @Story("CD_2.26_v1_events_channelidsBlank_Invalid — GET /content-detail-service/pub/v1/events/{displayDate} — blank channelids")
    public void CD_2_26_v1_events_channelidsBlank_Invalid() {
        String displayDate = resolveEventsDisplayDate();
        Allure.parameter("events.displayDate", displayDate);
        Allure.parameter("events.channelids", "");
        Response r = contentDetailApi.getEventsRaw(displayDate, "");
        attachAndAssertEventsBadRequest(r, "content-detail-events-channelids-blank", "#ERR-300-014");
    }

    @Test(priority = 153, description = "CD_2.26_v1_events_displayDateInvalid_Invalid")
    @Story("CD_2.26_v1_events_displayDateInvalid_Invalid — GET /content-detail-service/pub/v1/events/{displayDate} — invalid date format")
    public void CD_2_26_v1_events_displayDateInvalid_Invalid() {
        String channelIds = resolveEventsChannelIdsHeader();
        if (channelIds == null || channelIds.isBlank()) {
            throw new SkipException("Set vrgo.content.detail.events.channel.ids or a primary channel id.");
        }
        String invalidDisplayDate = "2026-09-24";
        Allure.parameter("events.displayDate", invalidDisplayDate);
        Allure.parameter("events.channelids", channelIds);
        Response r = contentDetailApi.getEventsRaw(invalidDisplayDate, channelIds);
        attachAndAssertEventsBadRequest(r, "content-detail-events-display-date-invalid", "#ERR-300-011");
    }

    @Test(priority = 160, description = "CD_2.8_v1_channelDay_Valid")
    @Story("CD_2.8_v1_channelDay_Valid — GET /content-detail-service/pub/v1/channel-day/{channelId}/{dayEpochMs}")
    public void CD_2_8_v1_channelDay_Valid() {
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
            description = "CD_2.13_v1_48hrsChannelDay_Valid"
    )
    @Story("CD_2.13_v1_48hrsChannelDay_Valid — GET /content-detail-service/pub/v1/channel-day/48-hours/{channelId}/{dayEpochMs}")
    public void CD_2_13_v1_48hrsChannelDay_Valid() {
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

    @Test(priority = 180, description = "CD_2.12_v1_epgDetail_Valid")
    @Story("CD_2.12_v1_epgDetail_Valid — GET /content-detail-service/pub/v1/epg/{epgId}")
    public void CD_2_12_v1_epgDetail_Valid() {
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

    @Test(priority = 190, description = "CD_2.9_v1_nextPreviousChannel_Next_Valid")
    @Story("CD_2.9_v1_nextPreviousChannel_Next_Valid — GET /content-detail-service/pub/v1/channel/{channelId}/NEXT")
    public void CD_2_9_v1_nextPreviousChannel_Next_Valid() {
        assertChannelNeighbor("NEXT");
    }

    @Test(priority = 191, description = "CD_2.9_v1_nextPreviousChannel_Previous_Valid")
    @Story("CD_2.9_v1_nextPreviousChannel_Previous_Valid — GET /content-detail-service/pub/v1/channel/{channelId}/PREVIOUS")
    public void CD_2_9_v1_nextPreviousChannel_Previous_Valid() {
        assertChannelNeighbor("PREVIOUS");
    }

    private void assertChannelNeighbor(String neighborState) {
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

    @Test(priority = 200, description = "CD_2.25_v1_myBoxChannels_Valid")
    @Story("CD_2.25_v1_myBoxChannels_Valid — GET /content-detail-service/pub/v1/mybox/channels")
    public void CD_2_25_v1_myBoxChannels_Valid() {
        Response r = contentDetailApi.getMyboxChannelsRaw();
        attachAndAssertEnvelope(r, "content-detail-mybox-channels", "vrgo.content.detail.mybox.channels.expected.message");
    }

    @Test(priority = 210, description = "CD_2.16_v1_myBoxGenres_Valid")
    @Story("CD_2.16_v1_myBoxGenres_Valid — GET /content-detail-service/pub/v1/mybox/genres")
    public void CD_2_16_v1_myBoxGenres_Valid() {
        Response r = contentDetailApi.getMyboxGenresRaw();
        attachAndAssertEnvelope(r, "content-detail-mybox-genres", "vrgo.content.detail.mybox.genres.expected.message");
    }

    @Test(priority = 215, description = "CD_2.10_v1_myBox_Valid")
    @Story("CD_2.10_v1_myBox_Valid — GET /content-detail-service/pub/v1/mybox/{dayEpochMs}")
    public void CD_2_10_v1_myBox_Valid() {
        long epoch = pickEpochMs(
                "vrgo.content.detail.mybox.epoch.ms",
                "vrgo.content.detail.channel.day.timezone"
        );
        int limit = parsePositiveInt(config.getProperty("vrgo.content.detail.mybox.limit"), 1000);
        int offset = parseNonNegativeInt(config.getProperty("vrgo.content.detail.mybox.offset"), 0);
        Allure.parameter("mybox.epochMs", String.valueOf(epoch));
        Response r = contentDetailApi.getMyboxRaw(epoch, limit, offset);
        attachAndAssertEnvelope(r, "content-detail-mybox", "vrgo.content.detail.mybox.expected.message");
    }

    @Test(priority = 216, description = "CD_2.10_v1_myBox_ottbouquetidAbsent_Invalid")
    @Story("CD_2.10_v1_myBox_ottbouquetidAbsent_Invalid — GET /content-detail-service/pub/v1/mybox/{dayEpochMs} — ottbouquetid absent")
    public void CD_2_10_v1_myBox_ottbouquetidAbsent_Invalid() {
        MyboxRequestParams params = myboxRequestParams();
        Allure.parameter("mybox.epochMs", String.valueOf(params.epochMs));
        Response r = contentDetailApi.getMyboxRaw(
                params.epochMs,
                params.limit,
                params.offset,
                null,
                Set.of("ottbouquetid")
        );
        attachAndAssertMyboxBadRequest(r, "content-detail-mybox-ottbouquetid-absent");
    }

    @Test(priority = 217, description = "CD_2.10_v1_myBox_ottbouquetidInvalid_Invalid")
    @Story("CD_2.10_v1_myBox_ottbouquetidInvalid_Invalid — GET /content-detail-service/pub/v1/mybox/{dayEpochMs} — invalid ottbouquetid")
    public void CD_2_10_v1_myBox_ottbouquetidInvalid_Invalid() {
        MyboxRequestParams params = myboxRequestParams();
        Allure.parameter("mybox.epochMs", String.valueOf(params.epochMs));
        Allure.parameter("mybox.ottbouquetid", "INVALID");
        Response r = contentDetailApi.getMyboxRaw(
                params.epochMs,
                params.limit,
                params.offset,
                Map.of("ottbouquetid", "INVALID"),
                null
        );
        attachAndAssertMyboxBadRequest(r, "content-detail-mybox-ottbouquetid-invalid");
    }

    @Test(priority = 218, description = "CD_2.10_v1_myBox_isCDVREnabled_NotNull_Valid")
    @Story("CD_2.10_v1_myBox_isCDVREnabled_NotNull_Valid — GET /content-detail-service/pub/v1/mybox/{dayEpochMs} — isCDVREnabled not null")
    public void CD_2_10_v1_myBox_isCDVREnabled_NotNull_Valid() {
        Response r = getMyboxSuccessResponse();
        assertJsonFieldNotNullWherePresent(r, "isCDVREnabled");
    }

    @Test(priority = 219, description = "CD_2.10_v1_myBox_dvbTriplet_NotNull_Valid")
    @Story("CD_2.10_v1_myBox_dvbTriplet_NotNull_Valid — GET /content-detail-service/pub/v1/mybox/{dayEpochMs} — dvbTriplet not null")
    public void CD_2_10_v1_myBox_dvbTriplet_NotNull_Valid() {
        Response r = getMyboxSuccessResponse();
        assertJsonFieldNotNullWherePresent(r, "dvbTriplet");
    }

    @Test(priority = 220, description = "CD_2.17_v1_getFilters_Valid")
    @Story("CD_2.17_v1_getFilters_Valid — GET /content-detail-service/pub/v1/filter/")
    public void CD_2_17_v1_getFilters_Valid() {
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

    @Test(priority = 230, description = "CD_2.11_v2_miniMyBox_Valid")
    @Story("CD_2.11_v2_miniMyBox_Valid — GET /content-detail-service/pub/v2/mini-mybox/{dayEpochMs}")
    public void CD_2_11_v2_miniMyBox_Valid() {
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

    private Response getMyboxSuccessResponse() {
        MyboxRequestParams params = myboxRequestParams();
        Allure.parameter("mybox.epochMs", String.valueOf(params.epochMs));
        Response r = contentDetailApi.getMyboxRaw(params.epochMs, params.limit, params.offset);
        attachAndAssertEnvelope(r, "content-detail-mybox", "vrgo.content.detail.mybox.expected.message");
        return r;
    }

    private MyboxRequestParams myboxRequestParams() {
        long epoch = pickEpochMs(
                "vrgo.content.detail.mybox.epoch.ms",
                "vrgo.content.detail.channel.day.timezone"
        );
        int limit = parsePositiveInt(config.getProperty("vrgo.content.detail.mybox.limit"), 1000);
        int offset = parseNonNegativeInt(config.getProperty("vrgo.content.detail.mybox.offset"), 0);
        return new MyboxRequestParams(epoch, limit, offset);
    }

    private void attachAndAssertMyboxBadRequest(Response r, String attachmentName) {
        AllureAttachmentUtils.attachJson(attachmentName, r.asString());
        r.then().statusCode(400);
    }

    private void attachAndAssertSeriesDetailBadRequest(Response r, String attachmentName) {
        AllureAttachmentUtils.attachJson(attachmentName, r.asString());
        r.then().statusCode(400);
    }

    private void attachAndAssertEventsBadRequest(Response r, String attachmentName, String expectedErrorCode) {
        AllureAttachmentUtils.attachJson(attachmentName, r.asString());
        r.then()
                .statusCode(400)
                .body("errorCode", equalTo(expectedErrorCode));
    }

    private void attachAndAssertEpisodeHierarchyBadRequest(
            Response r,
            String attachmentName,
            String expectedErrorCode
    ) {
        AllureAttachmentUtils.attachJson(attachmentName, r.asString());
        r.then()
                .statusCode(400)
                .body("errorCode", equalTo(expectedErrorCode));
    }

    /**
     * Fails when {@code fieldName} is present on any object in the response tree but its value is JSON null.
     */
    private static void assertJsonFieldNotNullWherePresent(Response r, String fieldName) {
        JsonNode root;
        try {
            root = JsonUtils.mapper().readTree(r.asString());
        } catch (JsonProcessingException e) {
            Assert.fail("Failed to parse response JSON for " + fieldName + " check: " + e.getMessage());
            return;
        }
        List<String> nullPaths = new ArrayList<>();
        collectNullFieldPaths(root, fieldName, "$", nullPaths);
        if (!nullPaths.isEmpty()) {
            Assert.fail("Expected non-null " + fieldName + " wherever present; null at: " + nullPaths);
        }
    }

    /**
     * Fails when {@code fieldName} is present on any object in the response tree but its value is not {@code false}.
     */
    private static void assertJsonBooleanFieldFalseWherePresent(Response r, String fieldName) {
        JsonNode root;
        try {
            root = JsonUtils.mapper().readTree(r.asString());
        } catch (JsonProcessingException e) {
            Assert.fail("Failed to parse response JSON for " + fieldName + " check: " + e.getMessage());
            return;
        }
        List<String> invalidPaths = new ArrayList<>();
        collectNonFalseBooleanFieldPaths(root, fieldName, "$", invalidPaths);
        if (!invalidPaths.isEmpty()) {
            Assert.fail("Expected " + fieldName + " to be false wherever present; found: " + invalidPaths);
        }
    }

    private static void collectNonFalseBooleanFieldPaths(
            JsonNode node,
            String fieldName,
            String path,
            List<String> invalidPaths
    ) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            if (node.has(fieldName)) {
                JsonNode value = node.get(fieldName);
                if (value == null || value.isNull() || !value.isBoolean() || value.asBoolean()) {
                    invalidPaths.add(path + "." + fieldName + "=" + value);
                }
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                collectNonFalseBooleanFieldPaths(entry.getValue(), fieldName, path + "." + entry.getKey(), invalidPaths);
            }
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collectNonFalseBooleanFieldPaths(node.get(i), fieldName, path + "[" + i + "]", invalidPaths);
            }
        }
    }

    private static void collectNullFieldPaths(
            JsonNode node,
            String fieldName,
            String path,
            List<String> nullPaths
    ) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            JsonNode value = node.get(fieldName);
            if (value != null && value.isNull()) {
                nullPaths.add(path + "." + fieldName);
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                collectNullFieldPaths(entry.getValue(), fieldName, path + "." + entry.getKey(), nullPaths);
            }
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collectNullFieldPaths(node.get(i), fieldName, path + "[" + i + "]", nullPaths);
            }
        }
    }

    private static final class MyboxRequestParams {
        private final long epochMs;
        private final int limit;
        private final int offset;

        private MyboxRequestParams(long epochMs, int limit, int offset) {
            this.epochMs = epochMs;
            this.limit = limit;
            this.offset = offset;
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
