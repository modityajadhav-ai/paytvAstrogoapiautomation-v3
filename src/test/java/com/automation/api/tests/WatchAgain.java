package com.automation.api.tests;

import com.automation.api.base.BaseTest;
import com.automation.api.config.Environment;
import com.automation.api.constants.CwAddContentKind;
import com.automation.api.model.vrgo.SubscriberContinueWatchRequest;
import com.automation.api.util.AllureAttachmentUtils;
import com.automation.api.util.JsonUtils;
import io.qameta.allure.Allure;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * Watch-again flow: add movie and **TV episode** ({@code vrgo.cw.add.tvshow.content.id}) to subscriber continue-watch
 * with {@code hasCompletedPlayBack=true}, confirm they disappear from the in-progress continue-watch list, then
 * confirm watch-again lists the movie and the **series** editorial id for TV (never the episode id — see
 * {@code vrgo.watch.again.assert.series.id} / {@code vrgo.watch.again.series.id} / {@code vrgo.cw.recent.series.id}).
 * Movie rows on watch-again may only match via parsed ids or raw body / {@code mov-} UUID tail (see {@link #watchAgainMoviePresent}).
 * <p>
 * Uses the same {@code vrgo.cw.add.movie.*} / {@code vrgo.cw.add.tvshow.*} ids as {@link ContinueWatch}. Credentials
 * match {@link BaseTest} / environment properties (no tokens in source).
 * <p>
 * Watch-again GET retries are controlled by {@code vrgo.watch.again.poll.enabled} (default {@code true} when unset).
 * {@code false} performs a single GET and can fail while subscriber-event is still catching up after completed CW POSTs
 * (the same URL in Postman run moments later may already include the series). Movie→TV subscriber POSTs use
 * {@code vrgo.cw.betweenAdds.sleep.ms} (see {@link ContinueWatch}).
 */
@Feature("Watch again")
public class WatchAgain extends BaseTest {

    @Test(description = "Clear first CW page so completed-playback assertions are not affected by leftover rows")
    @Story("GET/DELETE continue-watch (prepare)")
    public void watchAgain_prepare_clearContinueWatchFirstPage() {
        requireWatchAgainPrerequisites();
        clearContinueWatchFirstPage();
    }

    @Test(
            dependsOnMethods = "watchAgain_prepare_clearContinueWatchFirstPage",
            description = "POST movie and TV with hasCompletedPlayBack=true"
    )
    @Story("POST /subscriber-activity-producer/v3/subscriber-continue-watch (completed)")
    public void watchAgain_postMovieAndTvWithCompletedPlayback() {
        requireWatchAgainPrerequisites();
        postSubscriberContinueWatchCompleted(CwAddContentKind.MOVIE);
        ContinueWatch.sleepBetweenMovieAndTvCwAdds();
        postSubscriberContinueWatchCompleted(CwAddContentKind.TV_SHOW);
    }

    @Test(
            dependsOnMethods = "watchAgain_postMovieAndTvWithCompletedPlayback",
            description = "GET continue-watch: configured movie and TV editorial ids must not appear after completed playback"
    )
    @Story("GET /subscriber-event-service/v3/continue-watch/continue")
    public void watchAgain_getContinueWatchListExcludesCompletedItems() {
        requireWatchAgainPrerequisites();

        String movieId = CwAddContentKind.MOVIE.contentId(config);
        String tvId = CwAddContentKind.TV_SHOW.contentId(config);
        if (isBlank(movieId) || movieId.startsWith("REPLACE") || isBlank(tvId) || tvId.startsWith("REPLACE")) {
            throw new SkipException("Configure vrgo.cw.add.movie.content.id and vrgo.cw.add.tvshow.content.id.");
        }

        Allure.parameter("cw.expectAbsent.movie", movieId);
        Allure.parameter("cw.expectAbsent.tvshow", tvId);

        Response r = pollContinueWatchUntilNeitherConfiguredId(movieId, tvId);
        AllureAttachmentUtils.attachJson("continue-watch-after-completed-posts", r.asString());
        r.then().statusCode(200).body("status", equalTo(true));

        Set<String> ids = ContinueWatch.cwListContentIds(r);
        Assert.assertFalse(
                ContinueWatch.cwListContainsConfiguredId(ids, movieId),
                "Continue-watch list must not include configured movie id after completed playback; ids=" + ids
        );
        Assert.assertFalse(
                ContinueWatch.cwListContainsConfiguredId(ids, tvId),
                "Continue-watch list must not include configured TV episode id after completed playback; ids=" + ids
        );
    }

    @Test(
            dependsOnMethods = "watchAgain_getContinueWatchListExcludesCompletedItems",
            description = "GET watch-again: movie id and TV series id (not episode id) must appear"
    )
    @Story("GET /subscriber-event-service/v3/watch-again")
    public void watchAgain_getWatchAgainListIncludesMovieAndTv() {
        requireWatchAgainPrerequisites();

        String movieId = CwAddContentKind.MOVIE.contentId(config);
        String tvId = CwAddContentKind.TV_SHOW.contentId(config);
        if (isBlank(movieId) || movieId.startsWith("REPLACE") || isBlank(tvId) || tvId.startsWith("REPLACE")) {
            throw new SkipException("Configure vrgo.cw.add.movie.content.id and vrgo.cw.add.tvshow.content.id.");
        }

        String seriesId = seriesIdForWatchAgainGetAssertions();
        if (isBlank(seriesId) || seriesId.startsWith("REPLACE")) {
            throw new SkipException(
                    "Set series id for watch-again TV row: vrgo.watch.again.assert.series.id, or vrgo.watch.again.series.id, "
                            + "or vrgo.cw.recent.series.id (episode id vrgo.cw.add.tvshow.content.id is only for POST/CW).");
        }

        int limit = readIntProperty("vrgo.watch.again.limit", 20);
        int offset = readIntProperty("vrgo.watch.again.offset", 0);
        String contentType = firstNonBlank(config.getProperty("vrgo.watch.again.content.type"), "VOD");
        boolean ent = readBooleanProperty("vrgo.watch.again.is.entitlement.enabled", false);

        Allure.parameter("watchAgain.limit", String.valueOf(limit));
        Allure.parameter("watchAgain.offset", String.valueOf(offset));
        Allure.parameter("watchAgain.contentType", contentType);
        Allure.parameter("watchAgain.isEntitlementEnabled", String.valueOf(ent));
        Allure.parameter("watchAgain.expectedSeriesId", seriesId);
        Allure.parameter("watchAgain.cwPost.tvEpisodeId", tvId);

        boolean pollEnabled = readWatchAgainPollEnabled();
        Allure.parameter("watchAgain.poll.enabled", String.valueOf(pollEnabled));

        Response r;
        if (pollEnabled) {
            r = pollWatchAgainUntilMovieAndSeriesPresent(limit, offset, contentType, ent, movieId, seriesId);
        } else {
            r = continueWatchApi.getWatchAgainRaw(limit, offset, contentType, ent);
            Allure.parameter("watchAgain.poll.attempts", "1");
        }
        AllureAttachmentUtils.attachJson("watch-again-response", r.asString());
        r.then()
                .statusCode(200)
                .body("status", equalTo(true));

        // Watch-again lists TV by series editorial id, not the episode id used in subscriber-continue-watch POST.
        assertWatchAgainSeeAllListContainsMovieAndTvSeriesRow(r, movieId, seriesId);
    }

    private void postSubscriberContinueWatchCompleted(CwAddContentKind kind) {
        requireWatchAgainPrerequisites();

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
        Allure.parameter("watchAgain.cw.kind", kind.name());
        Allure.parameter(kind.propertyPrefix() + "content.id", contentId);
        Allure.parameter("watchAgain.hasCompletedPlayBack", "true");
        AllureAttachmentUtils.attachJson("subscriber-continue-watch-completed-" + kind.keySegment(), JsonUtils.toJson(body));

        Response r = continueWatchApi.addSubscriberContinueWatchRaw(true, body);
        r.then().statusCode(200);
    }

    private void clearContinueWatchFirstPage() {
        Response r = continueWatchApi.getContinueWatchRaw(20, 0, false);
        r.then().statusCode(200).body("status", equalTo(true));

        Set<String> uniqueIds = new LinkedHashSet<>();
        for (Map<String, Object> row : ContinueWatch.cwListRows(r)) {
            String id = ContinueWatch.cwRowContentId(row);
            if (id == null || id.isBlank() || !uniqueIds.add(id.strip())) {
                continue;
            }
            String contentType = ContinueWatch.cwRowContentType(row);
            Response del = continueWatchApi.deleteContinueWatchItemRaw(id.strip(), contentType);
            del.then().statusCode(anyOf(is(200), is(204)));
        }
    }

    private Response pollContinueWatchUntilNeitherConfiguredId(String movieId, String tvId) {
        int timeoutMs = readIntProperty("vrgo.cw.afteradds.poll.timeout.ms", 30_000);
        int intervalMs = readIntProperty("vrgo.cw.afteradds.poll.interval.ms", 750);
        long deadline = System.currentTimeMillis() + timeoutMs;
        Response last = null;
        int attempt = 0;
        while (true) {
            attempt++;
            last = continueWatchApi.getContinueWatchRaw(20, 0, false);
            last.then().statusCode(200).body("status", equalTo(true));
            Set<String> ids = ContinueWatch.cwListContentIds(last);
            boolean movieGone = !ContinueWatch.cwListContainsConfiguredId(ids, movieId);
            boolean tvGone = !ContinueWatch.cwListContainsConfiguredId(ids, tvId);
            if (movieGone && tvGone) {
                Allure.parameter("cw.pollUntilAbsent.attempts", String.valueOf(attempt));
                return last;
            }
            if (System.currentTimeMillis() >= deadline) {
                Assert.fail(
                        "Timed out waiting for CW list to exclude movie and TV after completed playback ("
                                + timeoutMs
                                + " ms). Last ids="
                                + ids
                                + " body="
                                + last.asString()
                );
            }
            sleepQuiet(intervalMs);
        }
    }

    /**
     * Series editorial id used only for **watch-again GET** assertions (TV row is keyed by series, not the episode
     * id used in {@code vrgo.cw.add.tvshow.content.id} for POST). Resolution order:
     * {@code vrgo.watch.again.assert.series.id} → {@code vrgo.watch.again.series.id} → {@code vrgo.cw.recent.series.id}.
     */
    private String seriesIdForWatchAgainGetAssertions() {
        String a = config.getProperty("vrgo.watch.again.assert.series.id");
        if (a != null && !a.isBlank()) {
            return a.strip();
        }
        String b = config.getProperty("vrgo.watch.again.series.id");
        if (b != null && !b.isBlank()) {
            return b.strip();
        }
        String c = config.getProperty("vrgo.cw.recent.series.id");
        return c == null ? "" : c.strip();
    }

    private Response pollWatchAgainUntilMovieAndSeriesPresent(
            int limit,
            int offset,
            String contentType,
            boolean isEntitlementEnabled,
            String movieId,
            String seriesId
    ) {
        int timeoutMs = readIntProperty("vrgo.watch.again.poll.timeout.ms", 30_000);
        int intervalMs = readIntProperty("vrgo.watch.again.poll.interval.ms", 750);
        long deadline = System.currentTimeMillis() + timeoutMs;
        Response last = null;
        int attempt = 0;
        while (true) {
            attempt++;
            last = continueWatchApi.getWatchAgainRaw(limit, offset, contentType, isEntitlementEnabled);
            last.then().statusCode(200).body("status", equalTo(true));
            if (watchAgainResponseSignalsMovieAndTvSeriesRow(last, movieId, seriesId)) {
                Allure.parameter("watchAgain.poll.attempts", String.valueOf(attempt));
                return last;
            }
            if (System.currentTimeMillis() >= deadline) {
                Assert.fail(
                        "Timed out waiting for watch-again to include movie and TV series row ids ("
                                + timeoutMs
                                + " ms). body="
                                + last.asString()
                );
            }
            sleepQuiet(intervalMs);
        }
    }

    /**
     * After GET watch-again (see-all list), assert movie editorial id and TV **series** id appear (VRGO lists the
     * series for completed TV watch-again, not the episode editorial id). The movie may not appear in flattened
     * {@code parsedContentIds} or may use bare UUID while properties use {@code mov-<uuid>} — see {@link #watchAgainMoviePresent}.
     */
    private static void assertWatchAgainSeeAllListContainsMovieAndTvSeriesRow(Response r, String movieId, String seriesId) {
        String m = movieId.strip();
        String ser = seriesId.strip();
        Set<String> ids = ContinueWatch.cwListContentIds(r);
        String body = r.asString();
        Assert.assertTrue(
                watchAgainMoviePresent(ids, body, m),
                "Watch-again must include configured movie ("
                        + m
                        + " or UUID without mov- prefix) in parsed list or JSON body. parsedContentIds="
                        + ids
        );
        Assert.assertTrue(
                ContinueWatch.cwListContainsConfiguredId(ids, ser) || bodyContainsId(body, ser),
                "Watch-again must include TV series editorial id "
                        + ser
                        + " (parsed row id or anywhere in body). parsedContentIds="
                        + ids
        );
    }

    /**
     * True when watch-again payload includes the movie (see {@link #watchAgainMoviePresent}) and the TV series row id.
     */
    private static boolean watchAgainResponseSignalsMovieAndTvSeriesRow(Response r, String movieId, String seriesId) {
        String m = movieId.strip();
        String ser = seriesId.strip();
        Set<String> ids = ContinueWatch.cwListContentIds(r);
        String body = r.asString();
        boolean movieOk = watchAgainMoviePresent(ids, body, m);
        boolean seriesOk = ContinueWatch.cwListContainsConfiguredId(ids, ser) || bodyContainsId(body, ser);
        return movieOk && seriesOk;
    }

    /**
     * Movie may be missing from flattened {@code cwListContentIds} (different envelope than continue-watch), only
     * present in the raw body, or listed as bare UUID while properties use {@code mov-<uuid>}.
     */
    private static boolean watchAgainMoviePresent(Set<String> ids, String body, String movieId) {
        String m = movieId.strip();
        if (ContinueWatch.cwListContainsConfiguredId(ids, m) || bodyContainsId(body, m)) {
            return true;
        }
        String uuidTail = stripMovEditorialPrefix(m);
        if (!uuidTail.equals(m)) {
            return ContinueWatch.cwListContainsConfiguredId(ids, uuidTail) || bodyContainsId(body, uuidTail);
        }
        return false;
    }

    /** If editorial id uses {@code mov-} prefix, return the UUID tail for alternate matching on watch-again. */
    private static String stripMovEditorialPrefix(String editorialId) {
        if (editorialId == null || editorialId.isBlank()) {
            return "";
        }
        String s = editorialId.strip();
        if (s.regionMatches(true, 0, "mov-", 0, 4)) {
            return s.length() > 4 ? s.substring(4).strip() : s;
        }
        return s;
    }

    private static boolean bodyContainsId(String body, String id) {
        return id != null && !id.isEmpty() && body.contains(id);
    }

    private static void sleepQuiet(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while polling VRGO subscriber-event", e);
        }
    }

    private void requireWatchAgainPrerequisites() {
        if (continueWatchApi == null) {
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

    private int readIntProperty(String key, int defaultValue) {
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

    private boolean readBooleanProperty(String key, boolean defaultValue) {
        String s = config.getProperty(key);
        if (s == null || s.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(s.strip());
    }

    /**
     * When property is unset, polling stays on (backward compatible). Explicit {@code false} disables the retry loop.
     */
    private boolean readWatchAgainPollEnabled() {
        String s = config.getProperty("vrgo.watch.again.poll.enabled");
        if (s == null || s.isBlank()) {
            return true;
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
