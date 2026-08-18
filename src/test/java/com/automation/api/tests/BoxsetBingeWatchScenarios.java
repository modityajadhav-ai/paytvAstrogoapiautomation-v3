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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * Boxset binge-watch <strong>single continuous flow</strong> across dual boxsets (shared M1/T1 + unique M2/T2/M3/T3).
 *
 * <p>Property prefix {@code vrgo.cw.boxset.binge.*} — distinct from {@code vrgo.cw.boxset.movies.*} in
 * {@link ContinueWatchScenarios}.</p>
 *
 * <h3>Flow (one DELETE at start; state carries forward)</h3>
 * <ol>
 *   <li>GET CW → DELETE every item on the list</li>
 *   <li>POST M1 partial (&lt; 97%)</li>
 *   <li>GET CW — M1 present only</li>
 *   <li>POST M1 {@code hasCompletedPlayBack=true}</li>
 *   <li>GET CW — M1 absent, T1 present (shared once); GET WA — M1 present</li>
 *   <li>POST T1 {@code hasCompletedPlayBack=true}</li>
 *   <li>GET WA — M1 and T1 present</li>
 *   <li>GET CW — M2 and M3 present (next siblings after T1)</li>
 *   <li>POST M2 complete → M2 absent from CW, M2 in WA</li>
 *   <li>GET CW — T2 present, T3 absent</li>
 *   <li>POST M3 complete → M3 absent from CW, T3 in CW, M3 in WA</li>
 *   <li>POST T2 complete → T2 absent from CW, T2 in WA</li>
 *   <li>POST T3 complete → T3 absent from CW, T3 in WA</li>
 * </ol>
 *
 * <p>Run: {@code scripts/run-boxset-binge-tests.bat} or
 * {@code mvn clean test -Ptest -Dsurefire.suiteXmlFiles=src/test/resources/testng-boxset-binge.xml}</p>
 */
@Feature("Continue watch")
public class BoxsetBingeWatchScenarios extends BaseTest {

    // =================== Continuous boxset binge flow (steps 01–13) ===================

    @Test(priority = 1, description = "Step 1 — GET continue-watch and DELETE each item on the list")
    @Story("DELETE /subscriber-event-service/v3/continue-watch/ (initial clean slate)")
    public void step01_getContinueWatchAndDeleteAllItems() {
        requireBoxsetBingePrerequisites();
        deleteAllCwItemsInternal("boxset.binge.flow.step01");
    }

    @Test(
            priority = 2,
            description = "Step 2 — POST shared movie M1 partial watch below 97% (hasCompletedPlayBack=false)",
            dependsOnMethods = "step01_getContinueWatchAndDeleteAllItems"
    )
    @Story("POST /subscriber-activity-producer/v3/subscriber-continue-watch (M1 partial)")
    public void step02_addSharedMovieM1PartialBelow96Percent() {
        requireBoxsetBingePrerequisites();
        postContinueWatch(false, sharedMovieM1ContentId(), movieContentType(),
                sharedMovieM1PartialWatchDuration(), "step02-m1-partial");
    }

    @Test(
            priority = 3,
            description = "Step 3 — GET CW: shared movie M1 (39889e62…) must be present",
            dependsOnMethods = "step02_addSharedMovieM1PartialBelow96Percent"
    )
    @Story("GET /subscriber-event-service/v3/continue-watch/continue (M1 partial)")
    public void step03_getContinueWatch_m1PresentOnly() {
        requireBoxsetBingePrerequisites();
        String m1Id = sharedMovieM1ContentId();
        Response r = pollCwUntilContentIdAppears(m1Id, "step03.m1");
        Set<String> allIds = ContinueWatch.cwListContentIds(r);
        assertAllPresent(allIds, Set.of(m1Id), "M1 must be in CW after partial POST");
        assertNonePresent(allIds, allOtherBingeContentIdsExcept(m1Id),
                "No other boxset binge content in CW after partial M1");
    }

    @Test(
            priority = 4,
            description = "Step 4 — POST shared movie M1 with hasCompletedPlayBack=true",
            dependsOnMethods = "step03_getContinueWatch_m1PresentOnly"
    )
    @Story("POST /subscriber-activity-producer/v3/subscriber-continue-watch (M1 completed)")
    public void step04_addSharedMovieM1CompletedHasCompletedPlayBackTrue() {
        requireBoxsetBingePrerequisites();
        postContinueWatch(true, sharedMovieM1ContentId(), movieContentType(),
                sharedMovieM1CompletedWatchDuration(), "step04-m1-completed");
    }

    @Test(
            priority = 5,
            description = "Step 5 — GET CW: M1 absent, T1 present (shared once); GET WA: M1 present",
            dependsOnMethods = "step04_addSharedMovieM1CompletedHasCompletedPlayBackTrue"
    )
    @Story("GET continue-watch + watch-again (after M1 completion)")
    public void step05_getCw_m1AbsentT1Present_getWa_m1Present() {
        requireBoxsetBingePrerequisites();
        String m1Id = sharedMovieM1ContentId();
        String t1Id = sharedTvT1EpisodeContentId();

        Response cw = pollCwUntilAbsentAndAllPresent(m1Id, Set.of(t1Id), "step05.cw");
        Set<String> cwIds = ContinueWatch.cwListContentIds(cw);
        assertNonePresent(cwIds, Set.of(
                boxset1UniqueMovieM2ContentId(),
                boxset1UniqueTvT2EpisodeContentId(),
                boxset2UniqueMovieM3ContentId(),
                boxset2UniqueTvT3EpisodeContentId()
        ), "Only immediate next sibling T1 should be promoted after M1 completes");
        assertNoDuplicateContentId(ContinueWatch.cwListRows(cw), t1Id,
                "Shared T1 must appear once in CW (boxset1 + boxset2 deduped)");

        Response wa = pollWatchAgainUntilContentPresent(m1Id, "step05.wa.m1", true);
        wa.then().statusCode(200).body("status", equalTo(true));
        Assert.assertTrue(
                watchAgainContentPresent(ContinueWatch.cwListContentIds(wa), wa.asString(), m1Id),
                "M1 must appear in watch-again after completion; m1Id=" + m1Id
        );
    }

    @Test(
            priority = 6,
            description = "Step 6 — POST shared TV T1 with hasCompletedPlayBack=true",
            dependsOnMethods = "step05_getCw_m1AbsentT1Present_getWa_m1Present"
    )
    @Story("POST /subscriber-activity-producer/v3/subscriber-continue-watch (T1 completed)")
    public void step06_addSharedTvT1CompletedHasCompletedPlayBackTrue() {
        requireBoxsetBingePrerequisites();
        postContinueWatch(true, sharedTvT1EpisodeContentId(), tvContentType(),
                sharedTvT1CompletedWatchDuration(), "step06-t1-completed");
    }

    @Test(
            priority = 7,
            description = "Step 7 — GET WA: M1 and T1 episode must both be present",
            dependsOnMethods = "step06_addSharedTvT1CompletedHasCompletedPlayBackTrue"
    )
    @Story("GET /subscriber-event-service/v3/watch-again (M1 + T1 after T1 completion)")
    public void step07_getWatchAgain_m1AndT1Present() {
        requireBoxsetBingePrerequisites();
        String m1Id = sharedMovieM1ContentId();
        String t1Id = sharedTvT1EpisodeContentId();

        Response wa = pollWatchAgainUntilAllPresent(Set.of(m1Id, t1Id), "step07.wa");
        wa.then().statusCode(200).body("status", equalTo(true));
        Set<String> waIds = ContinueWatch.cwListContentIds(wa);
        String body = wa.asString();
        Assert.assertTrue(watchAgainContentPresent(waIds, body, m1Id),
                "M1 must remain in WA after T1 completion; m1Id=" + m1Id);
        Assert.assertTrue(watchAgainContentPresent(waIds, body, t1Id),
                "T1 episode must appear in WA after completion; t1Id=" + t1Id);
    }

    @Test(
            priority = 8,
            description = "Step 8 — GET CW: M2 (boxset1) and M3 (boxset2) present; T1 absent",
            dependsOnMethods = "step07_getWatchAgain_m1AndT1Present"
    )
    @Story("GET /subscriber-event-service/v3/continue-watch/continue (M2+M3 after T1 completion)")
    public void step08_getContinueWatch_m2AndM3Present_t1Absent() {
        requireBoxsetBingePrerequisites();
        String t1Id = sharedTvT1EpisodeContentId();
        String m2Id = boxset1UniqueMovieM2ContentId();
        String m3Id = boxset2UniqueMovieM3ContentId();

        Response cw = pollCwUntilAbsentAndAllPresent(t1Id, Set.of(m2Id, m3Id), "step08.cw");
        Set<String> cwIds = ContinueWatch.cwListContentIds(cw);
        assertNonePresent(cwIds, Set.of(
                sharedMovieM1ContentId(),
                boxset1UniqueTvT2EpisodeContentId(),
                boxset2UniqueTvT3EpisodeContentId()
        ), "Only M2 and M3 should be promoted after shared T1 completes");
    }

    @Test(
            priority = 9,
            description = "Step 9 — POST M2 complete; GET CW: M2 absent; GET WA: M2 present",
            dependsOnMethods = "step08_getContinueWatch_m2AndM3Present_t1Absent"
    )
    @Story("POST + GET continue-watch / watch-again (M2 completion)")
    public void step09_addM2Completed_getCw_m2Absent_getWa_m2Present() {
        requireBoxsetBingePrerequisites();
        String m2Id = boxset1UniqueMovieM2ContentId();
        postContinueWatch(true, m2Id, movieContentType(),
                boxset1UniqueMovieM2CompletedWatchDuration(), "step09-m2-completed");

        Response cw = pollCwUntilContentIdAbsent(m2Id, "step09.cw.m2.absent");
        Assert.assertFalse(
                ContinueWatch.cwListContainsConfiguredId(ContinueWatch.cwListContentIds(cw), m2Id),
                "M2 must be removed from CW after completion"
        );

        Response wa = pollWatchAgainUntilContentPresent(m2Id, "step09.wa.m2", true);
        Assert.assertTrue(
                watchAgainContentPresent(ContinueWatch.cwListContentIds(wa), wa.asString(), m2Id),
                "M2 must appear in WA after completion; m2Id=" + m2Id
        );
    }

    @Test(
            priority = 10,
            description = "Step 10 — GET CW: T2 (boxset1) present; T3 (boxset2) absent",
            dependsOnMethods = "step09_addM2Completed_getCw_m2Absent_getWa_m2Present"
    )
    @Story("GET /subscriber-event-service/v3/continue-watch/continue (T2 promoted after M2)")
    public void step10_getContinueWatch_t2Present_t3Absent() {
        requireBoxsetBingePrerequisites();
        String t2Id = boxset1UniqueTvT2EpisodeContentId();
        String t3Id = boxset2UniqueTvT3EpisodeContentId();

        Response cw = pollCwUntilContentIdAppears(t2Id, "step10.cw.t2");
        Set<String> cwIds = ContinueWatch.cwListContentIds(cw);
        assertAllPresent(cwIds, Set.of(t2Id), "T2 must be in CW as next sibling after M2 (boxset1)");
        Assert.assertFalse(
                ContinueWatch.cwListContainsConfiguredId(cwIds, t3Id),
                "T3 must NOT be in CW when only boxset1 unique M2 completed; t3Id=" + t3Id
        );
    }

    @Test(
            priority = 11,
            description = "Step 11 — POST M3 complete; GET CW: M3 absent, T3 present; GET WA: M3 present",
            dependsOnMethods = "step10_getContinueWatch_t2Present_t3Absent"
    )
    @Story("POST + GET continue-watch / watch-again (M3 completion)")
    public void step11_addM3Completed_getCw_m3AbsentT3Present_getWa_m3Present() {
        requireBoxsetBingePrerequisites();
        String m3Id = boxset2UniqueMovieM3ContentId();
        String t3Id = boxset2UniqueTvT3EpisodeContentId();
        postContinueWatch(true, m3Id, movieContentType(),
                boxset2UniqueMovieM3CompletedWatchDuration(), "step11-m3-completed");

        Response cw = pollCwUntilAbsentAndAllPresent(m3Id, Set.of(t3Id), "step11.cw");
        Set<String> cwIds = ContinueWatch.cwListContentIds(cw);
        assertAllPresent(cwIds, Set.of(t3Id), "T3 must be promoted after M3 (boxset2) completes");

        Response wa = pollWatchAgainUntilContentPresent(m3Id, "step11.wa.m3", true);
        Assert.assertTrue(
                watchAgainContentPresent(ContinueWatch.cwListContentIds(wa), wa.asString(), m3Id),
                "M3 must appear in WA after completion"
        );
    }

    @Test(
            priority = 12,
            description = "Step 12 — POST T2 complete; GET CW: T2 absent; GET WA: T2 episode present",
            dependsOnMethods = "step11_addM3Completed_getCw_m3AbsentT3Present_getWa_m3Present"
    )
    @Story("POST + GET continue-watch / watch-again (T2 completion)")
    public void step12_addT2Completed_getCw_t2Absent_getWa_t2Present() {
        requireBoxsetBingePrerequisites();
        String t2Id = boxset1UniqueTvT2EpisodeContentId();
        postContinueWatch(true, t2Id, tvContentType(),
                boxset1UniqueTvT2CompletedWatchDuration(), "step12-t2-completed");

        Response cw = pollCwUntilContentIdAbsent(t2Id, "step12.cw.t2.absent");
        Assert.assertFalse(
                ContinueWatch.cwListContainsConfiguredId(ContinueWatch.cwListContentIds(cw), t2Id),
                "T2 must be removed from CW after completion"
        );

        Response wa = pollWatchAgainUntilContentPresent(t2Id, "step12.wa.t2", false);
        Assert.assertTrue(
                watchAgainContentPresent(ContinueWatch.cwListContentIds(wa), wa.asString(), t2Id),
                "T2 episode must appear in WA after completion"
        );
    }

    @Test(
            priority = 13,
            description = "Step 13 — POST T3 complete; GET CW: T3 absent; GET WA: T3 episode present",
            dependsOnMethods = "step12_addT2Completed_getCw_t2Absent_getWa_t2Present"
    )
    @Story("POST + GET continue-watch / watch-again (T3 completion)")
    public void step13_addT3Completed_getCw_t3Absent_getWa_t3Present() {
        requireBoxsetBingePrerequisites();
        String t3Id = boxset2UniqueTvT3EpisodeContentId();
        postContinueWatch(true, t3Id, tvContentType(),
                boxset2UniqueTvT3CompletedWatchDuration(), "step13-t3-completed");

        Response cw = pollCwUntilContentIdAbsent(t3Id, "step13.cw.t3.absent");
        Assert.assertFalse(
                ContinueWatch.cwListContainsConfiguredId(ContinueWatch.cwListContentIds(cw), t3Id),
                "T3 must be removed from CW after completion (last boxset2 child)"
        );

        Response wa = pollWatchAgainUntilContentPresent(t3Id, "step13.wa.t3", false);
        Assert.assertTrue(
                watchAgainContentPresent(ContinueWatch.cwListContentIds(wa), wa.asString(), t3Id),
                "T3 episode must appear in WA after completion"
        );
    }

    // =================== API smoke (after flow) ===================

    @Test(
            priority = 14,
            description = "GET content-detail boxset binge for boxset1",
            dependsOnMethods = "step13_addT3Completed_getCw_t3Absent_getWa_t3Present"
    )
    @Story("GET /content-detail-service/pub/v1/boxset/{boxsetId}/binge (boxset1)")
    public void step14_getBoxsetBingeApi_boxset1_returns200() {
        requireBoxsetBingePrerequisites();
        if (contentDetailApi == null) {
            throw new SkipException("Set vrgo.base.url for content-detail boxset binge API.");
        }
        Response r = contentDetailApi.getBoxsetBingeRaw(boxset1Id());
        AllureAttachmentUtils.attachJson("boxset-binge-api-boxset1", r.asString());
        r.then().statusCode(200).body("status", equalTo(true));
    }

    @Test(
            priority = 15,
            description = "GET content-detail boxset binge for boxset2",
            dependsOnMethods = "step14_getBoxsetBingeApi_boxset1_returns200"
    )
    @Story("GET /content-detail-service/pub/v1/boxset/{boxsetId}/binge (boxset2)")
    public void step15_getBoxsetBingeApi_boxset2_returns200() {
        requireBoxsetBingePrerequisites();
        if (contentDetailApi == null) {
            throw new SkipException("Set vrgo.base.url for content-detail boxset binge API.");
        }
        Response r = contentDetailApi.getBoxsetBingeRaw(boxset2Id());
        AllureAttachmentUtils.attachJson("boxset-binge-api-boxset2", r.asString());
        r.then().statusCode(200).body("status", equalTo(true));
    }

    @Test(
            priority = 16,
            description = "GET continue-watch/content/boxset/{boxsetId} for boxset1",
            dependsOnMethods = "step15_getBoxsetBingeApi_boxset2_returns200"
    )
    @Story("GET /subscriber-event-service/v3/continue-watch/content/boxset/{boxsetId}")
    public void step16_getContinueWatchBoxsetContent_boxset1_returns200() {
        requireBoxsetBingePrerequisites();
        String boxsetId = boxset1Id();
        Response r = continueWatchApi.getContinueWatchBoxsetContentRaw(boxsetId);
        AllureAttachmentUtils.attachJson("boxset-binge-cw-boxset1-" + boxsetId, r.asString());
        r.then().statusCode(200).body("status", equalTo(true)).body("data", org.hamcrest.Matchers.notNullValue());
    }

    // =================== POST helper ===================

    private void postContinueWatch(
            boolean hasCompletedPlayBack,
            String contentId,
            String contentType,
            int watchDuration,
            String attachmentLabel
    ) {
        Allure.parameter("environment", Environment.current().name());
        Allure.parameter("cw.boxset.binge.content.id", contentId);
        Allure.parameter("cw.boxset.binge.hasCompletedPlayBack", String.valueOf(hasCompletedPlayBack));
        Allure.parameter("cw.boxset.binge.watch.duration.s", String.valueOf(watchDuration));

        SubscriberContinueWatchRequest body = new SubscriberContinueWatchRequest(
                contentId, contentType, watchDuration, subscriberId());
        AllureAttachmentUtils.attachJson("subscriber-continue-watch-" + attachmentLabel, JsonUtils.toJson(body));

        Response r = continueWatchApi.addSubscriberContinueWatchRaw(hasCompletedPlayBack, body);
        r.then().statusCode(200);
    }

    // =================== Cleanup helpers ===================

    private Set<String> allConfiguredContentIds() {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(sharedMovieM1ContentId());
        ids.add(sharedTvT1EpisodeContentId());
        ids.add(boxset1UniqueMovieM2ContentId());
        ids.add(boxset1UniqueTvT2EpisodeContentId());
        ids.add(boxset2UniqueMovieM3ContentId());
        ids.add(boxset2UniqueTvT3EpisodeContentId());
        return ids;
    }

    private Set<String> allOtherBingeContentIdsExcept(String... keepIds) {
        Set<String> keep = Set.of(keepIds);
        Set<String> out = new LinkedHashSet<>();
        for (String id : allConfiguredContentIds()) {
            if (!keep.contains(id)) {
                out.add(id);
            }
        }
        return out;
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
            String contentType = ContinueWatch.cwRowContentType(row);
            Response del = continueWatchApi.deleteContinueWatchItemRaw(id.strip(), contentType);
            del.then().statusCode(anyOf(is(200), is(204)));
        }

        Allure.parameter("cw.cleanup.deleted", String.valueOf(deleted.size()));
        Allure.parameter("cw.cleanup.ids", String.join(", ", deleted));
    }

    // =================== Polling helpers ===================

    private Response pollCwUntilContentIdAppears(String contentId, String pollLabel) {
        int timeoutMs = cwPollTimeoutMs();
        int intervalMs = cwPollIntervalMs();
        long deadline = System.currentTimeMillis() + timeoutMs;
        Response last = null;
        int attempt = 0;
        while (true) {
            attempt++;
            last = continueWatchApi.getContinueWatchRaw(20, 0, false);
            last.then().statusCode(200).body("status", equalTo(true));
            Set<String> ids = ContinueWatch.cwListContentIds(last);
            if (ContinueWatch.cwListContainsConfiguredId(ids, contentId)) {
                Allure.parameter("cw.poll." + pollLabel + ".attempts", String.valueOf(attempt));
                return last;
            }
            if (System.currentTimeMillis() >= deadline) {
                Assert.fail(contentId + " did not appear in CW within " + timeoutMs + " ms; foundIds=" + ids);
            }
            sleepQuietly(intervalMs);
        }
    }

    private Response pollCwUntilContentIdAbsent(String contentId, String pollLabel) {
        int timeoutMs = cwPollTimeoutMs();
        int intervalMs = cwPollIntervalMs();
        long deadline = System.currentTimeMillis() + timeoutMs;
        Response last = null;
        int attempt = 0;
        while (true) {
            attempt++;
            last = continueWatchApi.getContinueWatchRaw(20, 0, false);
            last.then().statusCode(200).body("status", equalTo(true));
            Set<String> ids = ContinueWatch.cwListContentIds(last);
            if (!ContinueWatch.cwListContainsConfiguredId(ids, contentId)) {
                Allure.parameter("cw.poll." + pollLabel + ".attempts", String.valueOf(attempt));
                return last;
            }
            if (System.currentTimeMillis() >= deadline) {
                Assert.fail(contentId + " still in CW after " + timeoutMs + " ms; foundIds=" + ids);
            }
            sleepQuietly(intervalMs);
        }
    }

    private Response pollCwUntilAbsentAndAllPresent(
            String absentId, Set<String> mustBePresent, String pollLabel) {
        int timeoutMs = cwPollTimeoutMs();
        int intervalMs = cwPollIntervalMs();
        long deadline = System.currentTimeMillis() + timeoutMs;
        Response last = null;
        int attempt = 0;
        while (true) {
            attempt++;
            last = continueWatchApi.getContinueWatchRaw(20, 0, false);
            last.then().statusCode(200).body("status", equalTo(true));
            Set<String> ids = ContinueWatch.cwListContentIds(last);
            boolean absent = !ContinueWatch.cwListContainsConfiguredId(ids, absentId);
            boolean allPresent = mustBePresent.stream()
                    .allMatch(id -> ContinueWatch.cwListContainsConfiguredId(ids, id));
            if (absent && allPresent) {
                Allure.parameter("cw.poll." + pollLabel + ".attempts", String.valueOf(attempt));
                return last;
            }
            if (System.currentTimeMillis() >= deadline) {
                Set<String> missing = new LinkedHashSet<>();
                for (String id : mustBePresent) {
                    if (!ContinueWatch.cwListContainsConfiguredId(ids, id)) {
                        missing.add(id);
                    }
                }
                Assert.fail(
                        "CW poll timeout for " + pollLabel + "; absentId=" + absentId
                                + " absent=" + absent + " missing=" + missing + " foundIds=" + ids
                );
            }
            sleepQuietly(intervalMs);
        }
    }

    private Response pollWatchAgainUntilContentPresent(String contentId, String pollLabel, boolean isMovie) {
        int limit = readIntProperty("vrgo.watch.again.limit", 20);
        int offset = readIntProperty("vrgo.watch.again.offset", 0);
        String contentType = firstNonBlank(config.getProperty("vrgo.watch.again.content.type"), "VOD");
        boolean ent = readBooleanProperty("vrgo.watch.again.is.entitlement.enabled", false);

        int timeoutMs = cwPollTimeoutMs();
        int intervalMs = cwPollIntervalMs();
        long deadline = System.currentTimeMillis() + timeoutMs;
        Response last = null;
        int attempt = 0;
        while (true) {
            attempt++;
            last = continueWatchApi.getWatchAgainRaw(limit, offset, contentType, ent);
            last.then().statusCode(200).body("status", equalTo(true));
            Set<String> ids = ContinueWatch.cwListContentIds(last);
            if (watchAgainContentPresent(ids, last.asString(), contentId)) {
                Allure.parameter("watchAgain.poll." + pollLabel + ".attempts", String.valueOf(attempt));
                return last;
            }
            if (System.currentTimeMillis() >= deadline) {
                Assert.fail(
                        "Watch-again timeout for " + contentId + " (isMovie=" + isMovie + "); ids=" + ids
                );
            }
            sleepQuietly(intervalMs);
        }
    }

    private Response pollWatchAgainUntilAllPresent(Set<String> contentIds, String pollLabel) {
        int limit = readIntProperty("vrgo.watch.again.limit", 20);
        int offset = readIntProperty("vrgo.watch.again.offset", 0);
        String contentType = firstNonBlank(config.getProperty("vrgo.watch.again.content.type"), "VOD");
        boolean ent = readBooleanProperty("vrgo.watch.again.is.entitlement.enabled", false);

        int timeoutMs = cwPollTimeoutMs();
        int intervalMs = cwPollIntervalMs();
        long deadline = System.currentTimeMillis() + timeoutMs;
        Response last = null;
        int attempt = 0;
        while (true) {
            attempt++;
            last = continueWatchApi.getWatchAgainRaw(limit, offset, contentType, ent);
            last.then().statusCode(200).body("status", equalTo(true));
            Set<String> ids = ContinueWatch.cwListContentIds(last);
            String body = last.asString();
            boolean allFound = contentIds.stream()
                    .allMatch(id -> watchAgainContentPresent(ids, body, id));
            if (allFound) {
                Allure.parameter("watchAgain.poll." + pollLabel + ".attempts", String.valueOf(attempt));
                return last;
            }
            if (System.currentTimeMillis() >= deadline) {
                Assert.fail("Watch-again timeout waiting for all ids " + contentIds + "; found=" + ids);
            }
            sleepQuietly(intervalMs);
        }
    }

    private static boolean watchAgainContentPresent(Set<String> ids, String body, String contentId) {
        if (ContinueWatch.cwListContainsConfiguredId(ids, contentId) || bodyContainsId(body, contentId)) {
            return true;
        }
        String tail = stripMovEditorialPrefix(contentId);
        return !tail.equals(contentId.strip())
                && (ContinueWatch.cwListContainsConfiguredId(ids, tail) || bodyContainsId(body, tail));
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
        return id != null && !id.isEmpty() && body != null && body.contains(id);
    }

    // =================== Assertion helpers ===================

    private static void assertNonePresent(Set<String> actual, Set<String> mustBeAbsent, String message) {
        List<String> found = new ArrayList<>();
        for (String id : mustBeAbsent) {
            if (ContinueWatch.cwListContainsConfiguredId(actual, id)) {
                found.add(id);
            }
        }
        Assert.assertTrue(found.isEmpty(), message + "; unexpectedly present: " + found + " in " + actual);
    }

    private static void assertAllPresent(Set<String> actual, Set<String> mustBePresent, String message) {
        List<String> missing = new ArrayList<>();
        for (String id : mustBePresent) {
            if (!ContinueWatch.cwListContainsConfiguredId(actual, id)) {
                missing.add(id);
            }
        }
        Assert.assertTrue(missing.isEmpty(), message + "; missing: " + missing + " in " + actual);
    }

    private static void assertNoDuplicateContentId(List<Map<String, Object>> rows, String contentId, String message) {
        int count = 0;
        for (Map<String, Object> row : rows) {
            String id = ContinueWatch.cwRowContentId(row);
            if (id != null && ContinueWatch.cwListContainsConfiguredId(Set.of(id.strip()), contentId)) {
                count++;
            }
        }
        Assert.assertTrue(count <= 1, message + "; duplicate count=" + count + " for id=" + contentId);
    }

    // =================== Config / property accessors ===================

    private void requireBoxsetBingePrerequisites() {
        if (continueWatchApi == null) {
            throw new SkipException("Configure vrgo.base.url in environments/<env>.properties.");
        }
        if (!isVrgoAuthConfigured()) {
            throw new SkipException(VRGO_AUTH_SKIP_MESSAGE);
        }
        requireConfiguredId("vrgo.cw.boxset.binge.boxset1.id", boxset1Id());
        requireConfiguredId("vrgo.cw.boxset.binge.boxset2.id", boxset2Id());
        requireConfiguredId("vrgo.cw.boxset.binge.shared.movie.m1.content.id", sharedMovieM1ContentId());
        requireConfiguredId("vrgo.cw.boxset.binge.shared.tv.t1.episode.content.id", sharedTvT1EpisodeContentId());
        requireConfiguredId("vrgo.cw.boxset.binge.boxset1.unique.movie.m2.content.id", boxset1UniqueMovieM2ContentId());
        requireConfiguredId("vrgo.cw.boxset.binge.boxset1.unique.tv.t2.episode.content.id", boxset1UniqueTvT2EpisodeContentId());
        requireConfiguredId("vrgo.cw.boxset.binge.boxset2.unique.movie.m3.content.id", boxset2UniqueMovieM3ContentId());
        requireConfiguredId("vrgo.cw.boxset.binge.boxset2.unique.tv.t3.episode.content.id", boxset2UniqueTvT3EpisodeContentId());
    }

    private void requireConfiguredId(String propertyKey, String value) {
        if (isBlank(value) || value.startsWith("REPLACE")) {
            throw new SkipException("Set " + propertyKey + " in environments/<env>.properties for boxset binge tests.");
        }
    }

    private String boxset1Id() {
        return strip(config.getProperty("vrgo.cw.boxset.binge.boxset1.id"));
    }

    private String boxset2Id() {
        return strip(config.getProperty("vrgo.cw.boxset.binge.boxset2.id"));
    }

    private String sharedMovieM1ContentId() {
        return strip(config.getProperty("vrgo.cw.boxset.binge.shared.movie.m1.content.id"));
    }

    private String sharedTvT1EpisodeContentId() {
        return strip(config.getProperty("vrgo.cw.boxset.binge.shared.tv.t1.episode.content.id"));
    }

    private String boxset1UniqueMovieM2ContentId() {
        return strip(config.getProperty("vrgo.cw.boxset.binge.boxset1.unique.movie.m2.content.id"));
    }

    private String boxset1UniqueTvT2EpisodeContentId() {
        return strip(config.getProperty("vrgo.cw.boxset.binge.boxset1.unique.tv.t2.episode.content.id"));
    }

    private String boxset2UniqueMovieM3ContentId() {
        return strip(config.getProperty("vrgo.cw.boxset.binge.boxset2.unique.movie.m3.content.id"));
    }

    private String boxset2UniqueTvT3EpisodeContentId() {
        return strip(config.getProperty("vrgo.cw.boxset.binge.boxset2.unique.tv.t3.episode.content.id"));
    }

    private String movieContentType() {
        return firstNonBlank(config.getProperty("vrgo.cw.boxset.binge.movie.content.type"), "VOD");
    }

    private String tvContentType() {
        return firstNonBlank(config.getProperty("vrgo.cw.boxset.binge.tv.content.type"), "VOD");
    }

    private int sharedMovieM1PartialWatchDuration() {
        return readIntOrDefault("vrgo.cw.boxset.binge.shared.movie.m1.partial.watch.duration", 500);
    }

    private int sharedMovieM1CompletedWatchDuration() {
        return readIntOrDefault("vrgo.cw.boxset.binge.shared.movie.m1.completed.watch.duration", 1000);
    }

    private int sharedTvT1CompletedWatchDuration() {
        return readIntOrDefault("vrgo.cw.boxset.binge.shared.tv.t1.completed.watch.duration", 800);
    }

    private int boxset1UniqueMovieM2CompletedWatchDuration() {
        return readIntOrDefault("vrgo.cw.boxset.binge.boxset1.unique.movie.m2.completed.watch.duration", 900);
    }

    private int boxset2UniqueMovieM3CompletedWatchDuration() {
        return readIntOrDefault("vrgo.cw.boxset.binge.boxset2.unique.movie.m3.completed.watch.duration", 2057);
    }

    private int boxset1UniqueTvT2CompletedWatchDuration() {
        return readIntOrDefault("vrgo.cw.boxset.binge.boxset1.unique.tv.t2.completed.watch.duration", 3027);
    }

    private int boxset2UniqueTvT3CompletedWatchDuration() {
        return readIntOrDefault("vrgo.cw.boxset.binge.boxset2.unique.tv.t3.completed.watch.duration", 3027);
    }

    private String subscriberId() {
        return config.getProperty("vrgo.header.cp_id");
    }

    private int cwPollTimeoutMs() {
        return readIntProperty("vrgo.cw.afteradds.poll.timeout.ms", 30_000);
    }

    private int cwPollIntervalMs() {
        return readIntProperty("vrgo.cw.afteradds.poll.interval.ms", 750);
    }

    private int readIntOrDefault(String key, int defaultValue) {
        String v = config.getProperty(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int readIntProperty(String key, int defaultValue) {
        return readIntOrDefault(key, defaultValue);
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

    private static String strip(String s) {
        return s == null ? "" : s.strip();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static void sleepQuietly(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SkipException("Interrupted while polling boxset binge CW/WA");
        }
    }
}
