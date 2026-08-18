package com.automation.api.client;

import com.automation.api.auth.VrgoAuthSupport;
import com.automation.api.config.EnvironmentConfig;
import com.automation.api.util.TokenGeneratorSessionSupport;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * VRGO Token Generator API client ({@code POST /token-generator-service/v2/ctg} and
 * {@code POST /token-generator-service/v2/uwm}).
 * <p>
 * Sends only the headers observed on working browser requests (not the full {@code vrgo.header.*}
 * set used by other VRGO clients). {@code entitlementhash} comes from the bearer JWT.
 */
public class TokenGeneratorApiClient extends BaseApiClient {

    private static final String VRGO_HEADER_PREFIX = "vrgo.header.";

    /**
     * Headers sent by the browser for token-generator; excludes catalogueids, contenttype,
     * isentitlementenabled, ottbouquetid, entitlements, entitlementvalues.
     */
    private static final List<String> ALLOWED_HEADERS = List.of(
            "accept",
            "accept-language",
            "cp_id",
            "device_id",
            "environmentcode",
            "language",
            "languagecode",
            "local",
            "origin",
            "platform",
            "priority",
            "profileid",
            "profiletype",
            "referer",
            "requestcount",
            "sec-ch-ua",
            "sec-ch-ua-mobile",
            "sec-ch-ua-platform",
            "sec-fetch-dest",
            "sec-fetch-mode",
            "sec-fetch-site",
            "session_id",
            "tenant_identifier",
            "user-agent"
    );

    private final String ctgPath;
    private final String uwmPath;
    private final boolean defaultIsStatic;

    public TokenGeneratorApiClient(EnvironmentConfig config) {
        super(config, config.getProperty("vrgo.base.url"));
        this.ctgPath = config.getProperty(
                "vrgo.token.generator.path",
                "/token-generator-service/v2/ctg"
        );
        this.uwmPath = config.getProperty(
                "vrgo.token.generator.uwm.path",
                "/token-generator-service/v2/uwm"
        );
        this.defaultIsStatic = Boolean.parseBoolean(
                config.getProperty("vrgo.token.generator.is.static", "false")
        );
    }

    public Response postCtgRaw(Map<String, Object> body) {
        return postCtgRaw(defaultIsStatic, body);
    }

    public Response postCtgRaw(boolean isStatic, Map<String, Object> body) {
        return postRaw(ctgPath, isStatic, body);
    }

    public Response postUwmRaw(Map<String, Object> body) {
        return postUwmRaw(defaultIsStatic, body);
    }

    public Response postUwmRaw(boolean isStatic, Map<String, Object> body) {
        return postRaw(uwmPath, isStatic, body);
    }

    private Response postRaw(String path, boolean isStatic, Map<String, Object> body) {
        return vrgoGiven()
                .queryParam("isStatic", isStatic)
                .body(body)
                .when()
                .post(path);
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
                environmentConfig.getProperty("vrgo.x.api.key")
        );
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Set vrgo.x.api.key (properties / test base / env), VRGO_X_API_KEY, or -Dvrgo.x.api.key."
            );
        }
        r = r.header("x-api-key", apiKey);

        Map<String, String> configuredHeaders = environmentConfig.propertiesWithPrefix(VRGO_HEADER_PREFIX);
        for (String allowedHeader : ALLOWED_HEADERS) {
            String value = configuredHeaders.get(allowedHeader);
            if (value != null && !value.isBlank()) {
                r = r.header(allowedHeader, value);
            }
        }

        String entitlementHash = resolveEntitlementHash();
        if (entitlementHash != null && !entitlementHash.isBlank()) {
            r = r.header("entitlementhash", entitlementHash.strip());
        }
        return r;
    }

    /**
     * {@code entitlementhash} must match {@code sessionInfo.entitlements} in the request body.
     * The bearer JWT claim is authoritative for refresh-token runs; a static override is available
     * for replaying a captured browser curl via {@code vrgo.token.generator.entitlementhash}.
     */
    private String resolveEntitlementHash() {
        boolean preferConfig = Boolean.parseBoolean(firstNonBlank(
                System.getProperty("vrgo.token.generator.entitlementhash.prefer.config"),
                environmentConfig.getProperty("vrgo.token.generator.entitlementhash.prefer.config"),
                "false"
        ));
        if (preferConfig) {
            return firstNonBlank(
                    System.getProperty("vrgo.token.generator.entitlementhash"),
                    environmentConfig.getProperty("vrgo.token.generator.entitlementhash"),
                    environmentConfig.getProperty("vrgo.header.entitlementhash"),
                    TokenGeneratorSessionSupport.entitlementHashFromBearer()
            );
        }
        return firstNonBlank(
                TokenGeneratorSessionSupport.entitlementHashFromBearer(),
                System.getProperty("vrgo.token.generator.entitlementhash"),
                environmentConfig.getProperty("vrgo.token.generator.entitlementhash"),
                environmentConfig.getProperty("vrgo.header.entitlementhash")
        );
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
