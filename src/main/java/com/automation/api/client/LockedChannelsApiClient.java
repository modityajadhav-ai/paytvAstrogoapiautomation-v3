package com.automation.api.client;

import com.automation.api.auth.VrgoAuthSupport;
import com.automation.api.config.EnvironmentConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * VRGO subscriber-event locked channels (v3):
 * <ul>
 *   <li>POST   /v3/locked/channels                         — body {@code {"channelId":"..."}}</li>
 *   <li>GET    /v3/locked/channels                         — returns all locked channels</li>
 *   <li>GET    /v3/locked/channels/{channelCode}/status    — locked status for a channel code</li>
 *   <li>DELETE /v3/locked/channels/{channelId}             — unlock by channel UUID</li>
 * </ul>
 * Auth and static headers match other VRGO clients: {@code vrgo.bearer.token} / {@code VRGO_BEARER_TOKEN},
 * {@code vrgo.x.api.key} / env, and {@code vrgo.header.*} from the active environment file.
 */
public class LockedChannelsApiClient extends BaseApiClient {

    private static final String VRGO_HEADER_PREFIX = "vrgo.header.";

    private final String lockedChannelsPath;
    private final String lockedChannelsDeletePath;
    private final String lockedChannelsStatusPath;

    public LockedChannelsApiClient(EnvironmentConfig config) {
        super(config, config.getProperty("vrgo.base.url"));
        this.lockedChannelsPath = config.getProperty(
                "vrgo.locked.channels.path",
                "/subscriber-event-service/v3/locked/channels"
        );
        this.lockedChannelsDeletePath = config.getProperty(
                "vrgo.locked.channels.delete.path",
                "/subscriber-event-service/v3/locked/channels/{channelId}"
        );
        this.lockedChannelsStatusPath = config.getProperty(
                "vrgo.locked.channels.status.path",
                "/subscriber-event-service/v3/locked/channels/{channelCode}/status"
        );
    }

    /**
     * POST lock a channel for the authenticated profile.
     * Body: {@code {"channelId": "..."}}
     */
    public Response postLockChannelRaw(String channelId) {
        return vrgoGiven()
                .body(Map.of("channelId", channelId))
                .when()
                .post(lockedChannelsPath);
    }

    /**
     * GET all locked channels for the authenticated profile.
     */
    public Response getLockedChannelsRaw() {
        return vrgoGiven()
                .when()
                .get(lockedChannelsPath);
    }

    /**
     * GET locked status for a channel by its channel code (not UUID).
     */
    public Response getLockedChannelStatusRaw(String channelCode) {
        return vrgoGiven()
                .pathParam("channelCode", channelCode)
                .when()
                .get(lockedChannelsStatusPath);
    }

    /**
     * DELETE (unlock) a channel by channel UUID path param.
     */
    public Response deleteLockedChannelRaw(String channelId) {
        return vrgoGiven()
                .pathParam("channelId", channelId)
                .when()
                .delete(lockedChannelsDeletePath);
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
