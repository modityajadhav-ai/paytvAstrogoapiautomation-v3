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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Happy-flow coverage for the VRGO subscriber-event Locked Channels APIs (v3):
 * <ol>
 *   <li>DELETE /{channelId} — prepare a clean slate (unlock if already locked)</li>
 *   <li>GET              — verify channel is absent after prepare</li>
 *   <li>POST             — lock channel ({@code vrgo.locked.channels.add.channel.id})</li>
 *   <li>GET              — verify locked channel appears in the list</li>
 *   <li>GET /{channelCode}/status — verify locked status for the channel code</li>
 *   <li>DELETE /{channelId} — unlock the channel</li>
 *   <li>GET              — verify channel is no longer in the list</li>
 *   <li>DELETE /{channelId} — teardown / clean up (alwaysRun)</li>
 * </ol>
 * All paths are configurable via {@code vrgo.locked.channels.*} in the active environment file.
 */
@Feature("Lock Channel")
public class LockChannel extends BaseTest {

    // ── Step 1 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 0,
            description = "DELETE locked channel by id — empty slate before the flow"
    )
    @Story("DELETE /subscriber-event-service/v3/locked/channels/{channelId}")
    public void lockChannel_prepareUnlock() {
        requirePrerequisites();

        String channelId = resolveChannelId();
        Allure.parameter("locked.channels.channelId", channelId);

        Response r = lockedChannelsApi.deleteLockedChannelRaw(channelId);
        AllureAttachmentUtils.attachJson("locked-channels-prepare-unlock", r.asString());
        // 404 is acceptable when the channel was not locked yet.
        r.then().statusCode(anyOf(is(200), is(204), is(404)));

        Allure.parameter("environment", Environment.current().name());
    }

    // ── Step 2 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 5,
            dependsOnMethods = "lockChannel_prepareUnlock",
            description = "GET locked channels after prepare — channel must be absent"
    )
    @Story("GET /subscriber-event-service/v3/locked/channels")
    public void lockChannel_getAfterPrepareIsAbsent() {
        requirePrerequisites();

        String channelId = resolveChannelId();
        Response r = lockedChannelsApi.getLockedChannelsRaw();
        AllureAttachmentUtils.attachJson("locked-channels-get-after-prepare", r.asString());
        r.then().statusCode(200);

        Assert.assertFalse(
                responseContainsChannelId(r, channelId),
                "Locked channels list must not contain '" + channelId + "' after prepare DELETE."
        );
    }

    // ── Step 3 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 10,
            dependsOnMethods = "lockChannel_getAfterPrepareIsAbsent",
            description = "POST lock channel — adds one locked channel entry"
    )
    @Story("POST /subscriber-event-service/v3/locked/channels")
    public void lockChannel_postLockChannel() {
        requirePrerequisites();

        String channelId = resolveChannelId();
        Allure.parameter("locked.channels.channelId", channelId);

        Response r = lockedChannelsApi.postLockChannelRaw(channelId);
        AllureAttachmentUtils.attachJson("locked-channels-post-lock", r.asString());
        r.then().statusCode(200);
    }

    // ── Step 4 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 15,
            dependsOnMethods = "lockChannel_postLockChannel",
            description = "GET locked channels — channel must be present after POST lock"
    )
    @Story("GET /subscriber-event-service/v3/locked/channels")
    public void lockChannel_getVerifyChannelPresent() {
        requirePrerequisites();

        String channelId = resolveChannelId();
        Response r = lockedChannelsApi.getLockedChannelsRaw();
        AllureAttachmentUtils.attachJson("locked-channels-get-after-lock", r.asString());

        r.then()
                .statusCode(200)
                .body("data", notNullValue())
                .body("data.size()", greaterThan(0));

        Assert.assertTrue(
                responseContainsChannelId(r, channelId),
                "GET locked channels must contain '" + channelId + "' after POST lock."
        );
    }

    // ── Step 5 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 20,
            dependsOnMethods = "lockChannel_getVerifyChannelPresent",
            description = "GET locked channel status — must report locked for configured channel code"
    )
    @Story("GET /subscriber-event-service/v3/locked/channels/{channelCode}/status")
    public void lockChannel_getStatusVerifyLocked() {
        requirePrerequisites();

        String channelCode = resolveChannelCode();
        Allure.parameter("locked.channels.channelCode", channelCode);

        Response r = lockedChannelsApi.getLockedChannelStatusRaw(channelCode);
        AllureAttachmentUtils.attachJson("locked-channels-get-status", r.asString());

        r.then()
                .statusCode(200)
                .body("data", notNullValue());

        Assert.assertTrue(
                responseIndicatesLocked(r),
                "GET locked channel status for '" + channelCode + "' must indicate the channel is locked."
        );
    }

    // ── Step 6 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 25,
            dependsOnMethods = "lockChannel_getStatusVerifyLocked",
            description = "DELETE locked channel — unlock by channel id"
    )
    @Story("DELETE /subscriber-event-service/v3/locked/channels/{channelId}")
    public void lockChannel_deleteUnlockChannel() {
        requirePrerequisites();

        String channelId = resolveChannelId();
        Allure.parameter("locked.channels.deletedChannelId", channelId);

        Response r = lockedChannelsApi.deleteLockedChannelRaw(channelId);
        AllureAttachmentUtils.attachJson("locked-channels-delete-unlock", r.asString());
        r.then().statusCode(anyOf(is(200), is(204)));
    }

    // ── Step 7 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 30,
            dependsOnMethods = "lockChannel_deleteUnlockChannel",
            description = "GET locked channels — channel must be absent after DELETE unlock"
    )
    @Story("GET /subscriber-event-service/v3/locked/channels")
    public void lockChannel_getVerifyChannelAbsent() {
        requirePrerequisites();

        String channelId = resolveChannelId();
        Response r = lockedChannelsApi.getLockedChannelsRaw();
        AllureAttachmentUtils.attachJson("locked-channels-get-after-unlock", r.asString());
        r.then().statusCode(200);

        Assert.assertFalse(
                responseContainsChannelId(r, channelId),
                "Unlocked channel '" + channelId + "' must not appear in GET locked channels after DELETE."
        );
    }

    // ── Step 8 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 35,
            dependsOnMethods = "lockChannel_getVerifyChannelAbsent",
            alwaysRun = true,
            description = "Teardown: DELETE locked channel (idempotent unlock)"
    )
    @Story("DELETE /subscriber-event-service/v3/locked/channels/{channelId} (teardown)")
    public void lockChannel_teardownUnlock() {
        requirePrerequisites();

        String channelId = resolveChannelId();
        Response r = lockedChannelsApi.deleteLockedChannelRaw(channelId);
        AllureAttachmentUtils.attachJson("locked-channels-teardown-unlock", r.asString());
        r.then().statusCode(anyOf(is(200), is(204), is(404)));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String resolveChannelId() {
        String id = config.getProperty("vrgo.locked.channels.add.channel.id");
        if (!isConfiguredValue(id)) {
            throw new SkipException(
                    "Set vrgo.locked.channels.add.channel.id in environments/<env>.properties (non-blank, not REPLACE*).");
        }
        return id.strip();
    }

    private String resolveChannelCode() {
        String code = config.getProperty("vrgo.locked.channels.add.channel.code");
        if (!isConfiguredValue(code)) {
            throw new SkipException(
                    "Set vrgo.locked.channels.add.channel.code in environments/<env>.properties (non-blank, not REPLACE*).");
        }
        return code.strip();
    }

    /**
     * Returns {@code true} when the response body contains the given channelId inside
     * {@code data[*].channelId}. Falls back to a raw substring scan if the JSON path is absent.
     */
    private static boolean responseContainsChannelId(Response r, String channelId) {
        List<String> ids = r.jsonPath().getList("data.channelId");
        if (ids != null) {
            for (String id : ids) {
                if (channelId.equalsIgnoreCase(id)) {
                    return true;
                }
            }
            return false;
        }
        return r.asString().contains(channelId);
    }

    /**
     * Accepts common locked-status shapes: top-level {@code status}, {@code data.locked},
     * {@code data.isLocked}, top-level {@code locked}, or a raw body containing {@code "locked":true}.
     */
    private static boolean responseIndicatesLocked(Response r) {
        Boolean locked = safeGetBoolean(r, "status");
        if (locked != null) {
            return locked;
        }
        locked = safeGetBoolean(r, "data.locked");
        if (locked != null) {
            return locked;
        }
        locked = safeGetBoolean(r, "data.isLocked");
        if (locked != null) {
            return locked;
        }
        locked = safeGetBoolean(r, "locked");
        if (locked != null) {
            return locked;
        }
        String body = r.asString().toLowerCase(Locale.ROOT);
        return body.contains("\"status\":true")
                || body.contains("\"locked\":true")
                || body.contains("\"islocked\":true");
    }

    /** RestAssured {@code getBoolean} NPEs when the path is absent; use typed {@code get} instead. */
    private static Boolean safeGetBoolean(Response r, String path) {
        Object value = r.jsonPath().get(path);
        return value instanceof Boolean ? (Boolean) value : null;
    }

    private static boolean isConfiguredValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return !value.strip().toUpperCase(Locale.ROOT).startsWith("REPLACE");
    }

    private void requirePrerequisites() {
        if (lockedChannelsApi == null) {
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

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
