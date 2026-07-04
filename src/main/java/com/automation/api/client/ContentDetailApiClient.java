package com.automation.api.client;

import com.automation.api.auth.VrgoAuthSupport;
import com.automation.api.config.EnvironmentConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * VRGO content-detail-service: series (close/open), seasons, episodes, tv_show, BingeWatch ({@code /episode/...}),
 * episode-hierarchy, movie, boxset (+ binge, childs),
 * trailers, linear (channel, on-air, dates, events, channel-day, 48hr, EPG, bouquet next/prev), MyBox,
 * channel filters, mini-mybox, series/episode view-all, boxset childs, 3PP VOD editorial (movie / episode / season).
 * <p>
 * Auth and static headers match other VRGO clients: {@code vrgo.bearer.token} / {@code VRGO_BEARER_TOKEN},
 * {@code vrgo.x.api.key} / env, and {@code vrgo.header.*} from the active environment file.
 */
public class ContentDetailApiClient extends BaseApiClient {

    private static final String VRGO_HEADER_PREFIX = "vrgo.header.";

    private final String seriesPath;
    private final String seasonEpisodePath;
    private final String seriesEpisodePath;
    private final String seriesEpisodeViewAllPath;
    private final String tvShowPath;
    private final String bingeWatchEpisodePath;
    private final String episodeHierarchyPath;
    private final String moviePath;
    private final String boxsetPath;
    private final String boxsetBingePath;
    private final String boxsetChildsPath;
    private final String boxsetChildsIdHeaderName;
    private final String trailerPath;
    private final String channelPath;
    private final String channelOnAirPath;
    private final String channelDatesPath;
    private final String eventsPath;
    private final String channelDayPath;
    private final String channelDay48hrPath;
    private final String epgPath;
    private final String channelNeighborPath;
    private final String myboxChannelsPath;
    private final String myboxGenresPath;
    private final String channelFiltersPath;
    private final String miniMyboxPath;
    private final String threePpVodMoviePath;
    private final String threePpVodEpisodePath;
    private final String threePpVodSeasonPath;

    public ContentDetailApiClient(EnvironmentConfig config) {
        super(config, config.getProperty("vrgo.base.url"));
        this.seriesPath = config.getProperty(
                "vrgo.content.detail.series.path",
                "/content-detail-service/pub/v1/series/{seriesId}"
        );
        this.seasonEpisodePath = config.getProperty(
                "vrgo.content.detail.season.episode.path",
                "/content-detail-service/pub/v1/season_episode/{seasonId}"
        );
        this.seriesEpisodePath = config.getProperty(
                "vrgo.content.detail.series.episode.path",
                "/content-detail-service/pub/v1/series_episode/{seriesId}"
        );
        this.seriesEpisodeViewAllPath = config.getProperty(
                "vrgo.content.detail.series.episode.viewall.path",
                "/content-detail-service/pub/v1/series/{seriesId}/episode/{episodeId}"
        );
        this.tvShowPath = config.getProperty(
                "vrgo.content.detail.tv.show.path",
                "/content-detail-service/pub/v1/tv_show/{episodeId}"
        );
        this.bingeWatchEpisodePath = config.getProperty(
                "vrgo.content.detail.binge.watch.path",
                "/content-detail-service/pub/v1/episode/{episodeId}/{direction}"
        );
        this.episodeHierarchyPath = config.getProperty(
                "vrgo.content.detail.episode.hierarchy.path",
                "/content-detail-service/pub/v1/episode-hierarchy/{episodeId}/{direction}"
        );
        this.moviePath = config.getProperty(
                "vrgo.content.detail.movie.path",
                "/content-detail-service/pub/v1/movie/{movieId}"
        );
        this.boxsetPath = config.getProperty(
                "vrgo.content.detail.boxset.path",
                "/content-detail-service/pub/v1/boxset/{boxsetId}"
        );
        this.boxsetBingePath = config.getProperty(
                "vrgo.content.detail.boxset.binge.path",
                "/content-detail-service/pub/v1/boxset/{boxsetId}/binge"
        );
        this.boxsetChildsPath = config.getProperty(
                "vrgo.content.detail.boxset.childs.path",
                "/content-detail-service/pub/v1/boxset/childs"
        );
        this.boxsetChildsIdHeaderName = config.getProperty(
                "vrgo.content.detail.boxset.childs.header.name",
                "boxsetid"
        );
        this.trailerPath = config.getProperty(
                "vrgo.content.detail.trailer.path",
                "/content-detail-service/pub/v1/trailer/{contentType}/{contentId}"
        );
        this.channelPath = config.getProperty(
                "vrgo.content.detail.channel.path",
                "/content-detail-service/pub/v1/channel/{channelId}"
        );
        this.channelOnAirPath = config.getProperty(
                "vrgo.content.detail.channel.on.air.path",
                "/content-detail-service/pub/v1/on-air/{channelId}"
        );
        this.channelDatesPath = config.getProperty(
                "vrgo.content.detail.channel.dates.path",
                "/content-detail-service/pub/v1/channel/{channelId}/dates"
        );
        this.eventsPath = config.getProperty(
                "vrgo.content.detail.events.path",
                "/content-detail-service/pub/v1/events/{displayDate}"
        );
        this.channelDayPath = config.getProperty(
                "vrgo.content.detail.channel.day.path",
                "/content-detail-service/pub/v1/channel-day/{channelId}/{dayEpochMs}"
        );
        this.channelDay48hrPath = config.getProperty(
                "vrgo.content.detail.channel.day.48hr.path",
                "/content-detail-service/pub/v1/channel-day/48-hours/{channelId}/{dayEpochMs}"
        );
        this.epgPath = config.getProperty(
                "vrgo.content.detail.epg.path",
                "/content-detail-service/pub/v1/epg/{epgId}"
        );
        this.channelNeighborPath = config.getProperty(
                "vrgo.content.detail.channel.neighbor.path",
                "/content-detail-service/pub/v1/channel/{channelId}/{neighborState}"
        );
        this.myboxChannelsPath = config.getProperty(
                "vrgo.content.detail.mybox.channels.path",
                "/content-detail-service/pub/v1/mybox/channels"
        );
        this.myboxGenresPath = config.getProperty(
                "vrgo.content.detail.mybox.genres.path",
                "/content-detail-service/pub/v1/mybox/genres"
        );
        this.channelFiltersPath = config.getProperty(
                "vrgo.content.detail.channel.filters.path",
                "/content-detail-service/pub/v1/filter/"
        );
        this.miniMyboxPath = config.getProperty(
                "vrgo.content.detail.mini.mybox.path",
                "/content-detail-service/pub/v2/mini-mybox/{dayEpochMs}"
        );
        this.threePpVodMoviePath = config.getProperty(
                "vrgo.content.detail.3ppvod.movie.path",
                "/content-detail-service/pub/v1/3PPVODMovie/{contentId}"
        );
        this.threePpVodEpisodePath = config.getProperty(
                "vrgo.content.detail.3ppvod.episode.path",
                "/content-detail-service/pub/v1/3PPVODEpisode/{contentId}"
        );
        this.threePpVodSeasonPath = config.getProperty(
                "vrgo.content.detail.3ppvod.season.path",
                "/content-detail-service/pub/v1/3PPVODSeason/{contentId}"
        );
    }

    /** GET series detail (close or open editorial id); query params match VRGO parity with catalogue flows. */
    public Response getSeriesDetailRaw(
            String seriesId,
            String region,
            String contentType,
            boolean isEntitlementEnabled
    ) {
        return vrgoGiven()
                .pathParam("seriesId", seriesId)
                .queryParam("region", region)
                .queryParam("contentType", contentType)
                .queryParam("isEntitlementEnabled", isEntitlementEnabled)
                .when()
                .get(seriesPath);
    }

    public Response getSeasonEpisodesRaw(String seasonId, int limit, int offset, String sort) {
        return vrgoGiven()
                .pathParam("seasonId", seasonId)
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .queryParam("sort", sort)
                .when()
                .get(seasonEpisodePath);
    }

    /** GET episodes listed under an open-series editorial id ({@code series_episode}). */
    public Response getSeriesEpisodesRaw(String seriesId, int limit, int offset, String sort) {
        return vrgoGiven()
                .pathParam("seriesId", seriesId)
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .queryParam("sort", sort)
                .when()
                .get(seriesEpisodePath);
    }

    public Response getSeriesEpisodeViewAllRaw(
            String seriesId,
            String episodeId,
            String type,
            int size,
            boolean isOpenSeries,
            int episodeSortOrder,
            int seasonSortOrder,
            String seasonsSortOrders
    ) {
        return vrgoGiven()
                .pathParam("seriesId", seriesId)
                .pathParam("episodeId", episodeId)
                .queryParam("type", type)
                .queryParam("size", size)
                .queryParam("isOpenSeries", isOpenSeries)
                .queryParam("episodeSortOrder", episodeSortOrder)
                .queryParam("seasonSortOrder", seasonSortOrder)
                .queryParam("seasonsSortOrders", seasonsSortOrders)
                .when()
                .get(seriesEpisodeViewAllPath);
    }

    public Response getTvShowRaw(String episodeId, int limit, int offset, String sort) {
        return vrgoGiven()
                .pathParam("episodeId", episodeId)
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .queryParam("sort", sort)
                .when()
                .get(tvShowPath);
    }

    /**
     * BingeWatch: GET {@code /pub/v1/episode/{episodeId}/{direction}} — adjacent episode;
     * {@code direction} is {@code NEXT} or {@code PREVIOUS} only.
     */
    public Response getBingeWatchEpisodeRaw(String episodeId, String direction) {
        return vrgoGiven()
                .pathParam("episodeId", episodeId)
                .pathParam("direction", direction)
                .when()
                .get(bingeWatchEpisodePath);
    }

    /**
     * Episode hierarchy: GET {@code /pub/v1/episode-hierarchy/{episodeId}/{direction}} —
     * {@code direction} is {@code NEXT}, {@code CURRENT}, or {@code PREVIOUS}.
     */
    public Response getEpisodeHierarchyRaw(String episodeId, String direction) {
        return vrgoGiven()
                .pathParam("episodeId", episodeId)
                .pathParam("direction", direction)
                .when()
                .get(episodeHierarchyPath);
    }

    /** 3PP VOD movie editorial detail ({@code 3ppvodmovie} / {@code 3PPVODMovie}). */
    public Response getThreePpVodMovieRaw(String contentId) {
        return vrgoGiven()
                .pathParam("contentId", contentId)
                .when()
                .get(threePpVodMoviePath);
    }

    /** 3PP VOD episode editorial detail ({@code 3ppvodepisode} / {@code 3PPVODEpisode}). */
    public Response getThreePpVodEpisodeRaw(String contentId) {
        return vrgoGiven()
                .pathParam("contentId", contentId)
                .when()
                .get(threePpVodEpisodePath);
    }

    /** 3PP VOD season editorial detail ({@code 3ppvodseason} / {@code 3PPVODSeason}). */
    public Response getThreePpVodSeasonRaw(String contentId) {
        return vrgoGiven()
                .pathParam("contentId", contentId)
                .when()
                .get(threePpVodSeasonPath);
    }

    public Response getMovieRaw(String movieId) {
        return vrgoGiven()
                .pathParam("movieId", movieId)
                .when()
                .get(moviePath);
    }

    public Response getBoxsetRaw(String boxsetId) {
        return vrgoGiven()
                .pathParam("boxsetId", boxsetId)
                .when()
                .get(boxsetPath);
    }

    /** GET {@code /pub/v1/boxset/{boxsetId}/binge} — boxset binge-watch payload. */
    public Response getBoxsetBingeRaw(String boxsetId) {
        return vrgoGiven()
                .pathParam("boxsetId", boxsetId)
                .when()
                .get(boxsetBingePath);
    }

    public Response getBoxsetChildsRaw(
            String boxsetId,
            int fromMovie,
            int pageSizeMovie,
            int fromTvShow,
            int pageSizeTvShow,
            int fromTrailer,
            int pageSizeTrailer
    ) {
        return vrgoGiven()
                .header(boxsetChildsIdHeaderName, boxsetId)
                .queryParam("fromMovie", fromMovie)
                .queryParam("pageSizeMovie", pageSizeMovie)
                .queryParam("fromTvShow", fromTvShow)
                .queryParam("pageSizeTvShow", pageSizeTvShow)
                .queryParam("fromTrailer", fromTrailer)
                .queryParam("pageSizeTrailer", pageSizeTrailer)
                .when()
                .get(boxsetChildsPath);
    }

    /** {@code contentType} is lowercase: {@code movie}, {@code series}, {@code boxset}. */
    public Response getTrailerRaw(String contentTypeLower, String contentId) {
        return vrgoGiven()
                .pathParam("contentType", contentTypeLower)
                .pathParam("contentId", contentId)
                .when()
                .get(trailerPath);
    }

    public Response getChannelRaw(String channelId) {
        return vrgoGiven()
                .pathParam("channelId", channelId)
                .when()
                .get(channelPath);
    }

    public Response getOnAirRaw(String channelId) {
        return vrgoGiven()
                .pathParam("channelId", channelId)
                .when()
                .get(channelOnAirPath);
    }

    public Response getChannelDatesRaw(String channelId) {
        return vrgoGiven()
                .pathParam("channelId", channelId)
                .when()
                .get(channelDatesPath);
    }

    /** Header {@code channelids} (comma-separated linear channel editorial ids). */
    public Response getEventsRaw(String displayDate, String channelIdsHeader) {
        return vrgoGiven()
                .header("channelids", channelIdsHeader)
                .pathParam("displayDate", displayDate)
                .when()
                .get(eventsPath);
    }

    /**
     * GET channel-day or 48-hour grid; {@code pathTemplate} is typically {@link #getChannelDayPathTemplate()}
     * ({@code .../channel-day/{channelId}/{dayEpochMs}}) or {@link #getChannelDay48hrPathTemplate()}
     * ({@code .../channel-day/48-hours/{channelId}/{dayEpochMs}}).
     */
    public Response getChannelDayRaw(String pathTemplate, String channelId, long dayEpochMs) {
        return vrgoGiven()
                .pathParam("channelId", channelId)
                .pathParam("dayEpochMs", dayEpochMs)
                .when()
                .get(pathTemplate);
    }

    public Response getEpgRaw(String epgId) {
        String encodedEpgId = encodeEpgPathId(epgId);
        String resolvedPath = epgPath.replace("{epgId}", encodedEpgId);
        return vrgoGiven()
                .urlEncodingEnabled(false)
                .when()
                .get(resolvedPath);
    }

    /**
     * Channel-day {@code eventId} values use colons (e.g. {@code 58148111:uri:prg:20120014:B13670362});
     * the EPG path segment must be percent-encoded ({@code %3A}).
     */
    private static String encodeEpgPathId(String epgId) {
        if (epgId == null || epgId.isBlank()) {
            return epgId;
        }
        String trimmed = epgId.strip();
        if (!trimmed.contains(":")) {
            return trimmed;
        }
        return URLEncoder.encode(trimmed, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** {@code neighborState} is typically {@code NEXT} or {@code PREVIOUS} (uppercase path segment). */
    public Response getChannelNeighborRaw(String channelId, String neighborState) {
        return vrgoGiven()
                .pathParam("channelId", channelId)
                .pathParam("neighborState", neighborState)
                .when()
                .get(channelNeighborPath);
    }

    public Response getMyboxChannelsRaw() {
        return vrgoGiven()
                .when()
                .get(myboxChannelsPath);
    }

    public Response getMyboxGenresRaw() {
        return vrgoGiven()
                .when()
                .get(myboxGenresPath);
    }

    public Response getChannelFiltersRaw() {
        return vrgoGiven()
                .when()
                .get(channelFiltersPath);
    }

    public Response getMiniMyboxRaw(long dayEpochMs, int limit, int offset, String epgEnum) {
        return vrgoGiven()
                .pathParam("dayEpochMs", dayEpochMs)
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .queryParam("epgEnum", epgEnum)
                .when()
                .get(miniMyboxPath);
    }

    public String getChannelDayPathTemplate() {
        return channelDayPath;
    }

    public String getChannelDay48hrPathTemplate() {
        return channelDay48hrPath;
    }

    private RequestSpecification vrgoGiven() {
        RequestSpecification r = given().spec(spec);

        String token = VrgoAuthSupport.getBearerToken(environmentConfig);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "Set vrgo.bearer.token (e.g. via test base setup), VRGO_BEARER_TOKEN, or -Dvrgo.bearer.token."
            );
        }
        String authorization = token.regionMatches(true, 0, "Bearer ", 0, 7) ? token : "Bearer " + token;
        r = r.header("Authorization", authorization);

        String apiKey = firstNonBlank(
                System.getProperty("vrgo.x.api.key"),
                System.getenv("VRGO_X_API_KEY"),
                environmentConfig.getProperty("vrgo.x.api.key"));
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Set vrgo.x.api.key (properties / test base / env), VRGO_X_API_KEY, or -Dvrgo.x.api.key."
            );
        }
        r = r.header("x-api-key", apiKey);

        Map<String, String> staticHeaders = environmentConfig.propertiesWithPrefix(VRGO_HEADER_PREFIX);
        for (Map.Entry<String, String> e : staticHeaders.entrySet()) {
            r = r.header(e.getKey(), e.getValue());
        }
        return r;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
