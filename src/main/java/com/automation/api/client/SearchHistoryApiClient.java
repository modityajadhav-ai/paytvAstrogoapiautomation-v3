package com.automation.api.client;

import com.automation.api.auth.VrgoAuthSupport;
import com.automation.api.config.EnvironmentConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * VRGO subscriber-event search history (v1):
 * <ul>
 *   <li>POST   /v1/search-history            — body {@code {"searchQuery":"..."}}</li>
 *   <li>GET    /v1/search-history            — returns subscriber's search history</li>
 *   <li>DELETE /v1/search-history/{keyword}  — removes one entry by keyword path param</li>
 *   <li>DELETE /v1/search-history            — clears all search history</li>
 * </ul>
 * Auth and static headers match other VRGO clients: {@code vrgo.bearer.token} / {@code VRGO_BEARER_TOKEN},
 * {@code vrgo.x.api.key} / env, and {@code vrgo.header.*} from the active environment file.
 */
public class SearchHistoryApiClient extends BaseApiClient {

    private static final String VRGO_HEADER_PREFIX = "vrgo.header.";

    private final String searchHistoryPath;
    private final String searchHistoryDeleteKeywordPath;

    public SearchHistoryApiClient(EnvironmentConfig config) {
        super(config, config.getProperty("vrgo.base.url"));
        this.searchHistoryPath = config.getProperty(
                "vrgo.search.history.path",
                "/subscriber-event-service/v1/search-history"
        );
        this.searchHistoryDeleteKeywordPath = config.getProperty(
                "vrgo.search.history.delete.keyword.path",
                "/subscriber-event-service/v1/search-history/{keyword}"
        );
    }

    /**
     * POST a search query to the subscriber's search history.
     * Body: {@code {"searchQuery": "..."}}
     */
    public Response postSearchHistoryRaw(String searchQuery) {
        return vrgoGiven()
                .body(Map.of("searchQuery", searchQuery))
                .when()
                .post(searchHistoryPath);
    }

    /**
     * GET the full search history for the authenticated subscriber.
     */
    public Response getSearchHistoryRaw() {
        return vrgoGiven()
                .when()
                .get(searchHistoryPath);
    }

    /**
     * DELETE a single entry from search history by keyword path param.
     * Sends {@code {}} as the request body (matches the actual API contract).
     */
    public Response deleteSearchHistoryKeywordRaw(String keyword) {
        return vrgoGiven()
                .pathParam("keyword", keyword)
                .body(Map.of())
                .when()
                .delete(searchHistoryDeleteKeywordPath);
    }

    /**
     * DELETE all search history for the subscriber.
     * Sends {@code {}} as the request body (matches the actual API contract).
     */
    public Response deleteAllSearchHistoryRaw() {
        return vrgoGiven()
                .body(Map.of())
                .when()
                .delete(searchHistoryPath);
    }

    private RequestSpecification vrgoGiven() {
        RequestSpecification r = given().spec(spec);

        String token = VrgoAuthSupport.getBearerToken(environmentConfig);
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
                environmentConfig.getProperty("vrgo.x.api.key"));
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Set vrgo.x.api.key (properties / test base / env), VRGO_X_API_KEY, or -Dvrgo.x.api.key."
            );
        }
        r = r.header("x-api-key", apiKey);

        Map<String, String> staticHeaders = environmentConfig.propertiesWithPrefix(VRGO_HEADER_PREFIX);
        for (Map.Entry<String, String> e : staticHeaders.entrySet()) {
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
