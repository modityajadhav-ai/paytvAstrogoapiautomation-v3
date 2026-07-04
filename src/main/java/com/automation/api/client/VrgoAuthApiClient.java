package com.automation.api.client;

import com.automation.api.auth.VrgoJwtUtils;
import com.automation.api.config.EnvironmentConfig;
import com.automation.api.model.auth.VrgoTokenResponse;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

/**
 * VRGO consumer-am token endpoint: exchanges a refresh token for a short-lived bearer JWT.
 * <p>
 * Configure {@code vrgo.auth.token.url} in the active environment file (full URL).
 * Refresh token: {@code VRGO_REFRESH_TOKEN}, {@code -Dvrgo.refresh.token}, or
 * {@code secrets/vrgo-auth.local.properties} for local runs.
 */
public class VrgoAuthApiClient {

    private static final String DEFAULT_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:refresh_token";

    private final EnvironmentConfig environmentConfig;
    private final String tokenUrl;
    private final String grantType;
    private final RequestSpecification spec;

    public VrgoAuthApiClient(EnvironmentConfig config) {
        this.environmentConfig = config;
        String url = config.getProperty("vrgo.auth.token.url");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "Set vrgo.auth.token.url in the active environment file (e.g. https://consumer-am.test.xp.irdeto.com/v1/auth/token)."
            );
        }
        this.tokenUrl = url.strip();
        this.grantType = config.getProperty("vrgo.auth.grant.type", DEFAULT_GRANT_TYPE).strip();

        RestAssuredConfig raConfig = RestAssuredConfig.config()
                .httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", config.getConnectionTimeoutMs())
                        .setParam("http.socket.timeout", config.getReadTimeoutMs()));

        RequestSpecBuilder builder = new RequestSpecBuilder()
                .setConfig(raConfig)
                .setContentType(ContentType.URLENC)
                .setAccept(ContentType.JSON)
                .addFilter(new RequestLoggingFilter())
                .addFilter(new ResponseLoggingFilter());

        String origin = config.getProperty("vrgo.header.origin");
        if (origin != null && !origin.isBlank()) {
            builder.addHeader("origin", origin.strip());
        }
        String referer = config.getProperty("vrgo.header.referer");
        if (referer != null && !referer.isBlank()) {
            builder.addHeader("referer", referer.strip());
        }
        String userAgent = config.getProperty("vrgo.header.user-agent");
        if (userAgent != null && !userAgent.isBlank()) {
            builder.addHeader("User-Agent", userAgent.strip());
        }

        this.spec = builder.build();
    }

    public Response tokenRaw(String refreshToken) {
        return given()
                .spec(spec)
                .formParam("grant_type", grantType)
                .formParam("refresh_token", refreshToken)
                .when()
                .post(tokenUrl);
    }

    public VrgoTokenResponse tokenSuccess(String refreshToken) {
        return tokenRaw(refreshToken).then().statusCode(200).extract().as(VrgoTokenResponse.class);
    }

    /**
     * Exchanges a refresh token for an access token without asserting HTTP status (safe for suite bootstrap).
     *
     * @return parsed response on HTTP 200 with {@code access_token}, otherwise {@code null}
     */
    /**
     * Tries the token as-is (full JWT from browser Response), then the opaque {@code refresh} claim if present.
     */
    public VrgoTokenResponse fetchAccessToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }
        String trimmed = refreshToken.strip();
        VrgoTokenResponse body = fetchAccessTokenOnce(trimmed);
        if (body != null) {
            return body;
        }
        String opaque = VrgoJwtUtils.extractClaimString(trimmed, "refresh");
        if (opaque != null && !opaque.isBlank() && !opaque.equals(trimmed)) {
            return fetchAccessTokenOnce(opaque);
        }
        return null;
    }

    private VrgoTokenResponse fetchAccessTokenOnce(String refreshToken) {
        Response response = tokenRaw(refreshToken);
        if (response.getStatusCode() != 200) {
            return null;
        }
        VrgoTokenResponse body = response.as(VrgoTokenResponse.class);
        if (body == null || body.getAccessToken() == null || body.getAccessToken().isBlank()) {
            return null;
        }
        return body;
    }

    /**
     * Profile token-exchange after refresh when the account JWT lacks {@code profileId}.
     */
    public Response tokenExchangeRaw(String subjectToken, String profileId, String profileType) {
        return given()
                .spec(spec)
                .formParam("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                .formParam("subject_token", subjectToken)
                .formParam("subject_token_type", "urn:ietf:params:oauth:token-type:access_token")
                .formParam("profile_id", profileId)
                .formParam("profile_type", profileType)
                .when()
                .post(tokenUrl);
    }

    public VrgoTokenResponse fetchProfileAccessToken(String subjectToken, String profileId, String profileType) {
        Response response = tokenExchangeRaw(subjectToken, profileId, profileType);
        if (response.getStatusCode() != 200) {
            return null;
        }
        VrgoTokenResponse body = response.as(VrgoTokenResponse.class);
        if (body == null || body.getAccessToken() == null || body.getAccessToken().isBlank()) {
            return null;
        }
        return body;
    }

    public EnvironmentConfig getEnvironmentConfig() {
        return environmentConfig;
    }
}
