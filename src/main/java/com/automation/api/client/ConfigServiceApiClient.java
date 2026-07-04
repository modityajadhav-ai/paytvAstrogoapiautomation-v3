package com.automation.api.client;

import com.automation.api.auth.VrgoAuthSupport;
import com.automation.api.config.EnvironmentConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * VRGO config-service: authenticated GETs against paths from environment properties (same base URL and auth as other VRGO clients).
 * <p>
 * Auth and static headers match other VRGO clients: {@code vrgo.bearer.token} / {@code VRGO_BEARER_TOKEN},
 * {@code vrgo.x.api.key} / env, and {@code vrgo.header.*} from the active environment file.
 * {@link #getPlatformConfigsRaw(String)} and {@link #getAvatarsRaw(String, int, int)} replace the {@code platform}
 * header from properties when a non-blank {@code platformValue} is passed.
 * {@link #getOperatorConfigsRaw()} merges {@code vrgo.config.service.operator.profile.id} and
 * {@code vrgo.config.service.operator.header.*} onto the usual {@code vrgo.header.*} map.
 * {@link #getImageConfigsRaw()} does the same for {@code vrgo.config.service.image.*}.
 */
public class ConfigServiceApiClient extends BaseApiClient {

    private static final String VRGO_HEADER_PREFIX = "vrgo.header.";
    private static final String OPERATOR_HEADER_PREFIX = "vrgo.config.service.operator.header.";
    private static final String IMAGE_HEADER_PREFIX = "vrgo.config.service.image.header.";

    private final String smokePath;
    private final String platformConfigsPath;
    private final String avatarsPath;
    private final String operatorConfigsPath;
    private final String imageConfigsPath;

    public ConfigServiceApiClient(EnvironmentConfig config) {
        super(config, config.getProperty("vrgo.base.url"));
        this.smokePath = config.getProperty("vrgo.config.service.smoke.path", "");
        this.platformConfigsPath = config.getProperty(
                "vrgo.config.service.platform.configs.path",
                "/config-service/pub/v1/platform-configs"
        );
        this.avatarsPath = config.getProperty(
                "vrgo.config.service.avatars.path",
                "/config-service/v1/avatars"
        );
        this.operatorConfigsPath = config.getProperty(
                "vrgo.config.service.operator.configs.path",
                "/config-service/pub/v1/operator-configs"
        );
        this.imageConfigsPath = config.getProperty(
                "vrgo.config.service.image.configs.path",
                "/config-service/pub/v1/image-configs"
        );
    }

    /**
     * GET {@code vrgo.config.service.smoke.path} (relative to {@code vrgo.base.url}) for a minimal connectivity check.
     *
     * @throws IllegalStateException when {@code vrgo.config.service.smoke.path} is blank
     */
    public Response getSmokeRaw() {
        if (smokePath == null || smokePath.isBlank()) {
            throw new IllegalStateException(
                    "Set vrgo.config.service.smoke.path in the active environment file (path relative to vrgo.base.url)."
            );
        }
        return vrgoGiven(null, null).when().get(smokePath.strip());
    }

    /**
     * GET {@code vrgo.config.service.platform.configs.path} with {@code platform} header set to {@code platformValue}
     * (other {@code vrgo.header.*} values, including {@code environmentcode}, {@code language}, etc., come from the environment file).
     */
    public Response getPlatformConfigsRaw(String platformValue) {
        if (platformValue == null || platformValue.isBlank()) {
            throw new IllegalStateException("platformValue must be non-blank.");
        }
        return vrgoGiven(platformValue.strip(), null).when().get(platformConfigsPath.strip());
    }

    /**
     * GET {@code vrgo.config.service.avatars.path} with {@code limit} and {@code offset} query params.
     * When {@code platformValue} is null or blank, the {@code platform} header from {@code vrgo.header.*} is used unchanged.
     */
    public Response getAvatarsRaw(String platformValue, int limit, int offset) {
        String platformOverride = (platformValue == null || platformValue.isBlank()) ? null : platformValue.strip();
        return vrgoGiven(platformOverride, null)
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .when()
                .get(avatarsPath.strip());
    }

    /**
     * GET {@code vrgo.config.service.operator.configs.path} (operator-configs). Applies {@code vrgo.config.service.operator.profile.id}
     * as {@code profileid} when set, then any {@code vrgo.config.service.operator.header.*} entries (prefix stripped = header name),
     * on top of {@code vrgo.header.*} and auth headers.
     */
    public Response getOperatorConfigsRaw() {
        Map<String, String> overrides = new LinkedHashMap<>(environmentConfig.propertiesWithPrefix(OPERATOR_HEADER_PREFIX));
        String operatorProfileId = environmentConfig.getProperty("vrgo.config.service.operator.profile.id");
        if (operatorProfileId != null && !operatorProfileId.isBlank()) {
            overrides.put("profileid", operatorProfileId.strip());
        }
        return vrgoGiven(null, overrides).when().get(operatorConfigsPath.strip());
    }

    /**
     * GET {@code vrgo.config.service.image.configs.path} (image-configs). Applies {@code vrgo.config.service.image.profile.id}
     * as {@code profileid} when set, then any {@code vrgo.config.service.image.header.*} entries, on top of {@code vrgo.header.*} and auth.
     */
    public Response getImageConfigsRaw() {
        Map<String, String> overrides = new LinkedHashMap<>(environmentConfig.propertiesWithPrefix(IMAGE_HEADER_PREFIX));
        String imageProfileId = environmentConfig.getProperty("vrgo.config.service.image.profile.id");
        if (imageProfileId != null && !imageProfileId.isBlank()) {
            overrides.put("profileid", imageProfileId.strip());
        }
        return vrgoGiven(null, overrides).when().get(imageConfigsPath.strip());
    }

    /**
     * @param platformOverride when non-null and non-blank, replaces any {@code platform} entry from {@code vrgo.header.*}
     * @param headerOverrides  optional extra or replacement headers (e.g. {@code profileid} for operator-configs)
     */
    private RequestSpecification vrgoGiven(String platformOverride, Map<String, String> headerOverrides) {
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

        Map<String, String> staticHeaders = new LinkedHashMap<>(
                environmentConfig.propertiesWithPrefix(VRGO_HEADER_PREFIX)
        );
        if (platformOverride != null && !platformOverride.isBlank()) {
            staticHeaders.put("platform", platformOverride);
        }
        if (headerOverrides != null) {
            for (Map.Entry<String, String> e : headerOverrides.entrySet()) {
                if (e.getValue() != null && !e.getValue().isBlank()) {
                    staticHeaders.put(e.getKey(), e.getValue().strip());
                }
            }
        }
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
