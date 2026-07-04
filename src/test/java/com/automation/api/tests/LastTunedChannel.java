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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Happy-flow coverage for the VRGO subscriber-event Last Tuned Channel APIs:
 * <ol>
 *   <li>GET                  — check current last tuned channel; DELETE only when channel is already present</li>
 *   <li>POST                 — add channel ({@code vrgo.last.tuned.channel.add.channel.id})</li>
 *   <li>GET                  — verify last tuned channel reflects the added channel</li>
 *   <li>DELETE /{channelId}   — remove the channel just added</li>
 *   <li>GET                  — verify channel is no longer present</li>
 *   <li>DELETE /{channelId}   — teardown / clean up (alwaysRun; tolerates channel not found)</li>
 * </ol>
 * Prepare and teardown DELETE calls accept {@code 200} or a not-found response (404 / {@code "Channel not found"}).
 * POST, GET-after-add, and main DELETE assert {@code 200} only.
 */
@Feature("Last Tuned Channel")
public class LastTunedChannel extends BaseTest {

    // ── Step 1 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 0,
            description = "GET last tuned channel — delete only when channel is already present (prepare)"
    )
    @Story("GET + DELETE /subscriber-event-service/last-tuned-channel")
    public void lastTunedChannel_prepareCleanState() {
        requirePrerequisites();

        String channelId = resolveChannelId();
        Allure.parameter("last.tuned.channel.channelId", channelId);
        Allure.parameter("environment", Environment.current().name());

        Response get = lastTunedChannelApi.getLastTunedChannelRaw();
        AllureAttachmentUtils.attachJson("last-tuned-channel-prepare-get", get.asString());

        if (!responseContainsChannelId(get, channelId)) {
            Allure.parameter("last.tuned.channel.prepareDeleteSkipped", "true");
            return;
        }

        Allure.parameter("last.tuned.channel.prepareDeleteSkipped", "false");
        Response delete = lastTunedChannelApi.deleteLastTunedChannelRaw(channelId);
        AllureAttachmentUtils.attachJson("last-tuned-channel-prepare-delete", delete.asString());
        assertDeleteOkOrChannelNotFound(delete);
    }

    // ── Step 2 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 10,
            dependsOnMethods = "lastTunedChannel_prepareCleanState",
            description = "POST last tuned channel — adds channel to last tuned"
    )
    @Story("POST /subscriber-event-service/last-tuned-channel")
    public void lastTunedChannel_postAddChannel() {
        requirePrerequisites();

        String channelId = resolveChannelId();
        Allure.parameter("last.tuned.channel.channelId", channelId);

        Response r = lastTunedChannelApi.postLastTunedChannelRaw(channelId);
        AllureAttachmentUtils.attachJson("last-tuned-channel-post-add", r.asString());
        r.then().statusCode(200);
    }

    // ── Step 3 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 15,
            dependsOnMethods = "lastTunedChannel_postAddChannel",
            description = "GET last tuned channel — channel must be present after POST"
    )
    @Story("GET /subscriber-event-service/last-tuned-channel")
    public void lastTunedChannel_getVerifyChannelPresent() {
        requirePrerequisites();

        String channelId = resolveChannelId();
        Response r = lastTunedChannelApi.getLastTunedChannelRaw();
        AllureAttachmentUtils.attachJson("last-tuned-channel-get-after-add", r.asString());

        r.then()
                .statusCode(200)
                .body("data", notNullValue());

        Assert.assertTrue(
                responseContainsChannelId(r, channelId),
                "GET last tuned channel must contain '" + channelId + "' after POST."
        );
    }

    // ── Step 4 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 20,
            dependsOnMethods = "lastTunedChannel_getVerifyChannelPresent",
            description = "DELETE last tuned channel — remove by channel id"
    )
    @Story("DELETE /subscriber-event-service/last-tuned-channel/{channelId}")
    public void lastTunedChannel_deleteChannel() {
        requirePrerequisites();

        String channelId = resolveChannelId();
        Allure.parameter("last.tuned.channel.deletedChannelId", channelId);

        Response r = lastTunedChannelApi.deleteLastTunedChannelRaw(channelId);
        AllureAttachmentUtils.attachJson("last-tuned-channel-delete", r.asString());
        r.then().statusCode(200);
    }

    // ── Step 5 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 25,
            dependsOnMethods = "lastTunedChannel_deleteChannel",
            description = "GET last tuned channel — channel must be absent after DELETE"
    )
    @Story("GET /subscriber-event-service/last-tuned-channel")
    public void lastTunedChannel_getVerifyChannelAbsent() {
        requirePrerequisites();

        String channelId = resolveChannelId();
        Response r = lastTunedChannelApi.getLastTunedChannelRaw();
        AllureAttachmentUtils.attachJson("last-tuned-channel-get-after-delete", r.asString());
        r.then().statusCode(anyOf(is(200), is(404)));

        Assert.assertFalse(
                responseContainsChannelId(r, channelId),
                "Deleted channel '" + channelId + "' must not appear in GET last tuned channel after DELETE."
        );
    }

    // ── Step 6 ────────────────────────────────────────────────────────────────

    @Test(
            priority = 30,
            dependsOnMethods = "lastTunedChannel_getVerifyChannelAbsent",
            alwaysRun = true,
            description = "Teardown: DELETE last tuned channel when present (tolerates channel not found)"
    )
    @Story("DELETE /subscriber-event-service/last-tuned-channel/{channelId} (teardown)")
    public void lastTunedChannel_teardownDelete() {
        requirePrerequisites();

        String channelId = resolveChannelId();
        Response get = lastTunedChannelApi.getLastTunedChannelRaw();
        AllureAttachmentUtils.attachJson("last-tuned-channel-teardown-get", get.asString());

        if (!responseContainsChannelId(get, channelId)) {
            Allure.parameter("last.tuned.channel.teardownDeleteSkipped", "true");
            return;
        }

        Allure.parameter("last.tuned.channel.teardownDeleteSkipped", "false");
        Response delete = lastTunedChannelApi.deleteLastTunedChannelRaw(channelId);
        AllureAttachmentUtils.attachJson("last-tuned-channel-teardown-delete", delete.asString());
        assertDeleteOkOrChannelNotFound(delete);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String resolveChannelId() {
        String id = config.getProperty("vrgo.last.tuned.channel.add.channel.id");
        if (!isConfiguredValue(id)) {
            throw new SkipException(
                    "Set vrgo.last.tuned.channel.add.channel.id in environments/<env>.properties (non-blank, not REPLACE*).");
        }
        return id.strip();
    }

    /**
     * Prepare/teardown DELETE: pass on 200, or when the channel is already gone (404 / message).
     */
    private static void assertDeleteOkOrChannelNotFound(Response r) {
        if (r.statusCode() == 200) {
            return;
        }
        if (r.statusCode() == 404 || responseIndicatesChannelNotFound(r)) {
            return;
        }
        r.then().statusCode(200);
    }

    private static boolean responseIndicatesChannelNotFound(Response r) {
        String message = r.jsonPath().getString("message");
        if (message != null && message.toLowerCase(Locale.ROOT).contains("channel not found")) {
            return true;
        }
        return r.asString().toLowerCase(Locale.ROOT).contains("channel not found");
    }

    /**
     * Returns {@code true} when the response body contains the given channelId inside
     * {@code data.channelId} or {@code data[*].channelId}. Falls back to a raw substring scan
     * if the JSON path is absent.
     */
    private static boolean responseContainsChannelId(Response r, String channelId) {
        String single = r.jsonPath().getString("data.channelId");
        if (channelId.equalsIgnoreCase(single)) {
            return true;
        }

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

    private static boolean isConfiguredValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return !value.strip().toUpperCase(Locale.ROOT).startsWith("REPLACE");
    }

    private void requirePrerequisites() {
        if (lastTunedChannelApi == null) {
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
