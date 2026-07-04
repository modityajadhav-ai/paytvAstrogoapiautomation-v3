package com.automation.api.client;

import com.automation.api.auth.VrgoAuthSupport;
import com.automation.api.config.EnvironmentConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * VRGO subscriber-event last tuned channel:
 * <ul>
 *   <li>POST   /last-tuned-channel              — body {@code {"channelId":"..."}}</li>
 *   <li>GET    /last-tuned-channel              — returns last tuned channel status</li>
 *   <li>DELETE /last-tuned-channel/{channelId}   — remove channel from last tuned</li>
 * </ul>
 * Auth and static headers match other VRGO clients: {@code vrgo.bearer.token} / {@code VRGO_BEARER_TOKEN},
 * {@code vrgo.x.api.key} / env, and {@code vrgo.header.*} from the active environment file.
 */
public class LastTunedChannelApiClient extends BaseApiClient {

    private static final String VRGO_HEADER_PREFIX = "vrgo.header.";

    private final String lastTunedChannelPath;
    private final String lastTunedChannelDeletePath;

    public LastTunedChannelApiClient(EnvironmentConfig config) {
        super(config, config.getProperty("vrgo.base.url"));
        this.lastTunedChannelPath = config.getProperty(
                "vrgo.last.tuned.channel.path",
                "/subscriber-event-service/last-tuned-channel"
        );
        this.lastTunedChannelDeletePath = config.getProperty(
                "vrgo.last.tuned.channel.delete.path",
                "/subscriber-event-service/last-tuned-channel/{channelId}"
        );
    }

    /**
     * POST add a channel to last tuned for the authenticated profile.
     * Body: {@code {"channelId": "..."}}
     */
    public Response postLastTunedChannelRaw(String channelId) {
        return vrgoGiven()
                .body(Map.of("channelId", channelId))
                .when()
                .post(lastTunedChannelPath);
    }

    /**
     * GET last tuned channel status for the authenticated profile.
     */
    public Response getLastTunedChannelRaw() {
        return vrgoGiven()
                .when()
                .get(lastTunedChannelPath);
    }

    /**
     * DELETE remove a channel from last tuned by channel UUID path param.
     */
    public Response deleteLastTunedChannelRaw(String channelId) {
        return vrgoGiven()
                .pathParam("channelId", channelId)
                .when()
                .delete(lastTunedChannelDeletePath);
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
