package com.automation.api.client;

import com.automation.api.auth.VrgoAuthSupport;
import com.automation.api.config.EnvironmentConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * VRGO Homescreen Proxy API client.
 * <p>
 * Endpoint paths and configurable values are driven by
 * {@code vrgo.homescreen.proxy.*} keys in the active environment file.
 */
public class HomescreenProxyApiClient extends BaseApiClient {

    private static final String VRGO_HEADER_PREFIX = "vrgo.header.";

    private final String railHierarchyPath;
    private final String appCodeHeader;

    public HomescreenProxyApiClient(EnvironmentConfig config) {
        super(config, config.getProperty("vrgo.base.url"));
        this.railHierarchyPath = config.getProperty(
                "vrgo.homescreen.proxy.rail.hierarchy.path",
                "/homescreen-proxy/pub/v1/rail-hierarchy"
        );
        this.appCodeHeader = config.getProperty("vrgo.homescreen.proxy.header.appcode", "PAYTV");
    }

    /**
     * GET {@code /homescreen-proxy/pub/v1/rail-hierarchy?pageId=...&page=...&offset=...&limit=...}.
     *
     * @param pageId page identifier from menu {@code linkToPage}
     * @param page   page context, e.g. {@code home}
     * @param offset zero-based pagination offset
     * @param limit  maximum number of results to return
     */
    public Response getRailHierarchyRaw(String pageId, String page, int offset, int limit) {
        if (pageId == null || pageId.isBlank()) {
            throw new IllegalStateException("pageId must be non-blank.");
        }
        if (page == null || page.isBlank()) {
            throw new IllegalStateException("page must be non-blank.");
        }
        return vrgoGiven()
                .queryParam("pageId", pageId.strip())
                .queryParam("page", page.strip())
                .queryParam("offset", offset)
                .queryParam("limit", limit)
                .when()
                .get(railHierarchyPath);
    }

    private RequestSpecification vrgoGiven() {
        RequestSpecification r = given().spec(spec);

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

        if (appCodeHeader != null && !appCodeHeader.isBlank()) {
            r = r.header("appCode", appCodeHeader.strip());
        }

        for (Map.Entry<String, String> e : environmentConfig.propertiesWithPrefix(VRGO_HEADER_PREFIX).entrySet()) {
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
