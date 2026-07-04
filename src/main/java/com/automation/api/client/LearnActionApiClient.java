package com.automation.api.client;

import com.automation.api.auth.VrgoAuthSupport;
import com.automation.api.config.EnvironmentConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * VRGO Learn Action API client ({@code /learn-action/v1/learn}).
 * <p>
 * Sends a POST with {@code ?action=<action>} and a JSON body containing
 * {@code contentId} (channel-day EPG eventId), {@code contentType}, {@code sessionDuration},
 * {@code lastPosition}, {@code refUseCase}, and {@code primaryGenre}.
 * <p>
 * Endpoint path is driven by {@code vrgo.learn.action.path} in the active environment file.
 * Auth and static headers match other VRGO clients: {@code vrgo.bearer.token} /
 * {@code VRGO_BEARER_TOKEN}, {@code vrgo.x.api.key} / env, and {@code vrgo.header.*}.
 */
public class LearnActionApiClient extends BaseApiClient {

    private static final String VRGO_HEADER_PREFIX = "vrgo.header.";

    private final String learnActionPath;

    public LearnActionApiClient(EnvironmentConfig config) {
        super(config, config.getProperty("vrgo.base.url"));
        this.learnActionPath = config.getProperty(
                "vrgo.learn.action.path",
                "/learn-action/v1/learn"
        );
    }

    /**
     * POST a learn action event.
     *
     * @param action one of: {@code bookmark}, {@code clicked_on_search_result},
     *               {@code stop_watching}, {@code end_watching}, {@code start_watching},
     *               {@code purchased}, {@code download}, {@code record}
     * @param body   request payload; must include {@code contentId}, {@code contentType}, etc.
     */
    public Response postLearnActionRaw(String action, Map<String, Object> body) {
        return vrgoGiven()
                .queryParam("action", action)
                .body(body)
                .when()
                .post(learnActionPath);
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
