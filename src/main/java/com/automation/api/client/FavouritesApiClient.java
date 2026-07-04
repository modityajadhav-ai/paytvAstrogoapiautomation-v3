package com.automation.api.client;

import com.automation.api.auth.VrgoAuthSupport;
import com.automation.api.config.EnvironmentConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * VRGO subscriber-event favourites / watchlist: GET list, GET favourite channels, POST add, DELETE by content id.
 * <p>
 * Auth and static headers match other VRGO clients: {@code vrgo.bearer.token} / {@code VRGO_BEARER_TOKEN},
 * {@code vrgo.x.api.key} / env, and {@code vrgo.header.*} from the active environment file.
 */
public class FavouritesApiClient extends BaseApiClient {

    private static final String VRGO_HEADER_PREFIX = "vrgo.header.";

    private final String favouritesPath;
    private final String favouritesDeletePath;
    private final String favouritesChannelsPath;

    public FavouritesApiClient(EnvironmentConfig config) {
        super(config, config.getProperty("vrgo.base.url"));
        this.favouritesPath = config.getProperty(
                "vrgo.favourites.path",
                "/subscriber-event-service/v3/favourites"
        );
        this.favouritesDeletePath = config.getProperty(
                "vrgo.favourites.delete.path",
                "/subscriber-event-service/v3/favourites/{contentId}"
        );
        this.favouritesChannelsPath = config.getProperty(
                "vrgo.favourites.channels.path",
                "/subscriber-event-service/v3/favourites/channels"
        );
    }

    /**
     * POST add favourite (subscriber-event), query {@code region}, JSON body {@code {"contentId":"...","contentType":"..."}}
     * (e.g. {@code MOVIE}, {@code VOD} for series, {@code LIVE} for channel, {@code BOXSET}).
     */
    public Response postFavouriteRaw(String region, Map<String, ?> body) {
        return vrgoGiven()
                .queryParam("region", region)
                .body(body)
                .when()
                .post(favouritesPath);
    }

    /**
     * GET subscriber favourites (subscriber-event), e.g. {@code contentTypes=LIVE,VOD}.
     */
    public Response getFavouritesRaw(int offset, int limit, String contentTypes, String region, boolean isEntitlementEnabled) {
        return vrgoGiven()
                .queryParam("offset", offset)
                .queryParam("limit", limit)
                .queryParam("contentTypes", contentTypes)
                .queryParam("region", region)
                .queryParam("isEntitlementEnabled", isEntitlementEnabled)
                .when()
                .get(favouritesPath);
    }

    /**
     * GET subscriber favourite channels only (subscriber-event {@code /v3/favourites/channels}).
     */
    public Response getFavouritesChannelsRaw() {
        return vrgoGiven()
                .when()
                .get(favouritesChannelsPath);
    }

    /**
     * DELETE one favourite by path content id; body {@code {}} as in the VRGO client.
     */
    public Response deleteFavouriteRaw(String favouriteContentId) {
        return vrgoGiven()
                .pathParam("contentId", favouriteContentId)
                .body(Map.of())
                .when()
                .delete(favouritesDeletePath);
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
