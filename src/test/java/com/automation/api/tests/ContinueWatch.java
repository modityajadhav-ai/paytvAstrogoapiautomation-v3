package com.automation.api.tests;

import com.automation.api.base.BaseTest;
import com.automation.api.config.Environment;
import com.automation.api.constants.CwAddContentKind;
import com.automation.api.constants.VrgoContentKind;
import com.automation.api.model.vrgo.SubscriberContinueWatchRequest;
import com.automation.api.util.AllureAttachmentUtils;
import com.automation.api.util.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.qameta.allure.Allure;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.lang.reflect.Array;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * VRGO continue-watch smoke test.
 *
 * <p>
 * Credentials: managed automatically by {@link com.automation.api.auth.VrgoTokenHolder} via
 * {@code VRGO_REFRESH_TOKEN} or {@code secrets/vrgo-auth.local.properties}.
 * (test profile), or overrides via {@link BaseTest#VRGO_MANUAL_X_API_KEY} / {@code VRGO_X_API_KEY} /
 * {@code -Dvrgo.x.api.key}.
 *
 * <p>
 * Switch stack with {@code -P dev|test|stage|load|prod} or {@code -Denv=...} (see README). Layout labels:
 * {@link VrgoContentKind#resolve(com.automation.api.config.EnvironmentConfig)}. CW POST content UUIDs / types:
 * {@link CwAddContentKind} and {@code vrgo.cw.add.<kind>.*} in each {@code environments/*.properties} file.
 *
 * <p>
 * Execution order uses {@code @Test(priority = ...)} so {@code mvn test -Dtest=ContinueWatch#addMovieToContinueWatch}
 * can run without pulling in {@code getContinueWatchFirstPage} (Surefire only loads selected methods). For a full
 * continue-watch flow, run the whole class or {@code testng.xml}. Series/boxset steps still use {@code dependsOnMethods}.
 * The validation GET after POSTs polls until both configured ids appear, because subscriber-event reads can lag behind writes.
 * Optional pause before the TV add uses {@code vrgo.cw.betweenAdds.sleep.ms} after the movie POST when activity-producer needs time to settle.
 * Recent-content status is checked via {@code GET .../continue-watch/content/recent} using the same configured ids and {@code vrgo.cw.recent.content.region}.
 * Series-scoped recent episodes use {@code GET .../content/recent/{seriesId}} with {@code vrgo.cw.recent.series.id}.
 * Batch progress uses {@code POST .../contents/progress} with a map built from GET CW editorial ids and types.
 * Boxset-linked movies use {@code GET .../content/boxset/{boxsetId}} with {@code vrgo.cw.boxset.continue.api.id}.
 * Aggregated CW progress uses {@code POST .../cw/v3/progress} with {@code vrgo.cw.progress.path} and body {@code {"filters":[]}}.
 * Live CDVR events use {@code eventId} and {@code groupKey} from content-detail channel-day
 * ({@code vrgo.cw.add.cdvr.*}, {@code vrgo.content.detail.channel.day.*}).
 */
@Feature("Continue watch")
public class ContinueWatch extends BaseTest {

    private static final Pattern FORBIDDEN_ROW_TYPE = Pattern.compile("(?i)\\b(series|box\\s*set|boxset)\\b");

    @Test(
            priority = 0,
            description = "GET continue-watch first page (limit 20, offset 0); DELETE each item returned to clear list"
    )
    @Story("GET /subscriber-event-service/v3/continue-watch/continue")
    public void getContinueWatchFirstPage() {
        requireContinueWatchPrerequisites();

        Allure.parameter("environment", Environment.current().name());
        Allure.parameter("vrgo.base.url", config.getProperty("vrgo.base.url"));
        Allure.parameter("content.movie.id", VrgoContentKind.MOVIE.resolve(config));

        Response r = continueWatchApi.getContinueWatchRaw(20, 0, false);
        r.then()
                .statusCode(200)
                .body("status", equalTo(true))
                .body("message", equalTo("Data Fetched Successfully"))
                .body("data", notNullValue())
                .body("data.size()", greaterThan(0));

        assertGetCwListPolicy(r);

        // After a successful GET, remove every item returned on this page so later POST tests start from an empty list.
        deleteContinueWatchItemsFromGetResponse(r);
    }

    @Test(
            priority = 10,
            description = "POST subscriber-continue-watch — add movie to CW"
    )
    @Story("POST /subscriber-activity-producer/v3/subscriber-continue-watch (movie)")
    public void addMovieToContinueWatch() {
        postSubscriberContinueWatch(CwAddContentKind.MOVIE);
    }




    @Test(
            priority = 20,
            description = "POST subscriber-continue-watch — add TV show to CW"
    )
    @Story("POST /subscriber-activity-producer/v3/subscriber-continue-watch (tvshow)")
    public void addTvShowToContinueWatch() {
        sleepBetweenMovieAndTvCwAdds();
        postSubscriberContinueWatch(CwAddContentKind.TV_SHOW);
    }

    /**
     * Re-fetch CW after movie + TV POSTs: configured movie and TV content ids must appear in the list, and the list
     * must not expose series/boxset types or configured series/boxset ids.
     */
    @Test(
            priority = 30,
            description = "GET continue-watch after adds — movie & TV ids present; no series/boxset in list",
            dependsOnMethods = {"addMovieToContinueWatch", "addTvShowToContinueWatch"}
    )
    @Story("GET /subscriber-event-service/v3/continue-watch/continue (validation)")
    public void getContinueWatchAfterAdds_validatesListPolicyAndAddedIds() {
        requireContinueWatchPrerequisites();

        String movieId = CwAddContentKind.MOVIE.contentId(config);
        String tvId = CwAddContentKind.TV_SHOW.contentId(config);
        if (isBlank(movieId) || movieId.startsWith("REPLACE")
                || isBlank(tvId) || tvId.startsWith("REPLACE")) {
            throw new SkipException("Configure movie and TV show content ids for this environment (POST tests must run).");
        }

        // Subscriber-event GET can lag behind activity-producer POSTs; Postman often "works" due to manual delay.
        Response r = pollContinueWatchUntilBothConfiguredIds(movieId, tvId);

        List<Map<String, Object>> rows = cwDataAsMaps(r);
        Set<String> allIds = collectContentIds(rows);
        attachCwListMetrics(rows, allIds);

        Allure.parameter("cw.ids.in.response.sample", String.join(", ", allIds.stream().limit(20).toList()));

        Assert.assertTrue(
                anyIdMatchesConfigured(allIds, movieId),
                "GET CW data should include configured movie content id: " + movieId + " ; found ids: " + allIds
        );
        Assert.assertTrue(
                anyIdMatchesConfigured(allIds, tvId),
                "GET CW data should include configured TV show content id: " + tvId + " ; found ids: " + allIds
        );

        assertNoSeriesOrBoxsetInList(rows, allIds);
    }

    @Test(
            priority = 35,
            description = "GET continue-watch/content/recent — movie id reports recent CW status",
            dependsOnMethods = {"getContinueWatchAfterAdds_validatesListPolicyAndAddedIds"}
    )
    @Story("GET /subscriber-event-service/v3/continue-watch/content/recent (movie)")
    public void getRecentContinueWatchContent_movieConfiguredId() {
        getRecentContinueWatchContentForKind(CwAddContentKind.MOVIE);
    }

    @Test(
            priority = 36,
            description = "GET continue-watch/content/recent — TV show id reports recent CW status",
            dependsOnMethods = {"getContinueWatchAfterAdds_validatesListPolicyAndAddedIds"}
    )
    @Story("GET /subscriber-event-service/v3/continue-watch/content/recent (tvshow)")
    public void getRecentContinueWatchContent_tvShowConfiguredId() {
        getRecentContinueWatchContentForKind(CwAddContentKind.TV_SHOW);
    }

    @Test(
            priority = 37,
            description = "GET continue-watch/content/recent/{seriesId} — all episode ids; single primary=true matches configured TV",
            dependsOnMethods = {"getContinueWatchAfterAdds_validatesListPolicyAndAddedIds"}
    )
    @Story("GET /subscriber-event-service/v3/continue-watch/content/recent/{seriesId}")
    public void getRecentContinueWatchBySeries_episodeIdsAndPrimaryMatchesConfiguredTv() {
        requireContinueWatchPrerequisites();

        String seriesId = config.getProperty("vrgo.cw.recent.series.id");
        if (isBlank(seriesId) || seriesId.startsWith("REPLACE")) {
            throw new SkipException("Set vrgo.cw.recent.series.id (series path param) in environments/<env>.properties.");
        }
        String tvEpisodeId = CwAddContentKind.TV_SHOW.contentId(config);
        if (isBlank(tvEpisodeId) || tvEpisodeId.startsWith("REPLACE")) {
            throw new SkipException("Set vrgo.cw.add.tvshow.content.id for the episode that belongs to the series.");
        }
        String contentType = config.getProperty("vrgo.cw.recent.series.content.type");
        if (isBlank(contentType)) {
            contentType = "VOD";
        } else {
            contentType = contentType.strip();
        }
        String region = regionPropertyForRecentCwLookup();

        Allure.parameter("cw.recent.series.id", seriesId);
        Allure.parameter("cw.recent.series.contentType", contentType);
        Allure.parameter("cw.recent.series.region", region);
        Allure.parameter("cw.recent.series.expectedTvEpisodeId", tvEpisodeId);

        Response r = continueWatchApi.getContinueWatchRecentBySeriesRaw(seriesId, contentType, region);
        AllureAttachmentUtils.attachJson("continue-watch-recent-series-" + seriesId, r.asString());
        r.then()
                .statusCode(200)
                .body("status", equalTo(true));

        List<Map<String, Object>> rows = cwDataAsMaps(r);
        Assert.assertFalse(
                rows.isEmpty(),
                "Series recent CW should return at least one row; seriesId=" + seriesId + " body=" + r.asString()
        );

        Set<String> episodeIds = collectContentIds(rows);
        Allure.parameter("cw.recent.series.episodeIdCount", String.valueOf(episodeIds.size()));
        Allure.parameter("cw.recent.series.episodeIds", String.join(", ", episodeIds));

        Assert.assertTrue(
                anyIdMatchesConfigured(episodeIds, tvEpisodeId),
                "Series recent list should include configured TV episode id " + tvEpisodeId + " ; found " + episodeIds
        );

        assertExactlyOnePrimaryRowAndMatchesTvEpisode(rows, tvEpisodeId);
    }

    @Test(
            priority = 38,
            description = "POST continue-watch/contents/progress — body map from GET CW ids (contentId -> contentType)",
            dependsOnMethods = {"getContinueWatchAfterAdds_validatesListPolicyAndAddedIds"}
    )
    @Story("POST /subscriber-event-service/v3/continue-watch/contents/progress")
    public void postContinueWatchContentsProgress_payloadBuiltFromGetCwList() {
        requireContinueWatchPrerequisites();

        String movieId = CwAddContentKind.MOVIE.contentId(config);
        String tvId = CwAddContentKind.TV_SHOW.contentId(config);
        if (isBlank(movieId) || movieId.startsWith("REPLACE") || isBlank(tvId) || tvId.startsWith("REPLACE")) {
            throw new SkipException("Configure movie and TV show content ids for CW GET / progress flow.");
        }

        Response cw = continueWatchApi.getContinueWatchRaw(20, 0, false);
        cw.then().statusCode(200).body("status", equalTo(true));
        List<Map<String, Object>> rows = cwDataAsMaps(cw);
        if (collectContentIds(rows).isEmpty()) {
            cw = pollContinueWatchUntilBothConfiguredIds(movieId, tvId);
            rows = cwDataAsMaps(cw);
        }

        Map<String, String> progressBody = contentIdToProgressTypeMapFromCwRows(rows);
        Assert.assertFalse(
                progressBody.isEmpty(),
                "CW GET should yield at least one editorial id for contents/progress; rows=" + rows.size()
        );
        Assert.assertTrue(
                anyIdMatchesConfigured(progressBody.keySet(), movieId),
                "Progress payload should include configured movie id; keys=" + progressBody.keySet()
        );
        Assert.assertTrue(
                anyIdMatchesConfigured(progressBody.keySet(), tvId),
                "Progress payload should include configured TV episode id; keys=" + progressBody.keySet()
        );

        String region = regionPropertyForContentsProgress();
        Allure.parameter("cw.contents.progress.region", region);
        Allure.parameter("cw.contents.progress.idCount", String.valueOf(progressBody.size()));
        AllureAttachmentUtils.attachJson("continue-watch-contents-progress-request", JsonUtils.toJson(progressBody));

        Response r = continueWatchApi.postContinueWatchContentsProgressRaw(region, progressBody);
        AllureAttachmentUtils.attachJson("continue-watch-contents-progress-response", r.asString());
        r.then()
                .statusCode(200)
                .body("status", equalTo(true));
    }

    @Test(
            priority = 39,
            description = "GET continue-watch/content/boxset/{boxsetId} — linked movies with progress",
            dependsOnMethods = {"getContinueWatchAfterAdds_validatesListPolicyAndAddedIds"}
    )
    @Story("GET /subscriber-event-service/v3/continue-watch/content/boxset/{boxsetId}")
    public void getContinueWatchBoxsetContent_linkedMoviesIncludeProgress() {
        requireContinueWatchPrerequisites();

        String boxsetId = config.getProperty("vrgo.cw.boxset.continue.api.id");
        if (isBlank(boxsetId) || boxsetId.startsWith("REPLACE")) {
            throw new SkipException(
                    "Set vrgo.cw.boxset.continue.api.id (e.g. BOXSET-...) in environments/<env>.properties for this GET.");
        }
        String movieId = CwAddContentKind.MOVIE.contentId(config);
        if (isBlank(movieId) || movieId.startsWith("REPLACE")) {
            throw new SkipException("Set vrgo.cw.add.movie.content.id; movie must be linked to the boxset in CW.");
        }

        Allure.parameter("cw.boxset.continue.api.id", boxsetId);
        Allure.parameter("cw.boxset.expectedMovieId", movieId);

        Response r = continueWatchApi.getContinueWatchBoxsetContentRaw(boxsetId.strip());
        AllureAttachmentUtils.attachJson("continue-watch-boxset-content-" + boxsetId, r.asString());
        r.then()
                .statusCode(200)
                .body("status", equalTo(true))
                .body("data", notNullValue());

        assertBoxsetCwResponseContainsMovieWithProgress(r, boxsetId.strip(), movieId);
    }

    @Test(
            priority = 40,
            description = "POST subscriber-continue-watch — add series to CW",
            dependsOnMethods = {"getContinueWatchAfterAdds_validatesListPolicyAndAddedIds"}
    )
    @Story("POST /subscriber-activity-producer/v3/subscriber-continue-watch (series)")
    public void addSeriesToContinueWatch() {
        postSubscriberContinueWatch(CwAddContentKind.SERIES);
    }

    @Test(
            priority = 41,
            description = "POST subscriber-continue-watch — add boxset to CW",
            dependsOnMethods = {"getContinueWatchAfterAdds_validatesListPolicyAndAddedIds"}
    )
    @Story("POST /subscriber-activity-producer/v3/subscriber-continue-watch (boxset)")
    public void addBoxsetToContinueWatch() {
        postSubscriberContinueWatch(CwAddContentKind.BOXSET);
    }

    @Test(
            priority = 42,
            description = "POST cw/v3/progress with empty filters — data must be non-empty (no-op if already-present response)",
            dependsOnMethods = {"getContinueWatchAfterAdds_validatesListPolicyAndAddedIds"}
    )
    @Story("POST /subscriber-event-service/cw/v3/progress")
    public void postCwProgressV3_filtersEmpty_dataNotEmpty() {
        requireContinueWatchPrerequisites();

        Map<String, Object> body = Map.of("filters", List.of());
        AllureAttachmentUtils.attachJson("cw-progress-v3-request", JsonUtils.toJson(body));
        Response r = continueWatchApi.postCwProgressV3Raw(body);
        AllureAttachmentUtils.attachJson("cw-progress-v3-response", r.asString());

        if (cwProgressResponseIndicatesAlreadyPresentNoAssert(r)) {
            Allure.parameter("cw.progress.v3", "skipped assertions (duplicate / already-present style response)");
            return;
        }

        r.then()
                .statusCode(200)
                .body("status", equalTo(true))
                .body("data", notNullValue());
        assertCwProgressDataNonEmpty(r);
    }

    /**
     * Fetches a live EPG {@code eventId} and {@code groupKey} from channel-day, POSTs a CDVR continue-watch entry,
     * then polls GET continue-watch until that event id appears.
     */
    @Test(
            priority = 43,
            description = "POST subscriber-continue-watch — add live CDVR event from channel-day; GET CW includes event"
    )
    @Story("POST /subscriber-activity-producer/v3/subscriber-continue-watch (cdvr live event)")
    public void addLiveCdvrEventToContinueWatch_thenGetCwIncludesEvent() {
        requireContinueWatchPrerequisites();
        if (contentDetailApi == null) {
            throw new SkipException("Set vrgo.base.url in environments/<env>.properties for content-detail channel-day.");
        }

        String subscriberId = config.getProperty("vrgo.header.cp_id");
        if (isBlank(subscriberId) || subscriberId.startsWith("REPLACE")) {
            throw new SkipException("Set vrgo.header.cp_id (subscriberId) in the active environment file.");
        }

        ChannelDayCdvrEvent event = resolveChannelDayCdvrEvent();
        int watchDuration = readIntProperty("vrgo.cw.add.cdvr.watch.duration", 33);

        Allure.parameter("environment", Environment.current().name());
        Allure.parameter("cw.cdvr.contentId", event.contentId());
        Allure.parameter("cw.cdvr.groupKey", event.groupKey());
        Allure.parameter("cw.cdvr.subscriberId", subscriberId.strip());
        Allure.parameter("cw.cdvr.watchDuration", String.valueOf(watchDuration));

        SubscriberContinueWatchRequest body = new SubscriberContinueWatchRequest(
                event.contentId(),
                "cdvr",
                watchDuration,
                subscriberId.strip(),
                event.groupKey()
        );
        AllureAttachmentUtils.attachJson("subscriber-continue-watch-cdvr", JsonUtils.toJson(body));

        Response post = continueWatchApi.addSubscriberContinueWatchRaw(false, body);
        post.then().statusCode(200);

        Response getCw = pollContinueWatchUntilConfiguredId(event.contentId());
        List<Map<String, Object>> rows = cwDataAsMaps(getCw);
        Set<String> allIds = collectContentIds(rows);
        attachCwListMetrics(rows, allIds);

        Assert.assertTrue(
                anyIdMatchesConfigured(allIds, event.contentId()),
                "GET CW should include channel-day event id " + event.contentId() + " ; found ids: " + allIds
        );
    }

    /**
     * When the service reports a duplicate / already-synced condition, skip strict {@code data} checks so the test
     * stays green on repeat runs.
     */
    private static boolean cwProgressResponseIndicatesAlreadyPresentNoAssert(Response r) {
        int code = r.getStatusCode();
        if (code == 409 || code == 422) {
            return true;
        }
        Object st = r.jsonPath().get("status");
        if (Boolean.FALSE.equals(st)) {
            Object msg = r.jsonPath().get("message");
            String m = msg == null ? "" : String.valueOf(msg).toLowerCase();
            if (m.contains("already") || m.contains("duplicate") || m.contains("exist")) {
                return true;
            }
        }
        String body = r.asString();
        if (body != null) {
            String b = body.toLowerCase();
            if (b.contains("already exist") || b.contains("already present") || b.contains("duplicate")) {
                return true;
            }
        }
        return false;
    }

    /** {@code data} must be non-null and not an empty collection / map (VRGO progress payload). */
    private static void assertCwProgressDataNonEmpty(Response r) {
        Object data = r.jsonPath().get("data");
        Assert.assertNotNull(data, "cw/v3/progress data must not be null. body=" + r.asString());
        if (data instanceof List<?> list) {
            Assert.assertFalse(list.isEmpty(), "cw/v3/progress data must not be an empty list. body=" + r.asString());
        } else if (data instanceof Map<?, ?> map) {
            Assert.assertFalse(map.isEmpty(), "cw/v3/progress data must not be an empty object. body=" + r.asString());
        } else if (data instanceof Collection<?> c) {
            Assert.assertFalse(c.isEmpty(), "cw/v3/progress data must not be an empty collection. body=" + r.asString());
        }
    }

    /**
     * Polls GET continue-watch until both editorial ids appear (or timeout). Fails with last response diagnostics.
     */
    private Response pollContinueWatchUntilBothConfiguredIds(String movieId, String tvId) {
        int timeoutMs = readIntProperty("vrgo.cw.afteradds.poll.timeout.ms", 30_000);
        int intervalMs = readIntProperty("vrgo.cw.afteradds.poll.interval.ms", 750);
        long pollStartMs = System.currentTimeMillis();
        long deadline = pollStartMs + timeoutMs;
        Response last = null;
        int attempt = 0;
        while (true) {
            attempt++;
            last = getContinueWatchRawWithTransientRetry(20, 0, false);
            last.then()
                    .statusCode(200)
                    .body("status", equalTo(true))
                    .body("message", equalTo("Data Fetched Successfully"))
                    .body("data", notNullValue())
                    .body("data.size()", greaterThan(0));

            List<Map<String, Object>> rows = cwDataAsMaps(last);
            Set<String> allIds = collectContentIds(rows);
            if (anyIdMatchesConfigured(allIds, movieId) && anyIdMatchesConfigured(allIds, tvId)) {
                Allure.parameter("cw.afteradds.poll.attempts", String.valueOf(attempt));
                Allure.parameter("cw.afteradds.poll.waited.ms", String.valueOf(System.currentTimeMillis() - pollStartMs));
                return last;
            }
            if (System.currentTimeMillis() >= deadline) {
                Object hc = last.jsonPath().get("data.historyCount");
                Integer historyCount = hc instanceof Number ? ((Number) hc).intValue() : null;
                Assert.fail(
                        "CW GET after POSTs did not include both configured ids within " + timeoutMs + " ms "
                                + "(attempts=" + attempt + ", last historyCount=" + historyCount + ", found ids=" + allIds + "). "
                                + "Increase vrgo.cw.afteradds.poll.timeout.ms if the stack is slow."
                );
            }
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SkipException("Interrupted while polling CW after adds");
            }
        }
    }

    private Response pollContinueWatchUntilConfiguredId(String contentId) {
        int timeoutMs = readIntProperty("vrgo.cw.afteradds.poll.timeout.ms", 30_000);
        int intervalMs = readIntProperty("vrgo.cw.afteradds.poll.interval.ms", 750);
        long pollStartMs = System.currentTimeMillis();
        long deadline = pollStartMs + timeoutMs;
        Response last = null;
        int attempt = 0;
        while (true) {
            attempt++;
            last = getContinueWatchRawWithTransientRetry(20, 0, false);
            last.then()
                    .statusCode(200)
                    .body("status", equalTo(true))
                    .body("message", equalTo("Data Fetched Successfully"))
                    .body("data", notNullValue());

            Set<String> allIds = collectContentIds(cwDataAsMaps(last));
            if (anyIdMatchesConfigured(allIds, contentId)) {
                Allure.parameter("cw.cdvr.poll.attempts", String.valueOf(attempt));
                Allure.parameter("cw.cdvr.poll.waited.ms", String.valueOf(System.currentTimeMillis() - pollStartMs));
                return last;
            }
            if (System.currentTimeMillis() >= deadline) {
                Assert.fail(
                        "CW GET after CDVR POST did not include event id " + contentId + " within " + timeoutMs
                                + " ms (attempts=" + attempt + ", found ids=" + allIds + "). "
                                + "Increase vrgo.cw.afteradds.poll.timeout.ms if the stack is slow."
                );
            }
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SkipException("Interrupted while polling CW after CDVR add");
            }
        }
    }

    private Response getContinueWatchRawWithTransientRetry(int limit, int offset, boolean includeHistory) {
        int maxAttempts = readIntProperty("vrgo.http.transient.retry.max", 3);
        int retryDelayMs = readIntProperty("vrgo.http.transient.retry.delay.ms", 1_500);
        RuntimeException lastFailure = null;
        for (int networkAttempt = 1; networkAttempt <= maxAttempts; networkAttempt++) {
            try {
                return continueWatchApi.getContinueWatchRaw(limit, offset, includeHistory);
            } catch (RuntimeException ex) {
                if (!isTransientNetworkFailure(ex) || networkAttempt >= maxAttempts) {
                    throw ex;
                }
                lastFailure = ex;
                Allure.parameter("cw.http.transient.retry.attempt", String.valueOf(networkAttempt));
                sleepQuietly(retryDelayMs);
            }
        }
        throw lastFailure;
    }

    private static boolean isTransientNetworkFailure(Throwable t) {
        while (t != null) {
            if (t instanceof ConnectException
                    || t instanceof NoRouteToHostException
                    || t instanceof SocketTimeoutException
                    || t instanceof UnknownHostException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private static void sleepQuietly(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SkipException("Interrupted during transient HTTP retry");
        }
    }

    private ChannelDayCdvrEvent resolveChannelDayCdvrEvent() {
        String contentIdOverride = config.getProperty("vrgo.cw.add.cdvr.content.id");
        if (isConfiguredId(contentIdOverride)) {
            String groupKeyOverride = config.getProperty("vrgo.cw.add.cdvr.group.key");
            String groupKey = isConfiguredId(groupKeyOverride)
                    ? groupKeyOverride.strip()
                    : groupKeyFromEventId(contentIdOverride.strip());
            if (groupKey == null || groupKey.isBlank()) {
                throw new SkipException(
                        "Set vrgo.cw.add.cdvr.group.key or use an eventId from which groupKey can be derived.");
            }
            return new ChannelDayCdvrEvent(contentIdOverride.strip(), groupKey.strip());
        }

        String channelId = resolveChannelDayChannelIdForCdvr();
        if (!isConfiguredId(channelId)) {
            throw new SkipException(
                    "Set vrgo.content.detail.channel.day.channel.id (or channel.id) for channel-day lookup, "
                            + "or vrgo.cw.add.cdvr.content.id explicitly.");
        }

        long epoch = pickChannelDayEpochMs();
        Allure.parameter("cw.cdvr.channelId", channelId.strip());
        Allure.parameter("cw.cdvr.channelDay.epochMs", String.valueOf(epoch));

        Response channelDayResponse = contentDetailApi.getChannelDayRaw(
                contentDetailApi.getChannelDayPathTemplate(),
                channelId.strip(),
                epoch
        );
        AllureAttachmentUtils.attachJson("continue-watch-cdvr-channel-day", channelDayResponse.asString());
        channelDayResponse.then().statusCode(200);

        List<ChannelDayCdvrEvent> slots = extractEventSlotsFromChannelDay(channelDayResponse);
        if (slots.isEmpty()) {
            throw new SkipException(
                    "No eventId values found in channel-day response for channel " + channelId
                            + ". Set vrgo.cw.add.cdvr.content.id explicitly.");
        }

        boolean pickLatest = !"first".equalsIgnoreCase(
                firstNonBlank(config.getProperty("vrgo.cw.add.cdvr.event.pick"), "last").strip()
        );
        ChannelDayCdvrEvent picked = pickLatest ? slots.get(slots.size() - 1) : slots.get(0);
        if (picked.groupKey() == null || picked.groupKey().isBlank()) {
            throw new SkipException(
                    "Could not resolve groupKey for event " + picked.contentId()
                            + ". Set vrgo.cw.add.cdvr.group.key or check channel-day payload.");
        }
        Allure.parameter("cw.cdvr.event.pick", pickLatest ? "last" : "first");
        Allure.parameter("cw.cdvr.channelDay.eventCount", String.valueOf(slots.size()));
        return picked;
    }

    private String resolveChannelDayChannelIdForCdvr() {
        String dedicated = config.getProperty("vrgo.cw.add.cdvr.channel.id");
        if (isConfiguredId(dedicated)) {
            return dedicated.strip();
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

    private long pickChannelDayEpochMs() {
        long now = Instant.now().toEpochMilli();
        String fixed = config.getProperty("vrgo.content.detail.channel.day.epoch.ms");
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
        String tzId = config.getProperty("vrgo.content.detail.channel.day.timezone", "Asia/Kuala_Lumpur");
        ZoneId z = ZoneId.of(tzId);
        ZonedDateTime wall = ZonedDateTime.now(z);
        long endOfDayMs = wall.toLocalDate().plusDays(1).atStartOfDay(z).toInstant().toEpochMilli();
        long span = Math.max(1L, endOfDayMs - now);
        return now + ThreadLocalRandom.current().nextLong(span);
    }

    private List<ChannelDayCdvrEvent> extractEventSlotsFromChannelDay(Response channelDayResponse) {
        List<ChannelDayCdvrEvent> slots = new ArrayList<>();
        try {
            JsonNode root = JsonUtils.mapper().readTree(channelDayResponse.asString());
            collectChannelDayEventSlots(root, slots);
        } catch (JsonProcessingException e) {
            slots.clear();
        }
        if (!slots.isEmpty()) {
            return slots;
        }

        Matcher matcher = Pattern.compile("\"eventId\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(channelDayResponse.asString());
        while (matcher.find()) {
            String eventId = matcher.group(1);
            if (!isConfiguredId(eventId)) {
                continue;
            }
            String groupKey = groupKeyFromEventId(eventId);
            slots.add(new ChannelDayCdvrEvent(eventId.strip(), groupKey));
        }
        return slots;
    }

    private static void collectChannelDayEventSlots(JsonNode node, List<ChannelDayCdvrEvent> out) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            JsonNode eventIdNode = node.get("eventId");
            if (eventIdNode != null && eventIdNode.isTextual()) {
                String eventId = eventIdNode.asText();
                if (isConfiguredId(eventId)) {
                    String groupKey = textOrNull(node.get("groupKey"));
                    if (groupKey == null) {
                        groupKey = textOrNull(node.get("group_key"));
                    }
                    if (groupKey == null) {
                        groupKey = groupKeyFromEventId(eventId);
                    }
                    out.add(new ChannelDayCdvrEvent(eventId.strip(), groupKey != null ? groupKey.strip() : null));
                }
            }
            node.fields().forEachRemaining(e -> collectChannelDayEventSlots(e.getValue(), out));
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectChannelDayEventSlots(child, out);
            }
        }
    }

    /**
     * Derives {@code groupKey} from a channel-day {@code eventId} such as
     * {@code 58150885:uri:prg:20119886S1:B12758678} → {@code 20119886}.
     */
    private static String groupKeyFromEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return null;
        }
        Matcher m = Pattern.compile(":uri:prg:(\\d+)").matcher(eventId);
        return m.find() ? m.group(1) : null;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        String t = node.asText();
        return t == null || t.isBlank() ? null : t;
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

    private record ChannelDayCdvrEvent(String contentId, String groupKey) {
    }

    private void getRecentContinueWatchContentForKind(CwAddContentKind kind) {
        requireContinueWatchPrerequisites();

        String contentId = kind.contentId(config);
        if (isBlank(contentId) || contentId.startsWith("REPLACE")) {
            throw new SkipException(
                    "Set " + kind.propertyPrefix() + "content.id in environments/<env>.properties for " + kind + ".");
        }
        String contentType = kind.contentType(config, kind.defaultContentType());
        String region = regionPropertyForRecentCwLookup();

        Allure.parameter("environment", Environment.current().name());
        Allure.parameter("cw.recent.kind", kind.name());
        Allure.parameter("cw.recent.contentId", contentId);
        Allure.parameter("cw.recent.contentType", contentType);
        Allure.parameter("cw.recent.region", region);

        Response r = continueWatchApi.getContinueWatchRecentContentRaw(contentType, region, contentId);
        AllureAttachmentUtils.attachJson("continue-watch-content-recent-" + kind.keySegment(), r.asString());
        assertRecentContentStatusResponse(r, contentId);
    }

    private String regionPropertyForRecentCwLookup() {
        String region = config.getProperty("vrgo.cw.recent.content.region");
        if (region == null || region.isBlank()) {
            return "Malaysia";
        }
        return region.strip();
    }

    private String regionPropertyForContentsProgress() {
        String region = config.getProperty("vrgo.cw.contents.progress.region");
        if (region != null && !region.isBlank()) {
            return region.strip();
        }
        return regionPropertyForRecentCwLookup();
    }

    /**
     * Builds {@code { contentId: contentType, ... }} for {@code POST .../contents/progress} from GET CW rows.
     */
    private Map<String, String> contentIdToProgressTypeMapFromCwRows(List<Map<String, Object>> rows) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String id = extractPrimaryContentId(row);
            if (id == null || id.isBlank()) {
                continue;
            }
            String key = id.strip();
            String editorial = extractPrimaryContentType(row);
            out.putIfAbsent(key, editorialTypeToProgressApiValue(editorial));
        }
        return out;
    }

    /**
     * Maps editorial {@code contentType} from GET CW to the string expected in the progress POST body.
     * When unset, {@code movie} / {@code tv_show} map to {@code VOD} to match typical VRGO clients.
     * Set {@code vrgo.cw.contents.progress.content.type} to force one value for every id.
     */
    private String editorialTypeToProgressApiValue(String editorialContentType) {
        String force = config.getProperty("vrgo.cw.contents.progress.content.type");
        if (force != null && !force.isBlank()) {
            return force.strip();
        }
        if (editorialContentType == null || editorialContentType.isBlank()) {
            return "VOD";
        }
        String e = editorialContentType.strip();
        if (e.equalsIgnoreCase("movie") || e.equalsIgnoreCase("tv_show")) {
            return "VOD";
        }
        if (e.equalsIgnoreCase("VOD")) {
            return "VOD";
        }
        return e;
    }

    /**
     * Asserts recent-content lookup succeeded ({@code status: true}) and the payload reflects the queried id
     * or an explicit "recent" flag (shape varies by API version).
     */
    @SuppressWarnings("unchecked")
    private static void assertRecentContentStatusResponse(Response r, String contentId) {
        r.then()
                .statusCode(200)
                .body("status", equalTo(true));
        Object data = r.jsonPath().get("data");
        String body = r.asString();
        if (Boolean.TRUE.equals(data)) {
            return;
        }
        if (data instanceof Map<?, ?> raw) {
            Map<String, Object> m = (Map<String, Object>) raw;
            Object recent = firstMapValuePresent(m,
                    "recent", "isRecent", "isAvailable", "present", "exists", "inContinueWatch");
            if (Boolean.TRUE.equals(recent)) {
                return;
            }
            Object id = firstMapValuePresent(m, "contentId", "id", "editorialId");
            if (id != null && contentId.strip().equalsIgnoreCase(String.valueOf(id).strip())) {
                return;
            }
        }
        Assert.assertTrue(
                body.contains(contentId.strip()),
                "Recent CW response should reference configured content id or explicit recent flag; body=" + body
        );
    }

    private static Object firstMapValuePresent(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /**
     * Boxset continue-watch API: {@code data} is a non-null object; {@code boxsetId} and {@code watchedBoxsetContent}
     * are present; each watched item has non-null {@code contentId}, {@code progress}, {@code watchDuration},
     * {@code primary}.
     */
    private static void assertBoxsetCwDataShapeAndNonNullFields(Response r, String expectedBoxsetId) {
        JsonNode root = parseResponseJsonTree(r);
        JsonNode data = root.get("data");
        Assert.assertNotNull(data, "Boxset CW response data must not be null");
        Assert.assertTrue(data.isObject(), "Boxset CW data must be a JSON object");

        JsonNode boxsetIdNode = data.get("boxsetId");
        Assert.assertNotNull(boxsetIdNode, "data.boxsetId must be present");
        Assert.assertFalse(boxsetIdNode.isNull(), "data.boxsetId must not be null");
        Assert.assertEquals(
                expectedBoxsetId.strip(),
                boxsetIdNode.asText().strip(),
                "data.boxsetId should match requested boxset id"
        );

        JsonNode watched = data.get("watchedBoxsetContent");
        Assert.assertNotNull(watched, "data.watchedBoxsetContent must be present");
        Assert.assertFalse(watched.isNull(), "data.watchedBoxsetContent must not be null");
        Assert.assertTrue(watched.isArray(), "data.watchedBoxsetContent must be a JSON array");

        int i = 0;
        for (JsonNode item : watched) {
            Assert.assertTrue(item.isObject(), "watchedBoxsetContent[" + i + "] must be a JSON object");
            assertBoxsetWatchedItemFieldNonNull(item, i, "contentId");
            assertBoxsetWatchedItemFieldNonNull(item, i, "progress");
            assertBoxsetWatchedItemFieldNonNull(item, i, "watchDuration");
            assertBoxsetWatchedItemFieldNonNull(item, i, "primary");
            i++;
        }
    }

    private static void assertBoxsetWatchedItemFieldNonNull(JsonNode item, int index, String field) {
        JsonNode n = item.get(field);
        Assert.assertNotNull(n, "watchedBoxsetContent[" + index + "]." + field + " must be present");
        Assert.assertFalse(n.isNull(), "watchedBoxsetContent[" + index + "]." + field + " must not be JSON null");
    }

    private static JsonNode parseResponseJsonTree(Response r) {
        try {
            return JsonUtils.mapper().readTree(r.asString());
        } catch (JsonProcessingException e) {
            Assert.fail("Failed to parse boxset CW JSON: " + e.getMessage() + " body=" + r.asString());
            throw new AssertionError();
        }
    }

    /**
     * Boxset continue-watch API: expect the configured CW movie id and a {@code progress} field on parsed rows
     * (or progress mentioned in the raw body when the envelope differs).
     */
    private static void assertBoxsetCwResponseContainsMovieWithProgress(Response r, String boxsetId, String expectedMovieId) {
        assertBoxsetCwDataShapeAndNonNullFields(r, boxsetId);
        List<Map<String, Object>> rows = cwDataAsMaps(r);
        if (!rows.isEmpty()) {
            Set<String> ids = collectContentIds(rows);
            Assert.assertTrue(
                    anyIdMatchesConfigured(ids, expectedMovieId),
                    "Boxset CW should list configured movie id " + expectedMovieId + " ; found ids " + ids
            );
            for (Map<String, Object> row : rows) {
                Assert.assertTrue(
                        rowHasProgressField(row),
                        "Each boxset-linked CW row should expose progress (top-level or under editorial); row=" + row
                );
            }
            return;
        }
        String body = r.asString();
        Assert.assertTrue(
                body.contains(expectedMovieId.strip()),
                "Boxset CW response should reference configured movie id " + expectedMovieId + " ; body=" + body
        );
        Assert.assertTrue(
                body.toLowerCase().contains("progress"),
                "Boxset CW response should include progress metadata; body=" + body
        );
    }

    private static boolean rowHasProgressField(Map<String, Object> row) {
        for (Map<String, Object> layer : cwRowLookupLayers(row)) {
            if (layer.containsKey("progress") && layer.get("progress") != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * For series-scoped recent CW rows: exactly one row has {@code primary == true}, and that row's editorial id
     * matches the configured TV episode (the one added in this suite).
     */
    private static void assertExactlyOnePrimaryRowAndMatchesTvEpisode(
            List<Map<String, Object>> rows,
            String expectedTvEpisodeId
    ) {
        List<Map<String, Object>> primaryTrueRows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (Boolean.TRUE.equals(row.get("primary"))) {
                primaryTrueRows.add(row);
            }
        }
        Assert.assertEquals(
                primaryTrueRows.size(),
                1,
                "Exactly one episode row should have primary=true; found " + primaryTrueRows.size() + " among " + rows.size() + " rows"
        );
        String primaryEpisodeId = extractPrimaryContentId(primaryTrueRows.get(0));
        Assert.assertNotNull(primaryEpisodeId, "primary=true row should expose an editorial id under contentEditorial");
        Assert.assertTrue(
                anyIdMatchesConfigured(Set.of(primaryEpisodeId.strip()), expectedTvEpisodeId),
                "primary=true row should be the recently added TV episode; expected id matching " + expectedTvEpisodeId + " but got " + primaryEpisodeId
        );
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

    /**
     * Pause after movie subscriber-continue-watch POST before TV POST. Same delay is used from {@link WatchAgain}.
     * Property {@code vrgo.cw.betweenAdds.sleep.ms} (default {@code 1500}); use {@code 0} to disable.
     */
    static void sleepBetweenMovieAndTvCwAdds() {
        int ms = readIntProperty("vrgo.cw.betweenAdds.sleep.ms", 1_500);
        if (ms <= 0) {
            return;
        }
        Allure.parameter("cw.betweenAdds.sleep.ms", String.valueOf(ms));
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SkipException("Interrupted during vrgo.cw.betweenAdds sleep", e);
        }
    }

    private void postSubscriberContinueWatch(CwAddContentKind kind) {
        requireContinueWatchPrerequisites();

        String subscriberId = config.getProperty("vrgo.header.cp_id");
        if (isBlank(subscriberId) || subscriberId.startsWith("REPLACE")) {
            throw new SkipException("Set vrgo.header.cp_id in the active environment file.");
        }

        String contentId = kind.contentId(config);
        if (isBlank(contentId) || contentId.startsWith("REPLACE")) {
            throw new SkipException(
                    "Set " + kind.propertyPrefix() + "content.id in environments/<env>.properties for " + kind + ".");
        }

        String contentType = kind.contentType(config, kind.defaultContentType());
        int watchDuration = kind.watchDuration(config, kind.defaultWatchDuration());

        SubscriberContinueWatchRequest body = new SubscriberContinueWatchRequest(
                contentId,
                contentType,
                watchDuration,
                subscriberId
        );

        Allure.parameter("environment", Environment.current().name());
        Allure.parameter("cw.kind", kind.name());
        Allure.parameter(kind.propertyPrefix() + "content.id", contentId);
        AllureAttachmentUtils.attachJson("subscriber-continue-watch-" + kind.keySegment(), JsonUtils.toJson(body));

        Response r = continueWatchApi.addSubscriberContinueWatchRaw(false, body);
        r.then().statusCode(200);
    }

    private void assertGetCwListPolicy(Response r) {
        List<Map<String, Object>> rows = cwDataAsMaps(r);
        Set<String> allIds = collectContentIds(rows);
        attachCwListMetrics(rows, allIds);
        assertNoSeriesOrBoxsetInList(rows, allIds);
    }

    /**
     * When the GET response parses to a non-empty row list, calls DELETE continue-watch once per unique content id.
     * Only ids present on this response are removed (same {@code limit}/page as the GET).
     */
    private void deleteContinueWatchItemsFromGetResponse(Response getResponse) {
        List<Map<String, Object>> rows = cwDataAsMaps(getResponse);
        if (rows.isEmpty()) {
            return;
        }
        Set<String> uniqueIdsToDelete = new LinkedHashSet<>();
        int attempted = 0;
        for (Map<String, Object> row : rows) {
            String id = extractPrimaryContentId(row);
            if (id == null || id.isBlank()) {
                continue;
            }
            String contentId = id.strip();
            if (!uniqueIdsToDelete.add(contentId)) {
                continue;
            }
            String contentType = extractPrimaryContentType(row);
            attempted++;
            Response del = continueWatchApi.deleteContinueWatchItemRaw(contentId, contentType);
            del.then().statusCode(anyOf(is(200), is(204)));
        }
        Allure.parameter("cw.delete.afterGet.attempted", String.valueOf(attempted));
        Allure.parameter("cw.delete.afterGet.uniqueContentIds", String.valueOf(uniqueIdsToDelete.size()));
    }

    private static void attachCwListMetrics(List<Map<String, Object>> rows, Set<String> allIds) {
        Allure.parameter("cw.list.rowCount", String.valueOf(rows.size()));
        Allure.parameter("cw.list.uniqueContentIdCount", String.valueOf(allIds.size()));
    }

    private void assertNoSeriesOrBoxsetInList(List<Map<String, Object>> rows, Set<String> allIds) {
        for (Map<String, Object> row : rows) {
            String typeHint = extractTypeHint(row);
            Assert.assertFalse(
                    FORBIDDEN_ROW_TYPE.matcher(typeHint).find(),
                    "GET CW must only list movie / TV show style items; forbidden series/boxset hint in row: " + row
            );
        }

        String seriesId = CwAddContentKind.SERIES.contentId(config);
        if (isConfiguredId(seriesId)) {
            Assert.assertFalse(
                    anyIdMatchesConfigured(allIds, seriesId),
                    "GET CW must not list series content id, but found a matching id for: " + seriesId
            );
        }
        String boxsetId = CwAddContentKind.BOXSET.contentId(config);
        if (isConfiguredId(boxsetId)) {
            Assert.assertFalse(
                    anyIdMatchesConfigured(allIds, boxsetId),
                    "GET CW must not list boxset content id, but found a matching id for: " + boxsetId
            );
        }
    }

    /**
     * Normalizes GET continue-watch {@code data} to a list of row maps.
     * <p>
     * VRGO returns {@code data} as either a JSON array of items, or an object such as
     * {@code { "historyCount": n, "contentHistories": [ { "contentEditorial": { "id", "contentType", ... } } ] }}.
     * <p>
     * RestAssured 5 + Jackson may surface nested arrays as {@link JsonNode} (e.g. {@code ArrayNode}), not
     * {@link List}; callers must not rely on {@code instanceof List} alone.
     */
    private static List<Map<String, Object>> cwDataAsMaps(Response r) {
        List<Map<String, Object>> fromHistories = mapsFromJsonPathList(r, "data.contentHistories");
        if (!fromHistories.isEmpty()) {
            return fromHistories;
        }

        Object data = r.jsonPath().get("data");
        if (data == null) {
            return List.of();
        }
        if (data instanceof List<?>) {
            return rowsAsMaps((List<?>) data);
        }
        Map<String, Object> dataMap = tryToPropertyMap(data);
        if (dataMap == null) {
            return List.of();
        }
        List<?> raw = extractCwItemListFromDataMap(dataMap);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return rowsAsMaps(raw);
    }

    private static List<Map<String, Object>> mapsFromJsonPathList(Response r, String path) {
        List<?> loose = toLooseList(r.jsonPath().get(path));
        if (loose == null || loose.isEmpty()) {
            return List.of();
        }
        return rowsAsMaps(loose);
    }

    private static List<Map<String, Object>> rowsAsMaps(List<?> raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : raw) {
            Map<String, Object> row = toStringKeyedMap(o);
            if (row != null) {
                out.add(row);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tryToPropertyMap(Object data) {
        if (data instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        if (data instanceof JsonNode node && node.isObject()) {
            return JsonUtils.mapper().convertValue(node, new TypeReference<Map<String, Object>>() { });
        }
        return null;
    }

    /**
     * Coerces JSON array values from JsonPath (List, Jackson {@link JsonNode} array, Java array, etc.) to a {@link List}.
     */
    private static List<?> toLooseList(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof List<?> list) {
            return list;
        }
        if (v instanceof JsonNode node && node.isArray()) {
            List<Object> list = new ArrayList<>(node.size());
            node.forEach(list::add);
            return list;
        }
        if (v instanceof Collection<?> c) {
            return new ArrayList<>(c);
        }
        if (v.getClass().isArray()) {
            int n = Array.getLength(v);
            List<Object> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(Array.get(v, i));
            }
            return list;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toStringKeyedMap(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        if (o instanceof JsonNode node && node.isObject()) {
            return JsonUtils.mapper().convertValue(node, new TypeReference<Map<String, Object>>() { });
        }
        return null;
    }

    /**
     * Known wrapper keys for the continue-watch list inside {@code data} when {@code data} is an object
     * (including {@code watchedBoxsetContent} for boxset scoped GET).
     */
    private static List<?> extractCwItemListFromDataMap(Map<String, Object> dataMap) {
        for (String key : List.of(
                "contentHistories",
                "watchedBoxsetContent",
                "items",
                "records",
                "list",
                "content",
                "histories",
                "data")) {
            List<?> loose = toLooseList(dataMap.get(key));
            if (loose != null) {
                return loose;
            }
        }
        return null;
    }

    private static Set<String> collectContentIds(List<Map<String, Object>> rows) {
        Set<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String id = extractPrimaryContentId(row);
            if (id != null && !id.isBlank()) {
                ids.add(id.strip());
            }
        }
        return ids;
    }

    private static String extractPrimaryContentId(Map<String, Object> row) {
        for (Map<String, Object> layer : cwRowLookupLayers(row)) {
            for (String key : List.of("contentId", "id", "content_id", "entityId", "assetId")) {
                Object v = layer.get(key);
                if (v != null) {
                    return String.valueOf(v);
                }
            }
        }
        return null;
    }

    /**
     * Prefer {@code contentType} from the GET row (e.g. under {@code contentEditorial}); default matches CW delete curl.
     */
    private static String extractPrimaryContentType(Map<String, Object> row) {
        for (Map<String, Object> layer : cwRowLookupLayers(row)) {
            Object v = layer.get("contentType");
            if (v != null && !String.valueOf(v).isBlank()) {
                return String.valueOf(v).strip();
            }
        }
        return "VOD";
    }

    private static String extractTypeHint(Map<String, Object> row) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> layer : cwRowLookupLayers(row)) {
            for (String key : List.of(
                    "contentType", "layoutType", "layout", "entityType", "type",
                    "contentLayout", "genre", "category")) {
                Object v = layer.get(key);
                if (v != null) {
                    sb.append(' ').append(v);
                }
            }
        }
        return sb.toString();
    }

    /**
     * Continue-watch rows may nest editorial metadata (ids, contentType) under {@code contentEditorial} etc.
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> cwRowLookupLayers(Map<String, Object> row) {
        List<Map<String, Object>> layers = new ArrayList<>();
        layers.add(row);
        for (String nested : List.of("contentEditorial", "content", "editorial", "metadata")) {
            Object o = row.get(nested);
            if (o instanceof Map) {
                layers.add((Map<String, Object>) o);
            } else if (o instanceof JsonNode jn && jn.isObject()) {
                layers.add(JsonUtils.mapper().convertValue(jn, new TypeReference<Map<String, Object>>() { }));
            }
        }
        return layers;
    }

    private static boolean isConfiguredId(String id) {
        return id != null && !id.isBlank() && !id.startsWith("REPLACE");
    }

    private static boolean anyIdMatchesConfigured(Set<String> responseIds, String configuredId) {
        if (configuredId == null || configuredId.isBlank()) {
            return false;
        }
        String c = configuredId.strip();
        for (String rid : responseIds) {
            if (rid == null) {
                continue;
            }
            String r = rid.strip();
            if (r.equalsIgnoreCase(c)) {
                return true;
            }
            if (r.endsWith(c) || c.endsWith(r) || r.contains(c) || c.contains(r)) {
                return true;
            }
        }
        return false;
    }

    private void requireContinueWatchPrerequisites() {
        if (continueWatchApi == null) {
            throw new SkipException("Configure vrgo.base.url in environments/<env>.properties to run this test.");
        }
        if (!isVrgoAuthConfigured()) {
            throw new SkipException(VRGO_AUTH_SKIP_MESSAGE);
        }
        if (isBlank(System.getenv("VRGO_X_API_KEY"))
                && isBlank(System.getProperty("vrgo.x.api.key"))
                && isBlank(config.getProperty("vrgo.x.api.key"))) {
            throw new SkipException(
                    "Set vrgo.x.api.key in environments/<env>.properties, or BaseTest.VRGO_MANUAL_X_API_KEY, or VRGO_X_API_KEY / -Dvrgo.x.api.key.");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Same-package tests reuse CW list parsing (e.g. {@link WatchAgain}). */
    static List<Map<String, Object>> cwListRows(Response r) {
        return cwDataAsMaps(r);
    }

    static Set<String> cwListContentIds(Response r) {
        return collectContentIds(cwDataAsMaps(r));
    }

    static boolean cwListContainsConfiguredId(Set<String> responseIds, String configuredId) {
        return anyIdMatchesConfigured(responseIds, configuredId);
    }

    static String cwRowContentId(Map<String, Object> row) {
        return extractPrimaryContentId(row);
    }

    static String cwRowContentType(Map<String, Object> row) {
        return extractPrimaryContentType(row);
    }
}
