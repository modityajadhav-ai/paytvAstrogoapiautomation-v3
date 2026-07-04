package com.automation.api.tests;

import com.automation.api.base.BaseTest;
import com.automation.api.config.Environment;
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
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.notNullValue;

/**
 * Test coverage for the VRGO Recommendation Proxy APIs.
 * <p>
 * All endpoint paths, query parameters, and configurable values are driven by
 * {@code vrgo.recommendation.proxy.*} keys in the active environment file.
 */
@Feature("Recommendation Proxy")
public class RecommendationProxy extends BaseTest {

    /** Upstream recommendation provider may return this when it has no data for the request. */
    private static final String EMPTY_RECOMMENDATION_FROM_SOURCE =
            "Empty Recommendation response received from the source service";

    /** Recommendation proxy returns this when the use-case id is not configured for the environment. */
    private static final String USECASE_NOT_CONFIGURED_MARKER = "No Use Case found for id";

    // ── Content by use-case ───────────────────────────────────────────────────

    @Test(description = "GET /recommendation-proxy/v1/content-by-usecase — returns non-empty recommendation results for more_like_this_vod")
    @Story("GET content-by-usecase (more_like_this_vod)")
    public void contentByUsecase_moreLikeThisVodies_returnsResults() {
        requirePrerequisites();

        String usecase      = config.getProperty("vrgo.recommendation.proxy.content.by.usecase.usecase",    "more_like_this_vod");
        String page         = config.getProperty("vrgo.recommendation.proxy.content.by.usecase.page",        "details");
        int    offset       = readIntProperty("vrgo.recommendation.proxy.content.by.usecase.offset",          0);
        int    limit        = readIntProperty("vrgo.recommendation.proxy.content.by.usecase.limit",           100);
        String contentId    = config.getProperty("vrgo.recommendation.proxy.content.by.usecase.content.id");
        String contentType  = config.getProperty("vrgo.recommendation.proxy.content.by.usecase.content.type", "series");
        String primaryGenre = config.getProperty("vrgo.recommendation.proxy.content.by.usecase.primary.genre", "TV Show/Series");

        Allure.parameter("usecase",      usecase);
        Allure.parameter("page",         page);
        Allure.parameter("offset",       offset);
        Allure.parameter("limit",        limit);
        Allure.parameter("contentId",    contentId);
        Allure.parameter("contentType",  contentType);
        Allure.parameter("primaryGenre", primaryGenre);
        Allure.parameter("environment",  Environment.current().name());

        Response r = recommendationProxyApi.contentByUsecaseRaw(
                usecase, page, offset, limit, contentId, contentType, primaryGenre);
        AllureAttachmentUtils.attachJson("content-by-usecase-response", r.asString());
        assertRecommendationResultsOrSkip(r, "content-by-usecase");
    }

    @Test(description = "GET /recommendation-proxy/v1/content-by-usecase — more_like_this_epg with channelDay contentId from channel-day")
    @Story("GET content-by-usecase (more_like_this_epg)")
    public void contentByUsecase_moreLikeThisEpg_channelDayEventId_returnsResults() {
        requirePrerequisites();

        String usecase      = config.getProperty("vrgo.recommendation.proxy.content.by.usecase.epg.usecase",      "more_like_this_epg");
        String page         = config.getProperty("vrgo.recommendation.proxy.content.by.usecase.epg.page",          "details");
        int    offset       = readIntProperty("vrgo.recommendation.proxy.content.by.usecase.epg.offset",             0);
        int    limit        = readIntProperty("vrgo.recommendation.proxy.content.by.usecase.epg.limit",              100);
        String contentType  = config.getProperty("vrgo.recommendation.proxy.content.by.usecase.epg.content.type",  "channelDay");
        ChannelDayEpgContext channelDay = resolveChannelDayContextForEpg();
        String contentId    = channelDay.contentId();
        String primaryGenre = channelDay.primaryGenre();

        Allure.parameter("usecase",      usecase);
        Allure.parameter("page",         page);
        Allure.parameter("offset",       offset);
        Allure.parameter("limit",        limit);
        Allure.parameter("contentId",    contentId);
        Allure.parameter("contentType",  contentType);
        Allure.parameter("primaryGenre", primaryGenre);
        Allure.parameter("environment",  Environment.current().name());

        Response r = recommendationProxyApi.contentByUsecaseRaw(
                usecase, page, offset, limit, contentId, contentType, primaryGenre);
        AllureAttachmentUtils.attachJson("content-by-usecase-more_like_this_epg-response", r.asString());
        assertRecommendationResultsOrSkip(r, "content-by-usecase");
    }

    // ── Rail by use-case ──────────────────────────────────────────────────────

    /** Use-case values exercised by the rail-by-usecase tests (page=home). */
    private static final String[] RAIL_USECASES = {
            "dont_miss",
            "popular_top_10",
            "trending_sports",
            "personalised_boxset",
            "popular_top_10_kids",
            "trending_shows",
            "trending_movie_kids",
            "featured_hero",
            "recently_added",
            "because_you_watched",
            "top_10_home",
    };

    @DataProvider(name = "railUsecases")
    public static Object[][] railUsecases() {
        Object[][] rows = new Object[RAIL_USECASES.length][1];
        for (int i = 0; i < RAIL_USECASES.length; i++) {
            rows[i][0] = RAIL_USECASES[i];
        }
        return rows;
    }

    @Test(
            dataProvider = "railUsecases",
            description = "GET /recommendation-proxy/v1/rail-by-usecase — returns non-empty rail results for each home-page use-case"
    )
    @Story("GET rail-by-usecase (home)")
    public void railByUsecase_homePage_returnsResults(String usecase) {
        requirePrerequisites();

        String page   = config.getProperty("vrgo.recommendation.proxy.rail.by.usecase.page", "home");
        int    offset = readIntProperty("vrgo.recommendation.proxy.rail.by.usecase.offset",   0);
        int    limit  = readIntProperty("vrgo.recommendation.proxy.rail.by.usecase.limit",    50);

        Allure.parameter("usecase",     usecase);
        Allure.parameter("page",        page);
        Allure.parameter("offset",      offset);
        Allure.parameter("limit",       limit);
        Allure.parameter("environment", Environment.current().name());

        Response r = recommendationProxyApi.railByUsecaseRaw(usecase, page, offset, limit);
        AllureAttachmentUtils.attachJson("rail-by-usecase-" + usecase + "-response", r.asString());
        assertRecommendationResultsOrSkip(r, "rail-by-usecase");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Skips when the upstream recommendation provider returns no data (third-party dependency).
     * Otherwise asserts HTTP 200 and a non-null results/contents payload.
     */
    private static void assertRecommendationResultsOrSkip(Response r, String endpointLabel) {
        skipIfEmptyRecommendationFromSourceService(r);
        skipIfUsecaseNotConfigured(r);

        r.then()
                .statusCode(200)
                .body(notNullValue());

        Assert.assertFalse(r.asString().isBlank(),
                endpointLabel + " response body must not be empty.");
        Assert.assertTrue(r.jsonPath().getList("data.results") != null
                        || r.jsonPath().getList("contents") != null
                        || r.jsonPath().getList("results") != null,
                "Response should contain a non-null results/contents array (data.results, contents, or results).");
    }

    private static void skipIfEmptyRecommendationFromSourceService(Response r) {
        String errorMessage = resolveRecommendationErrorMessage(r);
        if (errorMessage != null && errorMessage.contains(EMPTY_RECOMMENDATION_FROM_SOURCE)) {
            throw new SkipException(
                    "Skipped: third-party recommendation source returned no data — "
                            + errorMessage
                            + ". This reflects upstream availability, not a recommendation-proxy defect.");
        }
    }

    private static void skipIfUsecaseNotConfigured(Response r) {
        String body = r.asString();
        if (body == null || !body.contains(USECASE_NOT_CONFIGURED_MARKER)) {
            return;
        }
        String detail = firstNonBlank(
                r.jsonPath().getString("message"),
                r.jsonPath().getString("errorMessage"),
                r.jsonPath().getString("data.message"),
                USECASE_NOT_CONFIGURED_MARKER
        );
        throw new SkipException(
                "Skipped: data or use case is not configured for this environment — " + detail);
    }

    private static String resolveRecommendationErrorMessage(Response r) {
        String body = r.asString();
        if (body == null || body.isBlank()) {
            return null;
        }
        if (!body.contains("errorMessage")) {
            return null;
        }
        String msg = r.jsonPath().getString("errorMessage");
        if (msg != null && !msg.isBlank()) {
            return msg.strip();
        }
        msg = r.jsonPath().getString("data.errorMessage");
        if (msg != null && !msg.isBlank()) {
            return msg.strip();
        }
        return body.contains(EMPTY_RECOMMENDATION_FROM_SOURCE) ? EMPTY_RECOMMENDATION_FROM_SOURCE : null;
    }

    private void requirePrerequisites() {
        if (recommendationProxyApi == null) {
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

    private static int readIntProperty(String key, int defaultValue) {
        String s = config.getProperty(key);
        if (s == null || s.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Resolves {@code contentId} and {@code primaryGenre} for the EPG recommendation use-case.
     * Both are taken from the same channel-day event slot when not explicitly overridden.
     */
    private ChannelDayEpgContext resolveChannelDayContextForEpg() {
        String contentIdOverride = firstNonBlank(
                config.getProperty("vrgo.recommendation.proxy.content.by.usecase.epg.content.id"),
                System.getProperty("vrgo.recommendation.proxy.content.by.usecase.epg.content.id")
        );
        String genreOverride = firstNonBlank(
                System.getProperty("vrgo.recommendation.proxy.content.by.usecase.epg.primary.genre"),
                config.getProperty("vrgo.recommendation.proxy.content.by.usecase.epg.primary.genre")
        );

        if (isConfiguredId(contentIdOverride) && genreOverride != null && !genreOverride.isBlank()) {
            Allure.parameter("contentId.source", "property");
            Allure.parameter("primaryGenre.source", "property");
            return new ChannelDayEpgContext(contentIdOverride.strip(), genreOverride.strip());
        }

        if (contentDetailApi == null) {
            throw new SkipException(
                    "contentDetailApi is null. Set vrgo.base.url in environments/<env>.properties.");
        }

        String channelId = firstNonBlank(
                config.getProperty("vrgo.recommendation.proxy.content.by.usecase.epg.channel.id"),
                config.getProperty("vrgo.content.detail.channel.day.channel.id"),
                config.getProperty("vrgo.content.detail.channel.id")
        );
        if (!isConfiguredId(channelId)) {
            throw new SkipException(
                    "Set vrgo.recommendation.proxy.content.by.usecase.epg.content.id (static) or a channel id "
                            + "(vrgo.recommendation.proxy.content.by.usecase.epg.channel.id / "
                            + "vrgo.content.detail.channel.day.channel.id).");
        }

        long epoch = pickChannelDayEpochMs();
        Allure.parameter("channelDay.channelId", channelId.strip());
        Allure.parameter("channelDay.epochMs", String.valueOf(epoch));

        Response channelDayResponse = contentDetailApi.getChannelDayRaw(
                contentDetailApi.getChannelDayPathTemplate(),
                channelId.strip(),
                epoch
        );
        AllureAttachmentUtils.attachJson("content-by-usecase-epg-channel-day", channelDayResponse.asString());
        channelDayResponse.then().statusCode(200);

        List<ChannelDayEpgSlot> slots = extractEpgSlotsFromChannelDay(channelDayResponse);
        if (slots.isEmpty()) {
            throw new SkipException(
                    "No eventId values found in channel-day response for channel " + channelId.strip()
                            + ". Set vrgo.recommendation.proxy.content.by.usecase.epg.content.id explicitly.");
        }

        Allure.parameter("channelDay.eventCount", String.valueOf(slots.size()));

        ChannelDayEpgSlot recentSlot = slots.get(slots.size() - 1);
        String contentId = isConfiguredId(contentIdOverride)
                ? contentIdOverride.strip()
                : recentSlot.eventId();
        String primaryGenre = findPrimaryGenreForEventId(slots, contentId);
        if (primaryGenre == null || primaryGenre.isBlank()) {
            primaryGenre = genreOverride != null && !genreOverride.isBlank()
                    ? genreOverride.strip()
                    : "Movie";
            Allure.parameter("primaryGenre.source", "fallback");
        } else {
            Allure.parameter("primaryGenre.source", "channel-day");
        }

        Allure.parameter("contentId.source", isConfiguredId(contentIdOverride) ? "property" : "channel-day");
        return new ChannelDayEpgContext(contentId, primaryGenre);
    }

    private static String findPrimaryGenreForEventId(List<ChannelDayEpgSlot> slots, String eventId) {
        for (int i = slots.size() - 1; i >= 0; i--) {
            ChannelDayEpgSlot slot = slots.get(i);
            if (eventId.equals(slot.eventId()) && slot.primaryGenre() != null && !slot.primaryGenre().isBlank()) {
                return slot.primaryGenre();
            }
        }
        ChannelDayEpgSlot recent = slots.get(slots.size() - 1);
        return recent.primaryGenre();
    }

    private List<ChannelDayEpgSlot> extractEpgSlotsFromChannelDay(Response channelDayResponse) {
        List<ChannelDayEpgSlot> slots = new ArrayList<>();
        try {
            JsonNode root = JsonUtils.mapper().readTree(channelDayResponse.asString());
            collectEpgSlotsFromJson(root, slots);
        } catch (JsonProcessingException ignored) {
            slots.clear();
        }
        if (!slots.isEmpty()) {
            return slots;
        }

        Matcher matcher = Pattern.compile("\"eventId\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(channelDayResponse.asString());
        while (matcher.find()) {
            String eventId = matcher.group(1);
            if (isConfiguredId(eventId)) {
                slots.add(new ChannelDayEpgSlot(eventId.strip(), null));
            }
        }
        return slots;
    }

    private static void collectEpgSlotsFromJson(JsonNode node, List<ChannelDayEpgSlot> out) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            JsonNode eventIdNode = node.get("eventId");
            if (eventIdNode != null && eventIdNode.isTextual()) {
                String eventId = eventIdNode.asText();
                if (isConfiguredId(eventId)) {
                    out.add(new ChannelDayEpgSlot(eventId.strip(), extractPrimaryGenreFromEventNode(node)));
                }
            }
            node.fields().forEachRemaining(e -> collectEpgSlotsFromJson(e.getValue(), out));
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectEpgSlotsFromJson(child, out);
            }
        }
    }

    /**
     * Reads {@code genres.primary[0].genre[0]} from a channel-day event object.
     */
    private static String extractPrimaryGenreFromEventNode(JsonNode eventNode) {
        JsonNode genres = eventNode.get("genres");
        if (genres == null || genres.isNull()) {
            return null;
        }
        JsonNode primary = genres.get("primary");
        if (primary == null || !primary.isArray() || primary.isEmpty()) {
            return null;
        }
        JsonNode firstPrimary = primary.get(0);
        if (firstPrimary == null || firstPrimary.isNull()) {
            return null;
        }
        JsonNode genreArr = firstPrimary.get("genre");
        if (genreArr == null || !genreArr.isArray() || genreArr.isEmpty()) {
            return null;
        }
        JsonNode genre = genreArr.get(0);
        if (genre == null || !genre.isTextual() || genre.asText().isBlank()) {
            return null;
        }
        return genre.asText().strip();
    }

    private record ChannelDayEpgContext(String contentId, String primaryGenre) {}

    private record ChannelDayEpgSlot(String eventId, String primaryGenre) {}

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

    private static boolean isConfiguredId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String upper = id.strip().toUpperCase(Locale.ROOT);
        return !upper.startsWith("REPLACE") && !upper.equals("NULL");
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
