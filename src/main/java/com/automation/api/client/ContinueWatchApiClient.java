package com.automation.api.client;

import com.automation.api.auth.VrgoAuthSupport;
import com.automation.api.config.EnvironmentConfig;
import com.automation.api.model.vrgo.SubscriberContinueWatchRequest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * VRGO continue-watch: read (subscriber-event), delete (subscriber-event), recent-by-id, recent-by-series,
 * contents progress, boxset-linked movies, watch-again list, CW progress ({@code cw/v3/progress}),
 * and write (subscriber-activity-producer).
 * <p>
 * {@code Authorization}: {@code vrgo.bearer.token} then {@code VRGO_BEARER_TOKEN}.<br>
 * {@code x-api-key}: JVM {@code vrgo.x.api.key}, then {@code VRGO_X_API_KEY}, then {@code vrgo.x.api.key} from the active environment file.
 */
public class ContinueWatchApiClient extends BaseApiClient {

    private static final String VRGO_HEADER_PREFIX = "vrgo.header.";

    private final String continueWatchPath;
    private final String continueWatchRecentPath;
    private final String continueWatchRecentBySeriesPath;
    private final String continueWatchContentsProgressPath;
    private final String continueWatchBoxsetContentPath;
    private final String continueWatchDeletePath;
    private final String subscriberContinueWatchPath;
    private final String watchAgainPath;
    private final String cwProgressV3Path;

    public ContinueWatchApiClient(EnvironmentConfig config) {
        super(config, config.getProperty("vrgo.base.url"));
        this.continueWatchPath = config.getProperty(
                "vrgo.continue.watch.path",
                "/subscriber-event-service/v3/continue-watch/continue"
        );
        this.continueWatchRecentPath = config.getProperty(
                "vrgo.continue.watch.recent.path",
                "/subscriber-event-service/v3/continue-watch/content/recent"
        );
        this.continueWatchRecentBySeriesPath = config.getProperty(
                "vrgo.continue.watch.recent.series.path",
                "/subscriber-event-service/v3/continue-watch/content/recent/{seriesId}"
        );
        this.continueWatchContentsProgressPath = config.getProperty(
                "vrgo.continue.watch.contents.progress.path",
                "/subscriber-event-service/v3/continue-watch/contents/progress"
        );
        this.continueWatchBoxsetContentPath = config.getProperty(
                "vrgo.continue.watch.boxset.content.path",
                "/subscriber-event-service/v3/continue-watch/content/boxset/{boxsetId}"
        );
        this.continueWatchDeletePath = config.getProperty(
                "vrgo.continue.watch.delete.path",
                "/subscriber-event-service/v3/continue-watch/"
        );
        this.subscriberContinueWatchPath = config.getProperty(
                "vrgo.subscriber.continue.watch.path",
                "/subscriber-activity-producer/v3/subscriber-continue-watch"
        );
        this.watchAgainPath = config.getProperty(
                "vrgo.watch.again.path",
                "/subscriber-event-service/v3/watch-again"
        );
        this.cwProgressV3Path = config.getProperty(
                "vrgo.cw.progress.path",
                "/subscriber-event-service/cw/v3/progress"
        );
    }

    public Response getContinueWatchRaw(int limit, int offset, boolean isEntitlementEnabled) {
        return vrgoGiven()
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .queryParam("isEntitlementEnabled", isEntitlementEnabled)
                .when()
                .get(continueWatchPath);
    }

    /**
     * GET whether the given content id is present as recent continue-watch for this subscriber (subscriber-event).
     *
     * @param contentType query {@code contentType} (e.g. {@code VOD} — align with POST body for that asset)
     * @param region      query {@code region} (e.g. {@code Malaysia})
     * @param contentId   query {@code contentId}
     */
    public Response getContinueWatchRecentContentRaw(String contentType, String region, String contentId) {
        return vrgoGiven()
                .queryParam("contentType", contentType)
                .queryParam("region", region)
                .queryParam("contentId", contentId)
                .when()
                .get(continueWatchRecentPath);
    }

    /**
     * GET recent continue-watch rows for all episodes under a series (subscriber-event).
     *
     * @param seriesId    path segment after {@code .../content/recent/}
     * @param contentType query {@code contentType} (e.g. {@code VOD})
     * @param region      query {@code region}
     */
    public Response getContinueWatchRecentBySeriesRaw(String seriesId, String contentType, String region) {
        return vrgoGiven()
                .pathParam("seriesId", seriesId)
                .queryParam("contentType", contentType)
                .queryParam("region", region)
                .when()
                .get(continueWatchRecentBySeriesPath);
    }

    /**
     * POST batch progress lookup for continue-watch contents (subscriber-event).
     * <p>
     * Body is a JSON object mapping {@code contentId} string keys to {@code contentType} string values
     * (e.g. {@code {"mov-...":"VOD","bc6d...":"VOD"}}), as in the VRGO client.
     *
     * @param region                 query {@code region} (e.g. {@code Malaysia})
     * @param contentIdToContentType map serialized as the JSON request body
     */
    public Response postContinueWatchContentsProgressRaw(String region, Map<String, String> contentIdToContentType) {
        return vrgoGiven()
                .queryParam("region", region)
                .body(contentIdToContentType)
                .when()
                .post(continueWatchContentsProgressPath);
    }

    /**
     * GET continue-watch entries for movies linked to a boxset (subscriber-event), including progress.
     *
     * @param boxsetId path id, typically {@code BOXSET-<uuid>}
     */
    public Response getContinueWatchBoxsetContentRaw(String boxsetId) {
        return vrgoGiven()
                .pathParam("boxsetId", boxsetId)
                .when()
                .get(continueWatchBoxsetContentPath);
    }

    /**
     * GET watch-again list (subscriber-event), same stack as continue-watch reads.
     *
     * @param limit                  query {@code limit}
     * @param offset                 query {@code offset}
     * @param contentType            query {@code contentType} (e.g. {@code VOD})
     * @param isEntitlementEnabled   query {@code isEntitlementEnabled}
     */
    public Response getWatchAgainRaw(int limit, int offset, String contentType, boolean isEntitlementEnabled) {
        return vrgoGiven()
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .queryParam("contentType", contentType)
                .queryParam("isEntitlementEnabled", isEntitlementEnabled)
                .when()
                .get(watchAgainPath);
    }

    /**
     * POST aggregated CW progress (subscriber-event {@code cw/v3/progress}).
     * <p>
     * Typical body: {@code {"filters":[]}} (empty filter list returns progress for the subscriber context).
     *
     * @param body JSON object (e.g. map with {@code filters} key)
     */
    public Response postCwProgressV3Raw(Map<String, ?> body) {
        return vrgoGiven()
                .body(body)
                .when()
                .post(cwProgressV3Path);
    }

    /**
     * POST subscriber continue-watch (e.g. add a movie/VOD item to CW).
     *
     * @param hasCompletedPlayBack query flag forwarded to the API ({@code hasCompletedPlayBack})
     * @param body                 JSON body (contentId, contentType, watchDuration, subscriberId)
     */
    public Response addSubscriberContinueWatchRaw(boolean hasCompletedPlayBack, SubscriberContinueWatchRequest body) {
        return vrgoGiven()
                .queryParam("hasCompletedPlayBack", hasCompletedPlayBack)
                .body(body)
                .when()
                .post(subscriberContinueWatchPath);
    }

    /**
     * POST subscriber continue-watch without the {@code hasCompletedPlayBack} query parameter.
     * Used for series-episode CW scenarios where completion is determined solely by the
     * {@code watchDuration} to total-duration ratio (97% threshold).
     *
     * @param body JSON body (contentId, contentType, watchDuration, subscriberId)
     */
    public Response addSubscriberContinueWatchNoFlagRaw(SubscriberContinueWatchRequest body) {
        return vrgoGiven()
                .body(body)
                .when()
                .post(subscriberContinueWatchPath);
    }

    /**
     * DELETE continue-watch entry by content id (subscriber-event-service).
     *
     * @param contentId   editorial / asset id
     * @param contentType e.g. {@code VOD}; must match the row returned by GET when possible
     */
    public Response deleteContinueWatchItemRaw(String contentId, String contentType) {
        return vrgoGiven()
                .body(Map.of("contentId", contentId, "contentType", contentType))
                .when()
                .delete(continueWatchDeletePath);
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
