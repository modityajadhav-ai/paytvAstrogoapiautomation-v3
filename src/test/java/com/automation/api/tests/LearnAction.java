package com.automation.api.tests;

import com.automation.api.base.BaseTest;
import com.automation.api.auth.VrgoGuestTokenSupport;
import com.automation.api.config.Environment;
import com.automation.api.util.AllureAttachmentUtils;
import com.automation.api.util.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.qameta.allure.Allure;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.equalTo;

/**
 * Test coverage for the VRGO Learn Action API ({@code POST /learn-action/v1/learn?action=<action>}).
 * <p>
 * The test resolves a live EPG {@code eventId} (e.g. {@code 58309818:uri:prg:20120011:D10000502X975782})
 * from the content-detail-service {@code GET channel-day/{channelId}/{dayEpochMs}} endpoint at setup time,
 * then iterates over all configured learn actions in a single parameterised test.
 * <p>
 * Configurable via {@code vrgo.learn.action.*} keys in the active environment file.
 */
@Feature("Learn Action")
public class LearnAction extends BaseTest {

    private static final String SUCCESS_MESSAGE = "Learn action submitted successfully.";

    /** Latest EPG eventId resolved in {@link #resolveLearnActionContentId()} and shared across all actions. */
    private String learnActionContentId;

    /** Guest EPG eventId resolved lazily for guest-user tests. */
    private String guestLearnActionContentId;

    // ── Setup ─────────────────────────────────────────────────────────────────

    @BeforeClass(alwaysRun = true)
    public void resolveLearnActionContentId() {
        requirePrerequisites();

        String override = firstNonBlank(
                config.getProperty("vrgo.learn.action.event.id"),
                System.getProperty("vrgo.learn.action.event.id"),
                config.getProperty("vrgo.learn.action.channel.day.content.id")
        );
        if (isConfiguredId(override)) {
            learnActionContentId = override.strip();
            Allure.parameter("contentId.source", "property");
            return;
        }

        if (contentDetailApi == null) {
            throw new SkipException(
                    "contentDetailApi is null. Set vrgo.base.url in environments/<env>.properties.");
        }

        String channelId = firstNonBlank(
                config.getProperty("vrgo.learn.action.channel.id"),
                config.getProperty("vrgo.content.detail.channel.day.channel.id"),
                config.getProperty("vrgo.content.detail.channel.id")
        );
        if (!isConfiguredId(channelId)) {
            throw new SkipException(
                    "Set vrgo.learn.action.event.id (static EPG eventId) or a channel id " +
                    "(vrgo.learn.action.channel.id / vrgo.content.detail.channel.day.channel.id) " +
                    "so the latest channel-day eventId can be resolved.");
        }

        long epoch = pickEpochMs();
        Allure.parameter("channelDay.channelId", channelId.strip());
        Allure.parameter("channelDay.epochMs", String.valueOf(epoch));

        Response channelDayResponse = contentDetailApi.getChannelDayRaw(
                contentDetailApi.getChannelDayPathTemplate(),
                channelId.strip(),
                epoch
        );
        AllureAttachmentUtils.attachJson("learn-action-setup-channel-day", channelDayResponse.asString());
        channelDayResponse.then().statusCode(200);

        List<String> eventIds = extractEventIdsFromChannelDay(channelDayResponse);
        if (eventIds.isEmpty()) {
            throw new SkipException(
                    "No eventId values found in channel-day response for channel " + channelId.strip()
                            + ". Set vrgo.learn.action.event.id explicitly.");
        }

        boolean pickLatest = !"first".equalsIgnoreCase(
                firstNonBlank(config.getProperty("vrgo.learn.action.event.pick"), "last").strip()
        );
        learnActionContentId = pickLatest
                ? eventIds.get(eventIds.size() - 1)
                : eventIds.get(0);

        Allure.parameter("contentId.source", "channel-day");
        Allure.parameter("channelDay.event.pick", pickLatest ? "last" : "first");
        Allure.parameter("channelDay.eventCount", String.valueOf(eventIds.size()));
    }

    // ── Data provider ─────────────────────────────────────────────────────────

    @DataProvider(name = "learnActions")
    public static Object[][] learnActions() {
        return new Object[][]{
                {"bookmark"},
                {"clicked_on_search_result"},
                {"stop_watching"},
                {"end_watching"},
                {"start_watching"},
                {"purchased"},
                {"download"},
                {"record"}
        };
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test(
            dataProvider = "learnActions",
            description = "POST /learn-action/v1/learn?action=<action> — posts a learn action event with a channel-day EPG eventId"
    )
    @Story("POST /learn-action/v1/learn")
    public void learnAction_postAction_returns200(String action) {
        requirePrerequisites();

        Allure.parameter("action",      action);
        Allure.parameter("contentId",   learnActionContentId);
        Allure.parameter("environment", Environment.current().name());

        Map<String, Object> body = buildLearnActionBody();

        Response r = learnActionApi.postLearnActionRaw(action, body);
        AllureAttachmentUtils.attachJson("learn-action-" + action + "-response", r.asString());

        r.then()
                .statusCode(200)
                .body("status", equalTo(true))
                .body("message", equalTo(SUCCESS_MESSAGE));
    }

    @Test(
            dataProvider = "learnActions",
            groups = "guest",
            description = "POST /learn-action/v1/learn?action=<action> — guest user posts a learn action event"
    )
    @Story("POST /learn-action/v1/learn (guest)")
    public void learnAction_postAction_guest_returns200(String action) {
        requireGuestPrerequisites();

        String contentId = resolveGuestLearnActionContentId();
        Allure.parameter("action",      action);
        Allure.parameter("contentId",   contentId);
        Allure.parameter("environment", Environment.current().name());
        Allure.parameter("userType",    "guest");

        Map<String, Object> body = buildLearnActionBody(contentId);

        Response r = learnActionApi.postLearnActionGuestRaw(action, body);
        AllureAttachmentUtils.attachJson("learn-action-guest-" + action + "-response", r.asString());

        r.then()
                .statusCode(200)
                .body("status", equalTo(true))
                .body("message", equalTo(SUCCESS_MESSAGE));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds the POST body from configurable properties, falling back to the values observed
     * in the reference curl command when a property is absent.
     */
    private Map<String, Object> buildLearnActionBody() {
        return buildLearnActionBody(learnActionContentId);
    }

    private Map<String, Object> buildLearnActionBody(String contentId) {
        int sessionDuration = readIntProperty("vrgo.learn.action.session.duration", 4);
        int lastPosition    = readIntProperty("vrgo.learn.action.last.position",    7076);
        String refUseCase   = firstNonBlank(
                config.getProperty("vrgo.learn.action.ref.use.case"),
                "uiredirect/default"
        );
        String primaryGenreRaw = config.getProperty("vrgo.learn.action.primary.genre", "Entertainment");
        List<String> primaryGenre = Arrays.asList(
                primaryGenreRaw.split(",")
        );
        primaryGenre.replaceAll(String::strip);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contentId",       contentId);
        body.put("contentType",     "channelDay");
        body.put("sessionDuration", sessionDuration);
        body.put("lastPosition",    lastPosition);
        body.put("refUseCase",      refUseCase);
        body.put("primaryGenre",    primaryGenre);
        return body;
    }

    private void requirePrerequisites() {
        if (learnActionApi == null) {
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
                    "Set vrgo.x.api.key in environments/<env>.properties, BaseTest.VRGO_MANUAL_X_API_KEY, or VRGO_X_API_KEY / -Dvrgo.x.api.key.");
        }
    }

    private void requireGuestPrerequisites() {
        if (learnActionApi == null || contentDetailApi == null) {
            throw new SkipException("Configure vrgo.base.url in environments/<env>.properties to run this test.");
        }
        if (!VrgoGuestTokenSupport.canBootstrapGuestAuth(config)) {
            throw new SkipException(
                    "Set vrgo.search.proxy.guest.bearer.token in secrets, VRGO_GUEST_BEARER_TOKEN, "
                            + "or enable guest browser recovery (vrgo.guest.browser.recovery.enabled=true).");
        }
        if (isBlank(System.getenv("VRGO_X_API_KEY"))
                && isBlank(System.getProperty("vrgo.x.api.key"))
                && isBlank(config.getProperty("vrgo.x.api.key"))) {
            throw new SkipException(
                    "Set vrgo.x.api.key in environments/<env>.properties, BaseTest.VRGO_MANUAL_X_API_KEY, or VRGO_X_API_KEY / -Dvrgo.x.api.key.");
        }
    }

    private String resolveGuestLearnActionContentId() {
        if (guestLearnActionContentId != null) {
            return guestLearnActionContentId;
        }

        String override = firstNonBlank(
                config.getProperty("vrgo.learn.action.event.id"),
                System.getProperty("vrgo.learn.action.event.id"),
                config.getProperty("vrgo.learn.action.channel.day.content.id")
        );
        if (isConfiguredId(override)) {
            guestLearnActionContentId = override.strip();
            return guestLearnActionContentId;
        }

        String channelId = firstNonBlank(
                config.getProperty("vrgo.learn.action.channel.id"),
                config.getProperty("vrgo.content.detail.channel.day.channel.id"),
                config.getProperty("vrgo.content.detail.channel.id")
        );
        if (!isConfiguredId(channelId)) {
            throw new SkipException(
                    "Set vrgo.learn.action.event.id (static EPG eventId) or a channel id "
                            + "for guest learn-action tests.");
        }

        long epoch = pickEpochMs();
        Response channelDayResponse = contentDetailApi.getChannelDayRawGuest(
                contentDetailApi.getChannelDayPathTemplate(),
                channelId.strip(),
                epoch
        );
        AllureAttachmentUtils.attachJson("learn-action-guest-setup-channel-day", channelDayResponse.asString());
        channelDayResponse.then().statusCode(200);

        List<String> eventIds = extractEventIdsFromChannelDay(channelDayResponse);
        if (eventIds.isEmpty()) {
            throw new SkipException(
                    "No eventId values found in guest channel-day response for channel " + channelId.strip());
        }

        boolean pickLatest = !"first".equalsIgnoreCase(
                firstNonBlank(config.getProperty("vrgo.learn.action.event.pick"), "last").strip()
        );
        guestLearnActionContentId = pickLatest
                ? eventIds.get(eventIds.size() - 1)
                : eventIds.get(0);
        return guestLearnActionContentId;
    }

    /**
     * Picks an epoch ms value that is guaranteed to be in {@code [now, end of local day)}.
     * Uses {@code vrgo.content.detail.channel.day.epoch.ms} when the stored value is still
     * in the future; otherwise generates a random epoch within today's remaining window.
     */
    private long pickEpochMs() {
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

    private static int readIntProperty(String key, int defaultValue) {
        String s = config.getProperty(key);
        if (s == null || s.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean isConfiguredId(String id) {
        if (id == null || id.isBlank()) return false;
        String upper = id.strip().toUpperCase(Locale.ROOT);
        return !upper.startsWith("REPLACE") && !upper.equals("NULL");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private List<String> extractEventIdsFromChannelDay(Response channelDayResponse) {
        List<String> eventIds = new ArrayList<>();
        try {
            JsonNode root = JsonUtils.mapper().readTree(channelDayResponse.asString());
            collectEventIdsFromJson(root, eventIds);
        } catch (JsonProcessingException ignored) {
            eventIds.clear();
        }
        if (!eventIds.isEmpty()) {
            return eventIds;
        }

        Matcher matcher = Pattern.compile("\"eventId\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(channelDayResponse.asString());
        while (matcher.find()) {
            String eventId = matcher.group(1);
            if (isConfiguredId(eventId)) {
                eventIds.add(eventId.strip());
            }
        }
        return eventIds;
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

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
