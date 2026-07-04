package com.automation.api.client;

import com.automation.api.auth.VrgoAuthSupport;
import com.automation.api.config.EnvironmentConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * VRGO VR Search Proxy API client.
 * <p>
 * Covers the search-proxy service endpoints (paths, query params, and bodies
 * are driven by {@code vrgo.search.proxy.*} properties in the active environment file).
 * <p>
 * Auth follows the same convention as other VRGO clients:
 * {@code vrgo.bearer.token} / {@code VRGO_BEARER_TOKEN} for the logged-in JWT and
 * {@code vrgo.search.proxy.guest.bearer.token} / {@code VRGO_GUEST_BEARER_TOKEN} for the guest JWT.
 * {@code vrgo.x.api.key} / {@code VRGO_X_API_KEY} for the API key.
 * <p>
 * Guest-specific header overrides (cp_id, device_id, entitlementhash, etc.) are read from
 * {@code vrgo.search.proxy.guest.header.*} and merged on top of the base {@code vrgo.header.*} map.
 */
public class VRSearchProxyApiClient extends BaseApiClient {

    private static final String VRGO_HEADER_PREFIX        = "vrgo.header.";
    private static final String GUEST_HEADER_PREFIX       = "vrgo.search.proxy.guest.header.";

    private final String globalContentSearchPath;
    private final String pubGlobalContentSearchPath;
    private final String searchSuggesterPath;
    private final String pubSearchSuggesterPath;
    private final String searchByUsecasePath;
    private final String pubSearchByUsecasePath;
    private final String subgenrePreferencePath;

    public VRSearchProxyApiClient(EnvironmentConfig config) {
        super(config, config.getProperty("vrgo.base.url"));
        this.globalContentSearchPath = config.getProperty(
                "vrgo.search.proxy.global.content.search.path",
                "/search-proxy/v1/global-content-search"
        );
        this.pubGlobalContentSearchPath = config.getProperty(
                "vrgo.search.proxy.pub.global.content.search.path",
                "/search-proxy/pub/v1/global-content-search"
        );
        this.searchSuggesterPath = config.getProperty(
                "vrgo.search.proxy.suggester.path",
                "/search-proxy/v1/search-suggester"
        );
        this.pubSearchSuggesterPath = config.getProperty(
                "vrgo.search.proxy.pub.suggester.path",
                "/search-proxy/pub/v1/search-suggester"
        );
        this.searchByUsecasePath = config.getProperty(
                "vrgo.search.proxy.usecase.path",
                "/search-proxy/v1/search-by-usecase"
        );
        this.pubSearchByUsecasePath = config.getProperty(
                "vrgo.search.proxy.pub.usecase.path",
                "/search-proxy/pub/v1/search-by-usecase"
        );
        this.subgenrePreferencePath = config.getProperty(
                "vrgo.search.proxy.subgenre.preference.path",
                "/search-proxy/v1/subgenre-preference"
        );
    }

    // ── Search Proxy endpoints ────────────────────────────────────────────────

    /**
     * GET /search-proxy/v1/global-content-search — full-catalogue search for a logged-in subscriber.
     *
     * @param searchQuery the search keyword (maps to {@code search} query param)
     * @param offset      zero-based pagination offset
     * @param limit       maximum number of results to return
     */
    public Response globalContentSearchRaw(String searchQuery, int offset, int limit) {
        return vrgoGiven(false, null)
                .queryParam("search", searchQuery)
                .queryParam("offset", offset)
                .queryParam("limit", limit)
                .when()
                .get(globalContentSearchPath);
    }

    /**
     * GET /search-proxy/pub/v1/global-content-search — full-catalogue search for a guest user.
     * <p>
     * Uses the guest bearer token ({@code vrgo.search.proxy.guest.bearer.token} /
     * {@code VRGO_GUEST_BEARER_TOKEN} / {@code BaseTest.VRGO_MANUAL_GUEST_BEARER_TOKEN}, falling back
     * to the standard logged-in token) and merges {@code vrgo.search.proxy.guest.header.*} header
     * overrides (cp_id, device_id, entitlementhash, entitlements, entitlementvalues, ottbouquetid,
     * profileid) on top of the base {@code vrgo.header.*} map.
     *
     * @param searchQuery the search keyword (maps to {@code search} query param)
     * @param offset      zero-based pagination offset
     * @param limit       maximum number of results to return
     */
    public Response guestGlobalContentSearchRaw(String searchQuery, int offset, int limit) {
        Map<String, String> guestOverrides = new LinkedHashMap<>(
                environmentConfig.propertiesWithPrefix(GUEST_HEADER_PREFIX)
        );
        return vrgoGiven(true, guestOverrides)
                .queryParam("search", searchQuery)
                .queryParam("offset", offset)
                .queryParam("limit", limit)
                .when()
                .get(pubGlobalContentSearchPath);
    }

    // ── Search Suggester endpoints ────────────────────────────────────────────

    /**
     * GET /search-proxy/v1/search-suggester — type-ahead suggestions for a logged-in subscriber.
     *
     * @param searchQuery partial keyword (maps to {@code search} query param)
     * @param page        page context, e.g. {@code search}
     * @param uc          use-case hint, e.g. {@code suggest}
     * @param offset      zero-based pagination offset
     * @param limit       maximum number of suggestions to return
     */
    public Response searchSuggesterRaw(String searchQuery, String page, String uc, int offset, int limit) {
        return vrgoGiven(false, null)
                .queryParam("search", searchQuery)
                .queryParam("page", page)
                .queryParam("uc", uc)
                .queryParam("offset", offset)
                .queryParam("limit", limit)
                .when()
                .get(searchSuggesterPath);
    }

    /**
     * GET /search-proxy/pub/v1/search-suggester — type-ahead suggestions for a guest user.
     * <p>
     * Uses the guest bearer token and merges {@code vrgo.search.proxy.guest.header.*} overrides
     * on top of the base {@code vrgo.header.*} map.
     *
     * @param searchQuery partial keyword (maps to {@code search} query param)
     * @param page        page context, e.g. {@code search}
     * @param uc          use-case hint, e.g. {@code suggest}
     * @param offset      zero-based pagination offset
     * @param limit       maximum number of suggestions to return
     */
    public Response guestSearchSuggesterRaw(String searchQuery, String page, String uc, int offset, int limit) {
        Map<String, String> guestOverrides = new LinkedHashMap<>(
                environmentConfig.propertiesWithPrefix(GUEST_HEADER_PREFIX)
        );
        return vrgoGiven(true, guestOverrides)
                .queryParam("search", searchQuery)
                .queryParam("page", page)
                .queryParam("uc", uc)
                .queryParam("offset", offset)
                .queryParam("limit", limit)
                .when()
                .get(pubSearchSuggesterPath);
    }

    // ── Search by use-case endpoints ─────────────────────────────────────────

    /**
     * GET /search-proxy/v1/search-by-usecase — returns curated results for a given use-case (logged-in user).
     *
     * @param usecase one of {@code trending_on_astro}, {@code popular_search}, {@code search}
     * @param page    page context, e.g. {@code search}
     * @param offset  zero-based pagination offset
     * @param limit   maximum number of results to return
     */
    public Response searchByUsecaseRaw(String usecase, String page, int offset, int limit) {
        return vrgoGiven(false, null)
                .queryParam("usecase", usecase)
                .queryParam("page", page)
                .queryParam("offset", offset)
                .queryParam("limit", limit)
                .when()
                .get(searchByUsecasePath);
    }

    /**
     * GET /search-proxy/pub/v1/search-by-usecase — returns curated results for a given use-case (guest user).
     * <p>
     * Uses the guest bearer token and merges {@code vrgo.search.proxy.guest.header.*} overrides.
     *
     * @param usecase one of {@code trending_on_astro}, {@code popular_search}, {@code search}
     * @param page    page context, e.g. {@code search}
     * @param offset  zero-based pagination offset
     * @param limit   maximum number of results to return
     */
    public Response guestSearchByUsecaseRaw(String usecase, String page, int offset, int limit) {
        Map<String, String> guestOverrides = new LinkedHashMap<>(
                environmentConfig.propertiesWithPrefix(GUEST_HEADER_PREFIX)
        );
        return vrgoGiven(true, guestOverrides)
                .queryParam("usecase", usecase)
                .queryParam("page", page)
                .queryParam("offset", offset)
                .queryParam("limit", limit)
                .when()
                .get(pubSearchByUsecasePath);
    }

    // ── Subgenre preference endpoints ────────────────────────────────────────

    /**
     * GET /search-proxy/v1/subgenre-preference — logged-in user's subgenre preference data.
     */
    public Response subgenrePreferenceRaw() {
        return vrgoGiven(false, null)
                .when()
                .get(subgenrePreferencePath);
    }

    /**
     * GET /search-proxy/v1/subgenre-preference — guest user's subgenre preference data.
     * <p>
     * Uses the guest bearer token and merges {@code vrgo.search.proxy.guest.header.*} overrides.
     * Both logged-in and guest calls share the same {@code /v1/} path.
     */
    public Response guestSubgenrePreferenceRaw() {
        Map<String, String> guestOverrides = new LinkedHashMap<>(
                environmentConfig.propertiesWithPrefix(GUEST_HEADER_PREFIX)
        );
        return vrgoGiven(true, guestOverrides)
                .when()
                .get(subgenrePreferencePath);
    }

    // ── Shared helper ─────────────────────────────────────────────────────────

    /**
     * @param useGuestToken  when {@code true}, resolves the guest bearer token first
     * @param headerOverrides optional extra or replacement headers applied on top of {@code vrgo.header.*}
     */
    private RequestSpecification vrgoGiven(boolean useGuestToken, Map<String, String> headerOverrides) {
        RequestSpecification r = given().spec(spec);

        String token;
        if (useGuestToken) {
            token = firstNonBlank(
                    System.getProperty("vrgo.search.proxy.guest.bearer.token"),
                    System.getenv("VRGO_GUEST_BEARER_TOKEN"),
                    VrgoAuthSupport.getBearerToken(environmentConfig)
            );
        } else {
            token = VrgoAuthSupport.getBearerToken(environmentConfig);
        }
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
                environmentConfig.getProperty("vrgo.x.api.key")
        );
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Set vrgo.x.api.key (properties / test base / env), VRGO_X_API_KEY, or -Dvrgo.x.api.key."
            );
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
