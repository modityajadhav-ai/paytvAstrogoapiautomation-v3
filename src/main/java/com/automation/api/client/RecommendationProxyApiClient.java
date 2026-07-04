package com.automation.api.client;

import com.automation.api.auth.VrgoAuthSupport;
import com.automation.api.config.EnvironmentConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * VRGO Recommendation Proxy API client.
 * <p>
 * Endpoint paths and configurable values are driven by
 * {@code vrgo.recommendation.proxy.*} keys in the active environment file.
 * <p>
 * Auth follows the same convention as other VRGO clients:
 * {@code vrgo.bearer.token} / {@code VRGO_BEARER_TOKEN} for the JWT and
 * {@code vrgo.x.api.key} / {@code VRGO_X_API_KEY} for the API key.
 */
public class RecommendationProxyApiClient extends BaseApiClient {

    private static final String VRGO_HEADER_PREFIX = "vrgo.header.";

    private final String contentByUsecasePath;
    private final String railByUsecasePath;

    public RecommendationProxyApiClient(EnvironmentConfig config) {
        super(config, config.getProperty("vrgo.base.url"));
        this.contentByUsecasePath = config.getProperty(
                "vrgo.recommendation.proxy.content.by.usecase.path",
                "/recommendation-proxy/v1/content-by-usecase"
        );
        this.railByUsecasePath = config.getProperty(
                "vrgo.recommendation.proxy.rail.by.usecase.path",
                "/recommendation-proxy/v1/rail-by-usecase"
        );
    }

    // ── Content by use-case ───────────────────────────────────────────────────

    /**
     * GET /recommendation-proxy/v1/content-by-usecase — returns recommended content for a given use-case.
     *
     * @param usecase      use-case identifier, e.g. {@code ...more_like_this_vodies}
     * @param page         page context, e.g. {@code page}
     * @param offset       zero-based pagination offset
     * @param limit        maximum number of results to return
     * @param contentId    content ID sent via the {@code contentid} request header (nullable)
     * @param contentType  content type sent via the {@code contenttype} header, e.g. {@code series} (nullable)
     * @param primaryGenre primary genre sent via the {@code primarygenre} header, e.g. {@code Sports} (nullable)
     */
    public Response contentByUsecaseRaw(String usecase, String page, int offset, int limit,
                                        String contentId, String contentType, String primaryGenre) {
        Map<String, String> headerOverrides = new LinkedHashMap<>();
        if (contentId != null && !contentId.isBlank()) {
            headerOverrides.put("contentid", contentId.strip());
        }
        if (contentType != null && !contentType.isBlank()) {
            headerOverrides.put("contenttype", contentType.strip());
        }
        if (primaryGenre != null && !primaryGenre.isBlank()) {
            headerOverrides.put("primarygenre", primaryGenre.strip());
        }
        return vrgoGiven(headerOverrides)
                .queryParam("usecase", usecase)
                .queryParam("page", page)
                .queryParam("offset", offset)
                .queryParam("limit", limit)
                .when()
                .get(contentByUsecasePath);
    }

    // ── Rail by use-case ──────────────────────────────────────────────────────

    /**
     * GET /recommendation-proxy/v1/rail-by-usecase — returns a curated rail of recommended content
     * for a given use-case (e.g. {@code dont_miss}) using only the standard {@code vrgo.header.*} set.
     *
     * @param usecase use-case identifier, e.g. {@code dont_miss}
     * @param page    page context, e.g. {@code home}
     * @param offset  zero-based pagination offset
     * @param limit   maximum number of results to return
     */
    public Response railByUsecaseRaw(String usecase, String page, int offset, int limit) {
        return vrgoGiven(null)
                .queryParam("usecase", usecase)
                .queryParam("page", page)
                .queryParam("offset", offset)
                .queryParam("limit", limit)
                .when()
                .get(railByUsecasePath);
    }

    // ── Shared helper ─────────────────────────────────────────────────────────

    /**
     * Builds a {@link RequestSpecification} with the standard VRGO auth headers
     * ({@code Authorization}, {@code x-api-key}) and all {@code vrgo.header.*} entries from the
     * active environment config.
     *
     * @param headerOverrides optional headers that replace same-named entries from {@code vrgo.header.*}
     */
    private RequestSpecification vrgoGiven(Map<String, String> headerOverrides) {
        RequestSpecification r = given();

        String token = VrgoAuthSupport.getBearerToken(environmentConfig);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "Set vrgo.bearer.token (e.g. via BaseTest), VRGO_BEARER_TOKEN, or -Dvrgo.bearer.token.");
        }
        String authorization = token.regionMatches(true, 0, "Bearer ", 0, 7) ? token : "Bearer " + token;
        r = r.header("Authorization", authorization);

        String apiKey = firstNonBlank(
                System.getProperty("vrgo.x.api.key"),
                System.getenv("VRGO_X_API_KEY"),
                environmentConfig.getProperty("vrgo.x.api.key")
        );
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Set vrgo.x.api.key (properties / test base / env), VRGO_X_API_KEY, or -Dvrgo.x.api.key.");
        }
        r = r.header("x-api-key", apiKey);

        Map<String, String> headers = new LinkedHashMap<>(
                environmentConfig.propertiesWithPrefix(VRGO_HEADER_PREFIX)
        );
        if (headerOverrides != null) {
            for (Map.Entry<String, String> e : headerOverrides.entrySet()) {
                if (e.getValue() != null && !e.getValue().isBlank()) {
                    headers.put(e.getKey(), e.getValue().strip());
                }
            }
        }
        for (Map.Entry<String, String> e : headers.entrySet()) {
            r = r.header(e.getKey(), e.getValue());
        }
        return r;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
