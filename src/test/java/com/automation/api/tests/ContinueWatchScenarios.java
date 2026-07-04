package com.automation.api.tests;

import com.automation.api.base.BaseTest;
import com.automation.api.config.Environment;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * Series-episode continue-watch scenario: adds EP1 <em>without</em> the {@code hasCompletedPlayBack}
 * query parameter and validates the 97%-completion threshold behaviour.
 *
 * <h3>Scenario flow</h3>
 * <ol>
 *   <li><strong>Partial EP1 POST</strong> – {@code watchDuration} &lt; 97 % of total duration.
 *       EP1 must appear in the GET CW list with non-zero progress.</li>
 *   <li><strong>Completed EP1 POST</strong> – {@code watchDuration} &ge; 97 % of total duration.
 *       EP1 must be absent from the GET CW list; configured EP2 (same series) must appear with
 *       progress&nbsp;=&nbsp;0.</li>
 *   <li><strong>Contents-progress POST</strong> – payload built from GET CW rows must NOT contain
 *       EP1 (EP1 must not be displayed with 0 progress after completion).</li>
 *   <li><strong>Recent-content GET for EP1</strong> – EP1 must not appear in the response with
 *       watchDuration / progress&nbsp;=&nbsp;0 after completion.</li>
 * </ol>
 *
 * <h3>Required properties (active environment file)</h3>
 * <ul>
 *   <li>{@code vrgo.cw.series.ep1.content.id} – EP1 editorial id</li>
 *   <li>{@code vrgo.cw.series.ep1.content.type} – EP1 content type (default {@code VOD})</li>
 *   <li>{@code vrgo.cw.series.ep1.total.duration} – total playback duration in seconds</li>
 *   <li>{@code vrgo.cw.series.ep2.content.id} – EP2 editorial id</li>
 *   <li>{@code vrgo.cw.series.ep2.content.type} – EP2 content type (default {@code VOD})</li>
 * </ul>
 *
 * <h3>Optional properties</h3>
 * <ul>
 *   <li>{@code vrgo.cw.series.ep1.partial.watch.duration} – watchDuration for the partial POST;
 *       must be &lt; 97 % of total. Defaults to 50 % of total when blank.</li>
 *   <li>{@code vrgo.cw.series.ep1.complete.watch.duration} – watchDuration for the completed POST;
 *       must be &ge; 97 % of total. Defaults to exactly 97 % of total when blank.</li>
 *   <li>{@code vrgo.cw.series.id} – series editorial id; used to verify promoted EP2 belongs to the same series.</li>
 * </ul>
 *
 * <p>Polling after each POST reuses {@code vrgo.cw.afteradds.poll.timeout.ms} /
 * {@code vrgo.cw.afteradds.poll.interval.ms} from the active environment file.</p>
 *
 * <h3>Boxset-movies scenario (priorities 100+)</h3>
 * <ol>
 *   <li><strong>Partial movie1 POST</strong> – {@code hasCompletedPlayBack=false}. Movie1 must appear in GET CW;
 *       movie2 must be absent.</li>
 *   <li><strong>Completed movie1 POST</strong> – {@code hasCompletedPlayBack=true}. Movie1 must move to watch-again;
 *       movie2 must appear in GET CW with progress&nbsp;=&nbsp;0.</li>
 * </ol>
 *
 * <h3>Boxset-movies required properties</h3>
 * <ul>
 *   <li>{@code vrgo.cw.boxset.movies.boxset.id}</li>
 *   <li>{@code vrgo.cw.boxset.movies.movie1.content.id}</li>
 *   <li>{@code vrgo.cw.boxset.movies.movie2.content.id}</li>
 * </ul>
 *
 * <h3>Open-series scenario (priorities 200+)</h3>
 * <ol>
 *   <li><strong>Completed EP1 POST</strong> – {@code hasCompletedPlayBack=true}. EP1 must be absent from GET CW;
 *       configured EP2 must appear with progress&nbsp;=&nbsp;0.</li>
 *   <li><strong>Completed last-episode POST</strong> – {@code hasCompletedPlayBack=true}. Last episode must be absent
 *       from GET CW.</li>
 *   <li><strong>Watch-again GET</strong> – open-series editorial id must appear (series row, not episode id).</li>
 * </ol>
 *
 * <h3>Open-series required properties</h3>
 * <ul>
 *   <li>{@code vrgo.cw.open.series.id}</li>
 *   <li>{@code vrgo.cw.open.series.ep1.content.id}</li>
 *   <li>{@code vrgo.cw.open.series.ep1.total.duration}</li>
 *   <li>{@code vrgo.cw.open.series.ep2.content.id}</li>
 *   <li>{@code vrgo.cw.open.series.last.ep.content.id}</li>
 * </ul>
 */
@Feature("Continue watch")
public class ContinueWatchScenarios extends BaseTest {

    private static final double COMPLETION_THRESHOLD = 0.97;

    // =================== Test methods ===================

    /**
     * Deletes every item currently in the CW list so the series-episode scenario starts from
     * a clean slate. Without this, leftover entries from previous runs (e.g. EP2 already in CW)
     * cause the API to skip EP2 and promote EP3 after EP1 completes.
     */
    @Test(
            priority = 0,
            description = "DELETE all existing CW items — clean slate before series-episode scenario"
    )
    @Story("DELETE /subscriber-event-service/v3/continue-watch/ (pre-scenario cleanup)")
    public void deleteAllCwItems_cleanSlateBeforeScenario() {
        requireSeriesEpPrerequisites();
        deleteAllCwItemsInternal("series.ep");
    }

    @Test(
            priority = 10,
            description = "POST series EP1 without hasCompletedPlayBack — watchDuration < 97% of total; EP1 must appear in CW list"
    )
    @Story("POST /subscriber-activity-producer/v3/subscriber-continue-watch (series EP1 partial watch)")
    public void addEp1WithPartialDuration_ep1AppearsInCwList() {
        requireSeriesEpPrerequisites();
        deleteSeriesEpisodesCwItemsBestEffort();

        String ep1Id        = ep1ContentId();
        String ep1Type      = ep1ContentType();
        int    totalDur     = ep1TotalDuration();
        int    partialDur   = resolvePartialDuration(totalDur);
        String subscriberId = subscriberId();

        Allure.parameter("environment", Environment.current().name());
        Allure.parameter("cw.series.ep1.content.id", ep1Id);
        Allure.parameter("cw.series.ep1.total.duration.s", String.valueOf(totalDur));
        Allure.parameter("cw.series.ep1.partial.watch.duration.s", String.valueOf(partialDur));
        Allure.parameter("cw.series.ep1.completion.threshold", "97%");

        SubscriberContinueWatchRequest body =
                new SubscriberContinueWatchRequest(ep1Id, ep1Type, partialDur, subscriberId);
        AllureAttachmentUtils.attachJson("series-ep1-partial-cw-request", JsonUtils.toJson(body));

        Response r = continueWatchApi.addSubscriberContinueWatchNoFlagRaw(body);
        AllureAttachmentUtils.attachJson("series-ep1-partial-cw-response", r.asString());
        r.then().statusCode(200);
    }

    @Test(
            priority = 20,
            description = "GET continue-watch after partial EP1 — EP1 must be present in list with non-zero progress",
            dependsOnMethods = {"addEp1WithPartialDuration_ep1AppearsInCwList"}
    )
    @Story("GET /subscriber-event-service/v3/continue-watch/continue (EP1 partial validation)")
    public void getCwAfterPartialEp1_ep1PresentWithNonZeroProgress() {
        requireSeriesEpPrerequisites();

        String ep1Id = ep1ContentId();
        Response r = pollCwUntilEp1Appears(ep1Id);

        List<Map<String, Object>> rows = ContinueWatch.cwListRows(r);
        Set<String>               allIds = ContinueWatch.cwListContentIds(r);

        Allure.parameter("cw.ids.found.sample", String.join(", ", allIds.stream().limit(20).toList()));

        Assert.assertTrue(
                ContinueWatch.cwListContainsConfiguredId(allIds, ep1Id),
                "CW GET must include EP1 after partial watchDuration POST; ep1Id=" + ep1Id + " foundIds=" + allIds
        );

        Map<String, Object> ep1Row = findRowById(rows, ep1Id);
        if (ep1Row != null) {
            assertRowHasNonZeroProgress(ep1Row, "EP1", ep1Id);
        }
    }

    /**
     * Calls {@code POST cw/v3/progress} after the partial EP1 watch and verifies EP1's
     * recorded progress is non-zero.
     */
    @Test(
            priority = 25,
            description = "POST cw/v3/progress after partial EP1 — EP1 must have non-zero watchDuration / progress",
            dependsOnMethods = {"getCwAfterPartialEp1_ep1PresentWithNonZeroProgress"}
    )
    @Story("POST /subscriber-event-service/cw/v3/progress (EP1 partial progress verification)")
    public void postCwProgressV3_ep1HasNonZeroProgressAfterPartialWatch() {
        requireSeriesEpPrerequisites();

        String ep1Id = ep1ContentId();

        Map<String, Object> body = Map.of("filters", List.of());
        AllureAttachmentUtils.attachJson("cw-progress-v3-partial-request", JsonUtils.toJson(body));

        Response r = continueWatchApi.postCwProgressV3Raw(body);
        AllureAttachmentUtils.attachJson("cw-progress-v3-partial-response", r.asString());
        r.then().statusCode(200).body("status", equalTo(true));

        Number ep1Progress = findProgressInCwV3Response(r, ep1Id);
        Allure.parameter("cw.v3.progress.ep1.id", ep1Id);
        Allure.parameter("cw.v3.progress.ep1.value", ep1Progress == null ? "not found in response" : String.valueOf(ep1Progress));

        if (ep1Progress != null) {
            Assert.assertTrue(
                    ep1Progress.doubleValue() > 0,
                    "cw/v3/progress must report EP1 watchDuration/progress > 0 after partial watch; "
                    + "ep1Id=" + ep1Id + " value=" + ep1Progress
            );
        }
    }

    @Test(
            priority = 30,
            description = "POST series EP1 without hasCompletedPlayBack — watchDuration >= 97% of total; triggers EP1 completion",
            dependsOnMethods = {"getCwAfterPartialEp1_ep1PresentWithNonZeroProgress"}
    )
    @Story("POST /subscriber-activity-producer/v3/subscriber-continue-watch (series EP1 completed >= 97%)")
    public void addEp1WithCompletedDuration_ep1MarkedAsCompleted() {
        requireSeriesEpPrerequisites();

        String ep1Id        = ep1ContentId();
        String ep1Type      = ep1ContentType();
        int    totalDur     = ep1TotalDuration();
        int    completeDur  = resolveCompletedDuration(totalDur);
        String subscriberId = subscriberId();

        Allure.parameter("cw.series.ep1.content.id", ep1Id);
        Allure.parameter("cw.series.ep1.total.duration.s", String.valueOf(totalDur));
        Allure.parameter("cw.series.ep1.complete.watch.duration.s", String.valueOf(completeDur));
        Allure.parameter("cw.series.ep1.completion.threshold", "97%");

        SubscriberContinueWatchRequest body =
                new SubscriberContinueWatchRequest(ep1Id, ep1Type, completeDur, subscriberId);
        AllureAttachmentUtils.attachJson("series-ep1-completed-cw-request", JsonUtils.toJson(body));

        Response r = continueWatchApi.addSubscriberContinueWatchNoFlagRaw(body);
        AllureAttachmentUtils.attachJson("series-ep1-completed-cw-response", r.asString());
        r.then().statusCode(200);
    }

    /**
     * After EP1 is POSTed with {@code watchDuration >= 97%} of total:
     * <ul>
     *   <li>EP1 must <em>not</em> be present in the CW list.</li>
     *   <li>Configured EP2 (next episode of the same series) must be present with {@code progress = 0}.</li>
     * </ul>
     */
    @Test(
            priority = 40,
            description = "GET continue-watch after EP1 completion — EP1 absent; next episode present with progress 0",
            dependsOnMethods = {"addEp1WithCompletedDuration_ep1MarkedAsCompleted"}
    )
    @Story("GET /subscriber-event-service/v3/continue-watch/continue (EP1 complete: EP1 absent, next episode at 0%)")
    public void getCwAfterCompletedEp1_ep1AbsentEp2PresentWithZeroProgress() {
        requireSeriesEpPrerequisites();

        String ep1Id    = ep1ContentId();
        String ep2Id    = ep2ContentId();
        String seriesId = seriesId();

        Response r = pollCwUntilContentIdAbsentAndOtherPresent(ep1Id, ep2Id, "series.ep");

        List<Map<String, Object>> rows  = ContinueWatch.cwListRows(r);
        Set<String>               allIds = ContinueWatch.cwListContentIds(r);

        Allure.parameter("cw.series.id", seriesId);
        Allure.parameter("cw.series.ep2.configured.id", ep2Id);
        Allure.parameter("cw.ids.found.sample", String.join(", ", allIds.stream().limit(20).toList()));

        Assert.assertFalse(
                ContinueWatch.cwListContainsConfiguredId(allIds, ep1Id),
                "EP1 must NOT be in CW list after watchDuration >= 97% POST; ep1Id=" + ep1Id + " foundIds=" + allIds
        );
        Assert.assertTrue(
                ContinueWatch.cwListContainsConfiguredId(allIds, ep2Id),
                "Configured EP2 (same series as EP1) must appear in CW list with 0 progress after EP1 completion; "
                        + "ep2Id=" + ep2Id + " seriesId=" + seriesId + " foundIds=" + allIds
        );

        Map<String, Object> ep2Row = findRowById(rows, ep2Id);
        Assert.assertNotNull(ep2Row, "EP2 row must be present in parsed CW rows; ep2Id=" + ep2Id);
        assertRowHasZeroProgress(ep2Row, "EP2", ep2Id);
        assertEpisodeListedInSeriesRecentCw(seriesId, ep2Id);
    }

    /**
     * Calls {@code POST cw/v3/progress} after EP1 completion and verifies:
     * <ul>
     *   <li>EP1's recorded progress is &ge; 97 % of total duration (completion level).</li>
     *   <li>Configured EP2 (same series) has progress&nbsp;=&nbsp;0 when present in the response.</li>
     * </ul>
     */
    @Test(
            priority = 45,
            description = "POST cw/v3/progress after EP1 completion — EP1 at completed progress; next episode at 0",
            dependsOnMethods = {"getCwAfterCompletedEp1_ep1AbsentEp2PresentWithZeroProgress"}
    )
    @Story("POST /subscriber-event-service/cw/v3/progress (EP1 completed, next episode at 0%)")
    public void postCwProgressV3_ep1CompletedAndNextEpisodeAtZeroProgress() {
        requireSeriesEpPrerequisites();

        String ep1Id    = ep1ContentId();
        String ep2Id    = ep2ContentId();
        int    totalDur = ep1TotalDuration();
        int    completedWatchDuration = resolveCompletedDuration(totalDur);
        double completedThresholdPct = roundTo2Decimals(COMPLETION_THRESHOLD * 100);

        Map<String, Object> body = Map.of("filters", List.of());
        AllureAttachmentUtils.attachJson("cw-progress-v3-completed-request", JsonUtils.toJson(body));

        Response r = continueWatchApi.postCwProgressV3Raw(body);
        AllureAttachmentUtils.attachJson("cw-progress-v3-completed-response", r.asString());
        r.then().statusCode(200).body("status", equalTo(true));

        // --- EP1 completed progress ---
        Number ep1Progress = findProgressInCwV3Response(r, ep1Id);
        double ep1ProgressPct = ep1Progress == null ? Double.NaN : roundTo2Decimals(ep1Progress.doubleValue());
        Allure.parameter("cw.v3.progress.ep1.id", ep1Id);
        Allure.parameter("cw.v3.progress.ep1.value",
                ep1Progress == null ? "not found in response" : String.valueOf(ep1ProgressPct));
        Allure.parameter("cw.v3.progress.ep1.completed.threshold.pct", String.valueOf(completedThresholdPct));
        Allure.parameter("cw.v3.progress.ep1.completed.watch.duration.s", String.valueOf(completedWatchDuration));

        if (ep1Progress != null) {
            Assert.assertTrue(
                    ep1ProgressPct >= completedThresholdPct,
                    "cw/v3/progress must report EP1 progress >= " + completedThresholdPct
                    + "% (97% of total " + totalDur + "s, watchDuration >= " + completedWatchDuration + "s) after completion; "
                    + "ep1Id=" + ep1Id + " actual=" + ep1ProgressPct + "%"
            );
        }

        // --- EP2 (same series) at 0 ---
        Number ep2Progress = findProgressInCwV3Response(r, ep2Id);
        Allure.parameter("cw.v3.progress.ep2.id", ep2Id);
        Allure.parameter("cw.v3.progress.ep2.value",
                ep2Progress == null ? "not found in response" : String.valueOf(ep2Progress));

        if (ep2Progress != null) {
            Assert.assertEquals(
                    ep2Progress.intValue(), 0,
                    "cw/v3/progress must report configured EP2 watchDuration/progress = 0; "
                    + "ep2Id=" + ep2Id + " actual=" + ep2Progress
            );
        }
    }

    /**
     * Builds the {@code POST .../contents/progress} payload from the current GET CW list and asserts:
     * <ul>
     *   <li>EP1 is <em>not</em> included in the payload (EP1 must not appear with 0 progress after completion).</li>
     *   <li>EP2 is included when it is present in the CW list.</li>
     * </ul>
     */
    @Test(
            priority = 50,
            description = "POST contents/progress — payload built from GET CW must exclude EP1 (no zero-progress EP1 after completion)",
            dependsOnMethods = {"getCwAfterCompletedEp1_ep1AbsentEp2PresentWithZeroProgress"}
    )
    @Story("POST /subscriber-event-service/v3/continue-watch/contents/progress (EP1 excluded post-completion)")
    public void postContinueWatchContentsProgress_ep1AbsentFromPayloadAfterCompletion() {
        requireSeriesEpPrerequisites();

        String ep1Id = ep1ContentId();
        String ep2Id = ep2ContentId();

        Response cw = continueWatchApi.getContinueWatchRaw(20, 0, false);
        cw.then().statusCode(200).body("status", equalTo(true));

        List<Map<String, Object>> rows = ContinueWatch.cwListRows(cw);
        Set<String> cwIds = ContinueWatch.cwListContentIds(cw);

        Map<String, String> progressBody = buildProgressPayload(rows);
        AllureAttachmentUtils.attachJson("series-ep-contents-progress-request", JsonUtils.toJson(progressBody));
        Allure.parameter("cw.series.progress.payload.keys", String.join(", ", progressBody.keySet()));

        Assert.assertFalse(
                ContinueWatch.cwListContainsConfiguredId(progressBody.keySet(), ep1Id),
                "Contents-progress payload must NOT include EP1 after completion " +
                "(EP1 should not appear with 0 progress); ep1Id=" + ep1Id + " payloadKeys=" + progressBody.keySet()
        );

        Assert.assertTrue(
                ContinueWatch.cwListContainsConfiguredId(cwIds, ep2Id),
                "CW list should include configured EP2 after EP1 completion; ep2Id=" + ep2Id + " cwIds=" + cwIds
        );
        Assert.assertTrue(
                ContinueWatch.cwListContainsConfiguredId(progressBody.keySet(), ep2Id),
                "Contents-progress payload should include EP2 (EP2 is in CW list); ep2Id=" + ep2Id
        );

        String region = regionForProgress();
        Allure.parameter("cw.series.progress.region", region);

        Response r = continueWatchApi.postContinueWatchContentsProgressRaw(region, progressBody);
        AllureAttachmentUtils.attachJson("series-ep-contents-progress-response", r.asString());
        r.then().statusCode(200).body("status", equalTo(true));
    }

    /**
     * Queries {@code GET .../continue-watch/content/recent} for EP1 after its completion.
     * EP1 must <em>not</em> appear in the response with {@code watchDuration / progress = 0}.
     * Either EP1 is absent (not recent) or it carries the non-zero completed progress value.
     */
    @Test(
            priority = 60,
            description = "GET continue-watch/content/recent for EP1 — must not return EP1 with zero progress after completion",
            dependsOnMethods = {"getCwAfterCompletedEp1_ep1AbsentEp2PresentWithZeroProgress"}
    )
    @Story("GET /subscriber-event-service/v3/continue-watch/content/recent (EP1 not at zero after completion)")
    public void getRecentContinueWatchContent_ep1NotPresentWithZeroProgressAfterCompletion() {
        requireSeriesEpPrerequisites();

        String ep1Id   = ep1ContentId();
        String ep1Type = ep1ContentType();
        String region  = regionForRecentLookup();

        Allure.parameter("cw.series.ep1.content.id", ep1Id);
        Allure.parameter("cw.series.ep1.content.type", ep1Type);
        Allure.parameter("cw.series.recent.region", region);

        Response r = continueWatchApi.getContinueWatchRecentContentRaw(ep1Type, region, ep1Id);
        AllureAttachmentUtils.attachJson("series-ep1-recent-content-response", r.asString());
        r.then().statusCode(200).body("status", equalTo(true));

        assertEp1NotPresentWithZeroProgressInRecentResponse(r, ep1Id);
    }

    // =================== Boxset-movies scenario ===================

    @Test(
            priority = 100,
            description = "DELETE all existing CW items — clean slate before boxset-movies scenario"
    )
    @Story("DELETE /subscriber-event-service/v3/continue-watch/ (boxset-movies pre-scenario cleanup)")
    public void deleteAllCwItems_boxsetMoviesCleanSlate() {
        requireBoxsetMoviesPrerequisites();
        deleteAllCwItemsInternal("boxset.movies");
    }

    @Test(
            priority = 110,
            description = "POST boxset movie1 with hasCompletedPlayBack=false — partial watch; movie1 in CW only"
    )
    @Story("POST /subscriber-activity-producer/v3/subscriber-continue-watch (boxset movie1 partial)")
    public void addBoxsetMovie1Partial_hasCompletedPlayBackFalse() {
        requireBoxsetMoviesPrerequisites();
        deleteBoxsetMoviesCwItemsBestEffort();

        String movie1Id        = boxsetMovie1ContentId();
        String movie1Type      = boxsetMovie1ContentType();
        int    partialDur      = boxsetMovie1PartialWatchDuration();
        String subscriberId    = subscriberId();

        Allure.parameter("environment", Environment.current().name());
        Allure.parameter("cw.boxset.movies.boxset.id", boxsetId());
        Allure.parameter("cw.boxset.movies.movie1.content.id", movie1Id);
        Allure.parameter("cw.boxset.movies.movie1.partial.watch.duration.s", String.valueOf(partialDur));
        Allure.parameter("cw.boxset.movies.hasCompletedPlayBack", "false");

        SubscriberContinueWatchRequest body =
                new SubscriberContinueWatchRequest(movie1Id, movie1Type, partialDur, subscriberId);
        AllureAttachmentUtils.attachJson("boxset-movie1-partial-cw-request", JsonUtils.toJson(body));

        Response r = continueWatchApi.addSubscriberContinueWatchRaw(false, body);
        AllureAttachmentUtils.attachJson("boxset-movie1-partial-cw-response", r.asString());
        r.then().statusCode(200);
    }

    @Test(
            priority = 120,
            description = "GET continue-watch after partial movie1 — movie1 present; movie2 absent",
            dependsOnMethods = {"addBoxsetMovie1Partial_hasCompletedPlayBackFalse"}
    )
    @Story("GET /subscriber-event-service/v3/continue-watch/continue (boxset movie1 partial validation)")
    public void getCwAfterPartialBoxsetMovie1_movie1PresentMovie2Absent() {
        requireBoxsetMoviesPrerequisites();

        String movie1Id = boxsetMovie1ContentId();
        String movie2Id = boxsetMovie2ContentId();

        Response r = pollCwUntilContentIdAppears(movie1Id, "boxset.movies.movie1");

        List<Map<String, Object>> rows = ContinueWatch.cwListRows(r);
        Set<String>               allIds = ContinueWatch.cwListContentIds(r);

        Allure.parameter("cw.boxset.movies.ids.found.sample", String.join(", ", allIds.stream().limit(20).toList()));

        Assert.assertTrue(
                ContinueWatch.cwListContainsConfiguredId(allIds, movie1Id),
                "CW GET must include boxset movie1 after partial POST with hasCompletedPlayBack=false; "
                        + "movie1Id=" + movie1Id + " foundIds=" + allIds
        );
        Assert.assertFalse(
                ContinueWatch.cwListContainsConfiguredId(allIds, movie2Id),
                "CW GET must NOT include boxset movie2 after partial movie1 POST; "
                        + "movie2Id=" + movie2Id + " foundIds=" + allIds
        );

        Map<String, Object> movie1Row = findRowById(rows, movie1Id);
        if (movie1Row != null) {
            assertRowHasNonZeroProgress(movie1Row, "boxset movie1", movie1Id);
        }
    }

    @Test(
            priority = 130,
            description = "POST boxset movie1 with hasCompletedPlayBack=true — completes movie1 and promotes movie2",
            dependsOnMethods = {"getCwAfterPartialBoxsetMovie1_movie1PresentMovie2Absent"}
    )
    @Story("POST /subscriber-activity-producer/v3/subscriber-continue-watch (boxset movie1 completed)")
    public void addBoxsetMovie1Completed_hasCompletedPlayBackTrue() {
        requireBoxsetMoviesPrerequisites();

        String movie1Id        = boxsetMovie1ContentId();
        String movie1Type      = boxsetMovie1ContentType();
        int    completedDur    = boxsetMovie1CompletedWatchDuration();
        String subscriberId    = subscriberId();

        Allure.parameter("cw.boxset.movies.movie1.content.id", movie1Id);
        Allure.parameter("cw.boxset.movies.movie1.completed.watch.duration.s", String.valueOf(completedDur));
        Allure.parameter("cw.boxset.movies.hasCompletedPlayBack", "true");

        SubscriberContinueWatchRequest body =
                new SubscriberContinueWatchRequest(movie1Id, movie1Type, completedDur, subscriberId);
        AllureAttachmentUtils.attachJson("boxset-movie1-completed-cw-request", JsonUtils.toJson(body));

        Response r = continueWatchApi.addSubscriberContinueWatchRaw(true, body);
        AllureAttachmentUtils.attachJson("boxset-movie1-completed-cw-response", r.asString());
        r.then().statusCode(200);
    }

    @Test(
            priority = 140,
            description = "GET continue-watch after completed movie1 — movie1 absent; movie2 present with progress 0",
            dependsOnMethods = {"addBoxsetMovie1Completed_hasCompletedPlayBackTrue"}
    )
    @Story("GET /subscriber-event-service/v3/continue-watch/continue (boxset movie1 complete: movie2 at 0%)")
    public void getCwAfterCompletedBoxsetMovie1_movie1AbsentMovie2PresentWithZeroProgress() {
        requireBoxsetMoviesPrerequisites();

        String movie1Id = boxsetMovie1ContentId();
        String movie2Id = boxsetMovie2ContentId();

        Response r = pollCwUntilContentIdAbsentAndOtherPresent(movie1Id, movie2Id, "boxset.movies");

        List<Map<String, Object>> rows  = ContinueWatch.cwListRows(r);
        Set<String>               allIds = ContinueWatch.cwListContentIds(r);

        Allure.parameter("cw.boxset.movies.ids.found.sample", String.join(", ", allIds.stream().limit(20).toList()));

        Assert.assertFalse(
                ContinueWatch.cwListContainsConfiguredId(allIds, movie1Id),
                "Boxset movie1 must NOT be in CW list after hasCompletedPlayBack=true POST; "
                        + "movie1Id=" + movie1Id + " foundIds=" + allIds
        );
        Assert.assertTrue(
                ContinueWatch.cwListContainsConfiguredId(allIds, movie2Id),
                "Boxset movie2 must appear in CW list with 0 progress after movie1 completion; "
                        + "movie2Id=" + movie2Id + " foundIds=" + allIds
        );

        Map<String, Object> movie2Row = findRowById(rows, movie2Id);
        Assert.assertNotNull(movie2Row, "Movie2 row must be present in parsed CW rows; movie2Id=" + movie2Id);
        assertRowHasZeroProgress(movie2Row, "boxset movie2", movie2Id);
    }

    @Test(
            priority = 150,
            description = "GET watch-again after completed movie1 — movie1 must appear in watch-again list",
            dependsOnMethods = {"getCwAfterCompletedBoxsetMovie1_movie1AbsentMovie2PresentWithZeroProgress"}
    )
    @Story("GET /subscriber-event-service/v3/watch-again (boxset movie1 after completion)")
    public void getWatchAgainAfterCompletedBoxsetMovie1_movie1Present() {
        requireBoxsetMoviesPrerequisites();

        String movie1Id = boxsetMovie1ContentId();

        int limit = readIntProperty("vrgo.watch.again.limit", 20);
        int offset = readIntProperty("vrgo.watch.again.offset", 0);
        String contentType = firstNonBlank(config.getProperty("vrgo.watch.again.content.type"), "VOD");
        boolean ent = readBooleanProperty("vrgo.watch.again.is.entitlement.enabled", false);

        Allure.parameter("watchAgain.limit", String.valueOf(limit));
        Allure.parameter("watchAgain.offset", String.valueOf(offset));
        Allure.parameter("watchAgain.contentType", contentType);
        Allure.parameter("watchAgain.expectedMovie1Id", movie1Id);

        Response r = pollWatchAgainUntilMoviePresent(limit, offset, contentType, ent, movie1Id);
        AllureAttachmentUtils.attachJson("boxset-movie1-watch-again-response", r.asString());
        r.then().statusCode(200).body("status", equalTo(true));

        Set<String> ids = ContinueWatch.cwListContentIds(r);
        String body = r.asString();
        Assert.assertTrue(
                watchAgainMoviePresent(ids, body, movie1Id),
                "Watch-again list must include boxset movie1 after hasCompletedPlayBack=true POST; "
                        + "movie1Id=" + movie1Id + " ids=" + ids
        );
    }

    // =================== Open-series scenario ===================

    @Test(
            priority = 200,
            description = "DELETE all existing CW items — clean slate before open-series scenario"
    )
    @Story("DELETE /subscriber-event-service/v3/continue-watch/ (open-series pre-scenario cleanup)")
    public void deleteAllCwItems_openSeriesCleanSlate() {
        requireOpenSeriesPrerequisites();
        deleteAllCwItemsInternal("open.series");
    }

    @Test(
            priority = 210,
            description = "POST open-series EP1 with hasCompletedPlayBack=true — completes EP1 and promotes EP2"
    )
    @Story("POST /subscriber-activity-producer/v3/subscriber-continue-watch (open-series EP1 completed)")
    public void addOpenSeriesEp1Completed_hasCompletedPlayBackTrue() {
        requireOpenSeriesPrerequisites();
        deleteOpenSeriesCwItemsBestEffort();

        String ep1Id        = openSeriesEp1ContentId();
        String ep1Type      = openSeriesEp1ContentType();
        int    completedDur = openSeriesEp1CompletedWatchDuration();
        String subscriberId = subscriberId();

        Allure.parameter("environment", Environment.current().name());
        Allure.parameter("cw.open.series.id", openSeriesId());
        Allure.parameter("cw.open.series.ep1.content.id", ep1Id);
        Allure.parameter("cw.open.series.ep1.completed.watch.duration.s", String.valueOf(completedDur));
        Allure.parameter("cw.open.series.hasCompletedPlayBack", "true");

        SubscriberContinueWatchRequest body =
                new SubscriberContinueWatchRequest(ep1Id, ep1Type, completedDur, subscriberId);
        AllureAttachmentUtils.attachJson("open-series-ep1-completed-cw-request", JsonUtils.toJson(body));

        Response r = continueWatchApi.addSubscriberContinueWatchRaw(true, body);
        AllureAttachmentUtils.attachJson("open-series-ep1-completed-cw-response", r.asString());
        r.then().statusCode(200);
    }

    @Test(
            priority = 220,
            description = "GET continue-watch after completed open-series EP1 — EP1 absent; EP2 present with progress 0",
            dependsOnMethods = {"addOpenSeriesEp1Completed_hasCompletedPlayBackTrue"}
    )
    @Story("GET /subscriber-event-service/v3/continue-watch/continue (open-series EP1 complete: EP2 at 0%)")
    public void getCwAfterCompletedOpenSeriesEp1_ep1AbsentEp2PresentWithZeroProgress() {
        requireOpenSeriesPrerequisites();

        String ep1Id = openSeriesEp1ContentId();
        String ep2Id = openSeriesEp2ContentId();

        Response r = pollCwUntilContentIdAbsentAndOtherPresent(ep1Id, ep2Id, "open.series");

        List<Map<String, Object>> rows  = ContinueWatch.cwListRows(r);
        Set<String>               allIds = ContinueWatch.cwListContentIds(r);

        Allure.parameter("cw.open.series.ids.found.sample", String.join(", ", allIds.stream().limit(20).toList()));

        Assert.assertFalse(
                ContinueWatch.cwListContainsConfiguredId(allIds, ep1Id),
                "Open-series EP1 must NOT be in CW list after hasCompletedPlayBack=true POST; "
                        + "ep1Id=" + ep1Id + " foundIds=" + allIds
        );
        Assert.assertTrue(
                ContinueWatch.cwListContainsConfiguredId(allIds, ep2Id),
                "Open-series EP2 must appear in CW list with 0 progress after EP1 completion; "
                        + "ep2Id=" + ep2Id + " foundIds=" + allIds
        );

        Map<String, Object> ep2Row = findRowById(rows, ep2Id);
        Assert.assertNotNull(ep2Row, "EP2 row must be present in parsed CW rows; ep2Id=" + ep2Id);
        assertRowHasZeroProgress(ep2Row, "open-series EP2", ep2Id);
    }

    @Test(
            priority = 230,
            description = "POST open-series last episode with hasCompletedPlayBack=true — completes series",
            dependsOnMethods = {"getCwAfterCompletedOpenSeriesEp1_ep1AbsentEp2PresentWithZeroProgress"}
    )
    @Story("POST /subscriber-activity-producer/v3/subscriber-continue-watch (open-series last episode completed)")
    public void addOpenSeriesLastEpCompleted_hasCompletedPlayBackTrue() {
        requireOpenSeriesPrerequisites();

        String lastEpId     = openSeriesLastEpContentId();
        String lastEpType   = openSeriesLastEpContentType();
        int    completedDur = openSeriesLastEpCompletedWatchDuration();
        String subscriberId = subscriberId();

        Allure.parameter("cw.open.series.last.ep.content.id", lastEpId);
        Allure.parameter("cw.open.series.last.ep.completed.watch.duration.s", String.valueOf(completedDur));
        Allure.parameter("cw.open.series.hasCompletedPlayBack", "true");

        SubscriberContinueWatchRequest body =
                new SubscriberContinueWatchRequest(lastEpId, lastEpType, completedDur, subscriberId);
        AllureAttachmentUtils.attachJson("open-series-last-ep-completed-cw-request", JsonUtils.toJson(body));

        Response r = continueWatchApi.addSubscriberContinueWatchRaw(true, body);
        AllureAttachmentUtils.attachJson("open-series-last-ep-completed-cw-response", r.asString());
        r.then().statusCode(200);
    }

    @Test(
            priority = 240,
            description = "GET continue-watch after completed open-series last episode — last episode absent from CW",
            dependsOnMethods = {"addOpenSeriesLastEpCompleted_hasCompletedPlayBackTrue"}
    )
    @Story("GET /subscriber-event-service/v3/continue-watch/continue (open-series last episode absent after completion)")
    public void getCwAfterCompletedOpenSeriesLastEp_lastEpAbsent() {
        requireOpenSeriesPrerequisites();

        String lastEpId = openSeriesLastEpContentId();

        Response r = pollCwUntilContentIdAbsent(lastEpId, "open.series.last.ep");

        Set<String> allIds = ContinueWatch.cwListContentIds(r);

        Allure.parameter("cw.open.series.ids.found.sample", String.join(", ", allIds.stream().limit(20).toList()));

        Assert.assertFalse(
                ContinueWatch.cwListContainsConfiguredId(allIds, lastEpId),
                "Open-series last episode must NOT be in CW list after hasCompletedPlayBack=true POST; "
                        + "lastEpId=" + lastEpId + " foundIds=" + allIds
        );
    }

    @Test(
            priority = 250,
            description = "GET watch-again after completed open-series last episode — series editorial id must appear",
            dependsOnMethods = {"getCwAfterCompletedOpenSeriesLastEp_lastEpAbsent"}
    )
    @Story("GET /subscriber-event-service/v3/watch-again (open-series after last episode completion)")
    public void getWatchAgainAfterCompletedOpenSeriesLastEp_seriesPresent() {
        requireOpenSeriesPrerequisites();

        String seriesId = openSeriesId();

        int limit = readIntProperty("vrgo.watch.again.limit", 20);
        int offset = readIntProperty("vrgo.watch.again.offset", 0);
        String contentType = firstNonBlank(config.getProperty("vrgo.watch.again.content.type"), "VOD");
        boolean ent = readBooleanProperty("vrgo.watch.again.is.entitlement.enabled", false);

        Allure.parameter("watchAgain.limit", String.valueOf(limit));
        Allure.parameter("watchAgain.offset", String.valueOf(offset));
        Allure.parameter("watchAgain.contentType", contentType);
        Allure.parameter("watchAgain.expectedOpenSeriesId", seriesId);

        Response r = pollWatchAgainUntilSeriesPresent(limit, offset, contentType, ent, seriesId);
        AllureAttachmentUtils.attachJson("open-series-watch-again-response", r.asString());
        r.then().statusCode(200).body("status", equalTo(true));

        Set<String> ids = ContinueWatch.cwListContentIds(r);
        String body = r.asString();
        Assert.assertTrue(
                watchAgainSeriesPresent(ids, body, seriesId),
                "Watch-again list must include open-series editorial id after last-episode completion; "
                        + "seriesId=" + seriesId + " ids=" + ids
        );
    }

    // =================== Polling helpers ===================

    /**
     * Removes configured open-series EP1/EP2/last-episode CW entries via direct DELETE calls only.
     */
    private void deleteOpenSeriesCwItemsBestEffort() {
        String ep1Id = openSeriesEp1ContentId();
        String ep2Id = openSeriesEp2ContentId();
        String lastEpId = openSeriesLastEpContentId();
        String ep1Type = openSeriesEp1ContentType();
        String ep2Type = openSeriesEp2ContentType();
        String lastEpType = openSeriesLastEpContentType();

        Allure.parameter("cw.open.series.cleanup.ep1.id", ep1Id);
        Allure.parameter("cw.open.series.cleanup.ep2.id", ep2Id);
        Allure.parameter("cw.open.series.cleanup.last.ep.id", lastEpId);

        deleteCwItemBestEffort(ep1Id, ep1Type);
        deleteCwItemBestEffort(ep2Id, ep2Type);
        deleteCwItemBestEffort(lastEpId, lastEpType);
    }

    /**
     * Removes configured series EP1/EP2 CW entries via direct DELETE calls only.
     * Does not call GET CW — safe to run before the series POST scenario even when the list API is down.
     */
    private void deleteSeriesEpisodesCwItemsBestEffort() {
        String ep1Id = ep1ContentId();
        String ep2Id = ep2ContentId();
        String ep1Type = ep1ContentType();
        String ep2Type = ep2ContentType();

        Allure.parameter("cw.series.cleanup.ep1.id", ep1Id);
        Allure.parameter("cw.series.cleanup.ep2.id", ep2Id);

        deleteCwItemBestEffort(ep1Id, ep1Type);
        deleteCwItemBestEffort(ep2Id, ep2Type);
    }

    /**
     * Verifies {@code episodeId} appears in the series-scoped recent CW list for {@code seriesId}.
     */
    private void assertEpisodeListedInSeriesRecentCw(String seriesId, String episodeId) {
        String contentType = config.getProperty("vrgo.cw.recent.series.content.type");
        if (isBlank(contentType)) {
            contentType = "VOD";
        } else {
            contentType = contentType.strip();
        }
        String region = regionForRecentLookup();

        Allure.parameter("cw.series.recent.seriesId", seriesId);
        Allure.parameter("cw.series.recent.expectedEpisodeId", episodeId);
        Allure.parameter("cw.series.recent.contentType", contentType);
        Allure.parameter("cw.series.recent.region", region);

        Response r = continueWatchApi.getContinueWatchRecentBySeriesRaw(seriesId, contentType, region);
        AllureAttachmentUtils.attachJson("series-ep2-recent-by-series-" + seriesId, r.asString());
        r.then().statusCode(200).body("status", equalTo(true));

        Set<String> episodeIds = ContinueWatch.cwListContentIds(r);
        Allure.parameter("cw.series.recent.episodeIds", String.join(", ", episodeIds));

        Assert.assertTrue(
                ContinueWatch.cwListContainsConfiguredId(episodeIds, episodeId),
                "Promoted episode must appear in series recent CW for the configured series; seriesId="
                        + seriesId + " episodeId=" + episodeId + " foundEpisodeIds=" + episodeIds
        );
    }

    /**
     * Removes configured boxset movie1/movie2 CW entries via direct DELETE calls only.
     * Does not call GET CW — safe to run before the boxset POST scenario even when the list API is down.
     */
    private void deleteBoxsetMoviesCwItemsBestEffort() {
        String movie1Id = boxsetMovie1ContentId();
        String movie2Id = boxsetMovie2ContentId();
        String movie1Type = boxsetMovie1ContentType();
        String movie2Type = boxsetMovie2ContentType();

        Allure.parameter("cw.boxset.movies.cleanup.movie1.id", movie1Id);
        Allure.parameter("cw.boxset.movies.cleanup.movie2.id", movie2Id);

        deleteCwItemBestEffort(movie1Id, movie1Type);
        deleteCwItemBestEffort(movie2Id, movie2Type);
    }

    private void deleteCwItemBestEffort(String contentId, String contentType) {
        try {
            Response del = continueWatchApi.deleteContinueWatchItemRaw(contentId, contentType);
            int status = del.statusCode();
            if (status != 200 && status != 204 && status != 404) {
                Allure.parameter("cw.cleanup." + contentId + ".status", String.valueOf(status));
            }
        } catch (RuntimeException e) {
            Allure.parameter("cw.cleanup." + contentId + ".error", e.getClass().getSimpleName());
        }
    }

    private void deleteAllCwItemsInternal(String scenarioLabel) {
        Allure.parameter("environment", Environment.current().name());
        Allure.parameter("cw.cleanup.scenario", scenarioLabel);

        Response get = continueWatchApi.getContinueWatchRaw(20, 0, false);
        get.then().statusCode(200).body("status", equalTo(true));

        List<Map<String, Object>> rows = ContinueWatch.cwListRows(get);
        if (rows.isEmpty()) {
            Allure.parameter("cw.cleanup.deleted", "0 (list already empty)");
            return;
        }

        Set<String> deleted = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String id = ContinueWatch.cwRowContentId(row);
            if (id == null || id.isBlank() || !deleted.add(id.strip())) {
                continue;
            }
            String type = ContinueWatch.cwRowContentType(row);
            Response del = continueWatchApi.deleteContinueWatchItemRaw(id.strip(), type);
            del.then().statusCode(anyOf(is(200), is(204)));
        }

        Allure.parameter("cw.cleanup.deleted", String.valueOf(deleted.size()));
        Allure.parameter("cw.cleanup.ids", String.join(", ", deleted));
    }

    /**
     * Polls GET CW until {@code contentId} appears (or timeout).
     */
    private Response pollCwUntilContentIdAppears(String contentId, String pollLabel) {
        int timeoutMs  = cwPollTimeoutMs();
        int intervalMs = cwPollIntervalMs();
        long pollStart = System.currentTimeMillis();
        long deadline  = pollStart + timeoutMs;
        Response last  = null;
        int attempt    = 0;
        while (true) {
            attempt++;
            last = continueWatchApi.getContinueWatchRaw(20, 0, false);
            last.then().statusCode(200).body("status", equalTo(true));
            Set<String> ids = ContinueWatch.cwListContentIds(last);
            if (ContinueWatch.cwListContainsConfiguredId(ids, contentId)) {
                Allure.parameter("cw.poll." + pollLabel + ".appear.attempts", String.valueOf(attempt));
                Allure.parameter("cw.poll." + pollLabel + ".appear.waited.ms",
                        String.valueOf(System.currentTimeMillis() - pollStart));
                return last;
            }
            if (System.currentTimeMillis() >= deadline) {
                Assert.fail(
                        contentId + " did not appear in CW list within " + timeoutMs + " ms "
                        + "(attempts=" + attempt + "). Increase vrgo.cw.afteradds.poll.timeout.ms."
                );
            }
            sleepQuietly(intervalMs);
        }
    }

    /**
     * Polls GET CW until {@code contentId} is no longer in the list.
     */
    private Response pollCwUntilContentIdAbsent(String contentId, String pollLabel) {
        int timeoutMs  = cwPollTimeoutMs();
        int intervalMs = cwPollIntervalMs();
        long pollStart = System.currentTimeMillis();
        long deadline  = pollStart + timeoutMs;
        Response last  = null;
        int attempt    = 0;

        while (true) {
            attempt++;
            last = continueWatchApi.getContinueWatchRaw(20, 0, false);
            last.then().statusCode(200).body("status", equalTo(true));
            Set<String> ids = ContinueWatch.cwListContentIds(last);
            if (!ContinueWatch.cwListContainsConfiguredId(ids, contentId)) {
                Allure.parameter("cw.poll." + pollLabel + ".absent.attempts", String.valueOf(attempt));
                Allure.parameter("cw.poll." + pollLabel + ".absent.waited.ms",
                        String.valueOf(System.currentTimeMillis() - pollStart));
                return last;
            }
            if (System.currentTimeMillis() >= deadline) {
                Assert.fail(
                        contentId + " was still present in CW list after " + timeoutMs + " ms "
                        + "(attempts=" + attempt + ", foundIds=" + ids + "). "
                        + "Increase vrgo.cw.afteradds.poll.timeout.ms."
                );
            }
            sleepQuietly(intervalMs);
        }
    }

    /**
     * Polls GET CW until {@code absentId} is gone and {@code presentId} is in the list.
     */
    private Response pollCwUntilContentIdAbsentAndOtherPresent(
            String absentId, String presentId, String pollLabel) {
        int timeoutMs  = cwPollTimeoutMs();
        int intervalMs = cwPollIntervalMs();
        long pollStart = System.currentTimeMillis();
        long deadline  = pollStart + timeoutMs;
        Response last  = null;
        int attempt    = 0;

        while (true) {
            attempt++;
            last = continueWatchApi.getContinueWatchRaw(20, 0, false);
            last.then().statusCode(200).body("status", equalTo(true));
            Set<String> ids = ContinueWatch.cwListContentIds(last);
            boolean absentGone = !ContinueWatch.cwListContainsConfiguredId(ids, absentId);
            boolean presentFound = ContinueWatch.cwListContainsConfiguredId(ids, presentId);

            if (absentGone && presentFound) {
                Allure.parameter("cw.poll." + pollLabel + ".promote.attempts", String.valueOf(attempt));
                Allure.parameter("cw.poll." + pollLabel + ".promote.waited.ms",
                        String.valueOf(System.currentTimeMillis() - pollStart));
                return last;
            }

            if (System.currentTimeMillis() >= deadline) {
                Allure.parameter("cw.poll." + pollLabel + ".promote.attempts", String.valueOf(attempt));
                Allure.parameter("cw.poll." + pollLabel + ".promote.waited.ms", String.valueOf(timeoutMs));
                if (absentGone) {
                    Allure.parameter("cw.poll." + pollLabel + ".promote.note",
                            absentId + " gone but " + presentId + " not yet promoted at timeout");
                    return last;
                }
                Assert.fail(
                        absentId + " was still present in CW list after " + timeoutMs + " ms "
                        + "(attempts=" + attempt + ", foundIds=" + ids + "). "
                        + "Increase vrgo.cw.afteradds.poll.timeout.ms."
                );
            }
            sleepQuietly(intervalMs);
        }
    }

    private Response pollWatchAgainUntilSeriesPresent(
            int limit, int offset, String contentType, boolean isEntitlementEnabled, String seriesId) {
        int timeoutMs = cwPollTimeoutMs();
        int intervalMs = cwPollIntervalMs();
        long pollStart = System.currentTimeMillis();
        long deadline = pollStart + timeoutMs;
        Response last = null;
        int attempt = 0;

        while (true) {
            attempt++;
            last = continueWatchApi.getWatchAgainRaw(limit, offset, contentType, isEntitlementEnabled);
            last.then().statusCode(200).body("status", equalTo(true));
            Set<String> ids = ContinueWatch.cwListContentIds(last);
            if (watchAgainSeriesPresent(ids, last.asString(), seriesId)) {
                Allure.parameter("watchAgain.poll.open.series.attempts", String.valueOf(attempt));
                Allure.parameter("watchAgain.poll.open.series.waited.ms",
                        String.valueOf(System.currentTimeMillis() - pollStart));
                return last;
            }
            if (System.currentTimeMillis() >= deadline) {
                Assert.fail(
                        "Open-series id did not appear in watch-again within " + timeoutMs + " ms "
                        + "(attempts=" + attempt + ", seriesId=" + seriesId + "). "
                        + "Increase vrgo.cw.afteradds.poll.timeout.ms."
                );
            }
            sleepQuietly(intervalMs);
        }
    }

    private static boolean watchAgainSeriesPresent(Set<String> ids, String body, String seriesId) {
        String s = seriesId.strip();
        return ContinueWatch.cwListContainsConfiguredId(ids, s) || bodyContainsId(body, s);
    }

    private Response pollWatchAgainUntilMoviePresent(
            int limit, int offset, String contentType, boolean isEntitlementEnabled, String movieId) {
        int timeoutMs = cwPollTimeoutMs();
        int intervalMs = cwPollIntervalMs();
        long pollStart = System.currentTimeMillis();
        long deadline = pollStart + timeoutMs;
        Response last = null;
        int attempt = 0;

        while (true) {
            attempt++;
            last = continueWatchApi.getWatchAgainRaw(limit, offset, contentType, isEntitlementEnabled);
            last.then().statusCode(200).body("status", equalTo(true));
            Set<String> ids = ContinueWatch.cwListContentIds(last);
            if (watchAgainMoviePresent(ids, last.asString(), movieId)) {
                Allure.parameter("watchAgain.poll.boxset.movie1.attempts", String.valueOf(attempt));
                Allure.parameter("watchAgain.poll.boxset.movie1.waited.ms",
                        String.valueOf(System.currentTimeMillis() - pollStart));
                return last;
            }
            if (System.currentTimeMillis() >= deadline) {
                Assert.fail(
                        "Boxset movie1 did not appear in watch-again within " + timeoutMs + " ms "
                        + "(attempts=" + attempt + ", movie1Id=" + movieId + "). "
                        + "Increase vrgo.cw.afteradds.poll.timeout.ms."
                );
            }
            sleepQuietly(intervalMs);
        }
    }

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

    /**
     * Polls GET CW until EP1 appears (or timeout). Uses the shared poll-timeout properties.
     */
    private Response pollCwUntilEp1Appears(String ep1Id) {
        return pollCwUntilContentIdAppears(ep1Id, "series.ep1");
    }

    // =================== Assertion helpers ===================

    private static void assertRowHasNonZeroProgress(Map<String, Object> row, String label, String contentId) {
        Number watchDuration = extractNumericField(row, "watchDuration", "watch_duration", "position");
        if (watchDuration != null) {
            Assert.assertTrue(
                    watchDuration.doubleValue() > 0,
                    label + " row must have watchDuration > 0 after partial POST; contentId=" + contentId
                            + " watchDuration=" + watchDuration
            );
            return;
        }
        Number progress = extractNumericField(row, "progress");
        if (progress != null) {
            Assert.assertTrue(
                    progress.doubleValue() > 0,
                    label + " row must have progress > 0 after partial POST; contentId=" + contentId
                            + " progress=" + progress
            );
        }
    }

    private static void assertRowHasZeroProgress(Map<String, Object> row, String label, String contentId) {
        Number watchDuration = extractNumericField(row, "watchDuration", "watch_duration", "position");
        if (watchDuration != null) {
            Assert.assertEquals(
                    watchDuration.intValue(), 0,
                    label + " must have watchDuration = 0 (not yet watched); contentId=" + contentId
                            + " watchDuration=" + watchDuration
            );
            return;
        }
        Number progress = extractNumericField(row, "progress");
        if (progress != null) {
            Assert.assertEquals(
                    progress.intValue(), 0,
                    label + " must have progress = 0 (not yet watched); contentId=" + contentId
                            + " progress=" + progress
            );
        }
    }

    /**
     * Asserts EP1 is not shown with {@code watchDuration / progress = 0} in the recent-content response.
     * <ul>
     *   <li>If {@code data} is {@code false} or {@code null} → EP1 is absent from recent CW → acceptable.</li>
     *   <li>If {@code data} is a map with a present progress field → that field must be &gt; 0.</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private static void assertEp1NotPresentWithZeroProgressInRecentResponse(Response r, String ep1Id) {
        Object data = r.jsonPath().get("data");
        String body = r.asString();

        if (data == null || Boolean.FALSE.equals(data)) {
            return;
        }
        if (!(data instanceof Map<?, ?> raw)) {
            return;
        }
        Map<String, Object> dataMap = (Map<String, Object>) raw;

        Number watchDuration = extractNumericField(dataMap, "watchDuration", "watch_duration", "position");
        if (watchDuration != null) {
            Assert.assertNotEquals(
                    watchDuration.intValue(), 0,
                    "EP1 must not appear in recent-content response with watchDuration = 0 after completion; "
                            + "ep1Id=" + ep1Id + " body=" + body
            );
            return;
        }
        Number progress = extractNumericField(dataMap, "progress");
        if (progress != null) {
            Assert.assertNotEquals(
                    progress.intValue(), 0,
                    "EP1 must not appear in recent-content response with progress = 0 after completion; "
                            + "ep1Id=" + ep1Id + " body=" + body
            );
        }
    }

    // =================== Row / field extraction helpers ===================

    private static Map<String, Object> findRowById(List<Map<String, Object>> rows, String contentId) {
        for (Map<String, Object> row : rows) {
            String id = ContinueWatch.cwRowContentId(row);
            if (id != null && ContinueWatch.cwListContainsConfiguredId(Set.of(id.strip()), contentId)) {
                return row;
            }
        }
        return null;
    }

    /**
     * Searches the row and its nested editorial sub-maps for a numeric field matching any of
     * {@code fieldNames}, returning the first match found.
     */
    private static Number extractNumericField(Map<String, Object> row, String... fieldNames) {
        for (Map<String, Object> layer : rowLayers(row)) {
            for (String field : fieldNames) {
                Object v = layer.get(field);
                if (v instanceof Number n) {
                    return n;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rowLayers(Map<String, Object> row) {
        List<Map<String, Object>> layers = new ArrayList<>(4);
        layers.add(row);
        for (String nested : List.of("contentEditorial", "content", "editorial", "metadata")) {
            Object o = row.get(nested);
            if (o instanceof Map<?, ?> m) {
                layers.add((Map<String, Object>) m);
            }
        }
        return layers;
    }

    // =================== cw/v3/progress response parser ===================

    /**
     * Searches the {@code cw/v3/progress} response body for a progress entry matching
     * {@code contentId} and returns its {@code watchDuration} or {@code progress} value.
     *
     * <p>Handles two common response shapes:
     * <ul>
     *   <li><b>List shape</b> — {@code data} is a JSON array; each element is a map with an id
     *       field ({@code contentId}, {@code id}, {@code editorialId}) and a value field
     *       ({@code watchDuration}, {@code progress}).</li>
     *   <li><b>Map shape</b> — {@code data} is a JSON object keyed by contentId; values are
     *       either a number (direct progress) or a nested map containing a value field.</li>
     * </ul>
     *
     * @return the numeric progress/watchDuration for the matching entry, or {@code null} when
     *         the content id is not present in the response (never interacted with — acceptable
     *         for the next episode before it is played).
     */
    @SuppressWarnings("unchecked")
    private static Number findProgressInCwV3Response(Response r, String contentId) {
        Object data = r.jsonPath().get("data");
        if (data == null) {
            return null;
        }
        if (data instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> entry = (Map<String, Object>) raw;
                for (String idKey : List.of("contentId", "id", "editorialId", "assetId")) {
                    Object id = entry.get(idKey);
                    if (id != null && ContinueWatch.cwListContainsConfiguredId(
                            Set.of(String.valueOf(id).strip()), contentId)) {
                        return extractNumericField(entry,
                                "watchDuration", "watch_duration", "progress", "position");
                    }
                }
            }
        } else if (data instanceof Map<?, ?> rawMap) {
            Map<String, Object> dataMap = (Map<String, Object>) rawMap;
            for (Map.Entry<String, Object> e : dataMap.entrySet()) {
                if (!ContinueWatch.cwListContainsConfiguredId(Set.of(e.getKey().strip()), contentId)) {
                    continue;
                }
                Object val = e.getValue();
                if (val instanceof Number n) {
                    return n;
                }
                if (val instanceof Map<?, ?> vm) {
                    return extractNumericField((Map<String, Object>) vm,
                            "watchDuration", "watch_duration", "progress", "position");
                }
            }
        }
        return null;
    }

    // =================== Progress payload builder ===================

    private Map<String, String> buildProgressPayload(List<Map<String, Object>> rows) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String id = ContinueWatch.cwRowContentId(row);
            if (id == null || id.isBlank()) {
                continue;
            }
            String type = ContinueWatch.cwRowContentType(row);
            out.putIfAbsent(id.strip(), resolveProgressApiType(type));
        }
        return out;
    }

    private String resolveProgressApiType(String editorialType) {
        String force = config.getProperty("vrgo.cw.contents.progress.content.type");
        if (force != null && !force.isBlank()) {
            return force.strip();
        }
        if (editorialType == null || editorialType.isBlank()) {
            return "VOD";
        }
        String e = editorialType.strip();
        return (e.equalsIgnoreCase("movie") || e.equalsIgnoreCase("tv_show") || e.equalsIgnoreCase("VOD"))
                ? "VOD" : e;
    }

    // =================== Config / property helpers ===================

    private void requireOpenSeriesPrerequisites() {
        if (continueWatchApi == null) {
            throw new SkipException("Configure vrgo.base.url in environments/<env>.properties to run this test.");
        }
        if (isBlank(System.getenv("VRGO_BEARER_TOKEN")) && isBlank(System.getProperty("vrgo.bearer.token"))) {
            throw new SkipException(
                    "Set BaseTest.VRGO_MANUAL_BEARER_TOKEN, VRGO_BEARER_TOKEN, or -Dvrgo.bearer.token.");
        }
        String seriesId = config.getProperty("vrgo.cw.open.series.id");
        String ep1Id = config.getProperty("vrgo.cw.open.series.ep1.content.id");
        String ep2Id = config.getProperty("vrgo.cw.open.series.ep2.content.id");
        String lastEpId = config.getProperty("vrgo.cw.open.series.last.ep.content.id");
        String totalDurRaw = config.getProperty("vrgo.cw.open.series.ep1.total.duration");
        if (isBlank(seriesId) || seriesId.startsWith("REPLACE")) {
            throw new SkipException(
                    "Set vrgo.cw.open.series.id in environments/<env>.properties for open-series CW tests.");
        }
        if (isBlank(ep1Id) || ep1Id.startsWith("REPLACE")) {
            throw new SkipException(
                    "Set vrgo.cw.open.series.ep1.content.id in environments/<env>.properties for open-series CW tests.");
        }
        if (isBlank(ep2Id) || ep2Id.startsWith("REPLACE")) {
            throw new SkipException(
                    "Set vrgo.cw.open.series.ep2.content.id in environments/<env>.properties for open-series CW tests.");
        }
        if (isBlank(lastEpId) || lastEpId.startsWith("REPLACE")) {
            throw new SkipException(
                    "Set vrgo.cw.open.series.last.ep.content.id in environments/<env>.properties for open-series CW tests.");
        }
        if (isBlank(totalDurRaw) || totalDurRaw.startsWith("REPLACE")) {
            throw new SkipException(
                    "Set vrgo.cw.open.series.ep1.total.duration (total seconds) in environments/<env>.properties.");
        }
    }

    private String openSeriesId() {
        return config.getProperty("vrgo.cw.open.series.id").strip();
    }

    private String openSeriesEp1ContentId() {
        return config.getProperty("vrgo.cw.open.series.ep1.content.id").strip();
    }

    private String openSeriesEp1ContentType() {
        String v = config.getProperty("vrgo.cw.open.series.ep1.content.type");
        return (v == null || v.isBlank()) ? "VOD" : v.strip();
    }

    private int openSeriesEp1TotalDuration() {
        return Integer.parseInt(config.getProperty("vrgo.cw.open.series.ep1.total.duration").strip());
    }

    private int openSeriesEp1CompletedWatchDuration() {
        String v = config.getProperty("vrgo.cw.open.series.ep1.completed.watch.duration");
        if (v != null && !v.isBlank()) {
            return Integer.parseInt(v.strip());
        }
        return openSeriesEp1TotalDuration();
    }

    private String openSeriesEp2ContentId() {
        return config.getProperty("vrgo.cw.open.series.ep2.content.id").strip();
    }

    private String openSeriesEp2ContentType() {
        String v = config.getProperty("vrgo.cw.open.series.ep2.content.type");
        return (v == null || v.isBlank()) ? openSeriesEp1ContentType() : v.strip();
    }

    private String openSeriesLastEpContentId() {
        return config.getProperty("vrgo.cw.open.series.last.ep.content.id").strip();
    }

    private String openSeriesLastEpContentType() {
        String v = config.getProperty("vrgo.cw.open.series.last.ep.content.type");
        return (v == null || v.isBlank()) ? openSeriesEp1ContentType() : v.strip();
    }

    private int openSeriesLastEpCompletedWatchDuration() {
        String v = config.getProperty("vrgo.cw.open.series.last.ep.completed.watch.duration");
        if (v != null && !v.isBlank()) {
            return Integer.parseInt(v.strip());
        }
        return openSeriesEp1TotalDuration();
    }

    private void requireBoxsetMoviesPrerequisites() {
        if (continueWatchApi == null) {
            throw new SkipException("Configure vrgo.base.url in environments/<env>.properties to run this test.");
        }
        if (isBlank(System.getenv("VRGO_BEARER_TOKEN")) && isBlank(System.getProperty("vrgo.bearer.token"))) {
            throw new SkipException(
                    "Set BaseTest.VRGO_MANUAL_BEARER_TOKEN, VRGO_BEARER_TOKEN, or -Dvrgo.bearer.token.");
        }
        String boxsetId = config.getProperty("vrgo.cw.boxset.movies.boxset.id");
        String movie1Id = config.getProperty("vrgo.cw.boxset.movies.movie1.content.id");
        String movie2Id = config.getProperty("vrgo.cw.boxset.movies.movie2.content.id");
        if (isBlank(boxsetId) || boxsetId.startsWith("REPLACE")) {
            throw new SkipException(
                    "Set vrgo.cw.boxset.movies.boxset.id in environments/<env>.properties for boxset-movies CW tests.");
        }
        if (isBlank(movie1Id) || movie1Id.startsWith("REPLACE")) {
            throw new SkipException(
                    "Set vrgo.cw.boxset.movies.movie1.content.id in environments/<env>.properties for boxset-movies CW tests.");
        }
        if (isBlank(movie2Id) || movie2Id.startsWith("REPLACE")) {
            throw new SkipException(
                    "Set vrgo.cw.boxset.movies.movie2.content.id in environments/<env>.properties for boxset-movies CW tests.");
        }
    }

    private String boxsetId() {
        return config.getProperty("vrgo.cw.boxset.movies.boxset.id").strip();
    }

    private String boxsetMovie1ContentId() {
        return config.getProperty("vrgo.cw.boxset.movies.movie1.content.id").strip();
    }

    private String boxsetMovie1ContentType() {
        String v = config.getProperty("vrgo.cw.boxset.movies.movie1.content.type");
        return (v == null || v.isBlank()) ? "VOD" : v.strip();
    }

    private String boxsetMovie2ContentId() {
        return config.getProperty("vrgo.cw.boxset.movies.movie2.content.id").strip();
    }

    private String boxsetMovie2ContentType() {
        String v = config.getProperty("vrgo.cw.boxset.movies.movie2.content.type");
        return (v == null || v.isBlank()) ? boxsetMovie1ContentType() : v.strip();
    }

    private int boxsetMovie1PartialWatchDuration() {
        String v = config.getProperty("vrgo.cw.boxset.movies.movie1.partial.watch.duration");
        if (v != null && !v.isBlank()) {
            return Integer.parseInt(v.strip());
        }
        String fallback = config.getProperty("vrgo.cw.add.movie.watch.duration");
        if (fallback != null && !fallback.isBlank()) {
            return Integer.parseInt(fallback.strip());
        }
        return 500;
    }

    private int boxsetMovie1CompletedWatchDuration() {
        String v = config.getProperty("vrgo.cw.boxset.movies.movie1.completed.watch.duration");
        if (v != null && !v.isBlank()) {
            return Integer.parseInt(v.strip());
        }
        String fallback = config.getProperty("vrgo.cw.add.movie.watch.duration");
        if (fallback != null && !fallback.isBlank()) {
            return Integer.parseInt(fallback.strip());
        }
        return 1000;
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

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.strip();
        }
        return b;
    }

    private void requireSeriesEpPrerequisites() {
        if (continueWatchApi == null) {
            throw new SkipException("Configure vrgo.base.url in environments/<env>.properties to run this test.");
        }
        if (isBlank(System.getenv("VRGO_BEARER_TOKEN")) && isBlank(System.getProperty("vrgo.bearer.token"))) {
            throw new SkipException(
                    "Set BaseTest.VRGO_MANUAL_BEARER_TOKEN, VRGO_BEARER_TOKEN, or -Dvrgo.bearer.token.");
        }
        String ep1Id      = config.getProperty("vrgo.cw.series.ep1.content.id");
        String ep2Id      = config.getProperty("vrgo.cw.series.ep2.content.id");
        String totalDurRaw = config.getProperty("vrgo.cw.series.ep1.total.duration");
        if (isBlank(ep1Id) || ep1Id.startsWith("REPLACE")) {
            throw new SkipException(
                    "Set vrgo.cw.series.ep1.content.id in environments/<env>.properties to run series-episode CW tests.");
        }
        if (isBlank(ep2Id) || ep2Id.startsWith("REPLACE")) {
            throw new SkipException(
                    "Set vrgo.cw.series.ep2.content.id in environments/<env>.properties to run series-episode CW tests.");
        }
        if (isBlank(totalDurRaw) || totalDurRaw.startsWith("REPLACE")) {
            throw new SkipException(
                    "Set vrgo.cw.series.ep1.total.duration (total seconds) in environments/<env>.properties.");
        }
        String seriesId = config.getProperty("vrgo.cw.series.id");
        if (isBlank(seriesId) || seriesId.startsWith("REPLACE")) {
            throw new SkipException(
                    "Set vrgo.cw.series.id in environments/<env>.properties to run series-episode CW tests.");
        }
    }

    private String ep1ContentId() {
        return config.getProperty("vrgo.cw.series.ep1.content.id").strip();
    }

    private String ep1ContentType() {
        String v = config.getProperty("vrgo.cw.series.ep1.content.type");
        return (v == null || v.isBlank()) ? "VOD" : v.strip();
    }

    private int ep1TotalDuration() {
        return Integer.parseInt(config.getProperty("vrgo.cw.series.ep1.total.duration").strip());
    }

    private int resolvePartialDuration(int totalDuration) {
        String v = config.getProperty("vrgo.cw.series.ep1.partial.watch.duration");
        if (v != null && !v.isBlank()) {
            return Integer.parseInt(v.strip());
        }
        return (int) (totalDuration * 0.50);
    }

    private int resolveCompletedDuration(int totalDuration) {
        String v = config.getProperty("vrgo.cw.series.ep1.complete.watch.duration");
        if (v != null && !v.isBlank()) {
            return Integer.parseInt(v.strip());
        }
        return (int) Math.ceil(totalDuration * COMPLETION_THRESHOLD);
    }

    private String ep2ContentId() {
        return config.getProperty("vrgo.cw.series.ep2.content.id").strip();
    }

    private String ep2ContentType() {
        String v = config.getProperty("vrgo.cw.series.ep2.content.type");
        return (v == null || v.isBlank()) ? ep1ContentType() : v.strip();
    }

    private String seriesId() {
        return config.getProperty("vrgo.cw.series.id").strip();
    }

    private String subscriberId() {
        return config.getProperty("vrgo.header.cp_id");
    }

    private String regionForRecentLookup() {
        String v = config.getProperty("vrgo.cw.recent.content.region");
        return (v == null || v.isBlank()) ? "Malaysia" : v.strip();
    }

    private String regionForProgress() {
        String v = config.getProperty("vrgo.cw.contents.progress.region");
        return (v != null && !v.isBlank()) ? v.strip() : regionForRecentLookup();
    }

    private int cwPollTimeoutMs() {
        String v = config.getProperty("vrgo.cw.afteradds.poll.timeout.ms");
        if (v == null || v.isBlank()) return 30_000;
        try { return Integer.parseInt(v.strip()); } catch (NumberFormatException e) { return 30_000; }
    }

    private int cwPollIntervalMs() {
        String v = config.getProperty("vrgo.cw.afteradds.poll.interval.ms");
        if (v == null || v.isBlank()) return 750;
        try { return Integer.parseInt(v.strip()); } catch (NumberFormatException e) { return 750; }
    }

    /** Rounds to 2 decimal places so cw/v3/progress percentage values match API display precision. */
    private static double roundTo2Decimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static void sleepQuietly(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SkipException("Interrupted while polling CW for series-episode scenario");
        }
    }
}
