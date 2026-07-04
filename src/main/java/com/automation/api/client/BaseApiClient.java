package com.automation.api.client;

import com.automation.api.config.EnvironmentConfig;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared REST Assured configuration: base URI, timeouts, JSON defaults, logging filters.
 */
public abstract class BaseApiClient {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final EnvironmentConfig environmentConfig;
    protected final RequestSpecification spec;

    protected BaseApiClient(EnvironmentConfig config) {
        this(config, null);
    }

    /**
     * @param baseUriOverride if non-blank, used instead of {@link EnvironmentConfig#getBaseUrl()}
     */
    protected BaseApiClient(EnvironmentConfig config, String baseUriOverride) {
        this.environmentConfig = config;
        String baseUri = (baseUriOverride != null && !baseUriOverride.isBlank())
                ? baseUriOverride
                : config.getBaseUrl();
        RestAssuredConfig raConfig = RestAssuredConfig.config()
                .httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", config.getConnectionTimeoutMs())
                        .setParam("http.socket.timeout", config.getReadTimeoutMs()));
        this.spec = new RequestSpecBuilder()
                .setConfig(raConfig)
                .setBaseUri(baseUri)
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .addFilter(new RequestLoggingFilter())
                .addFilter(new ResponseLoggingFilter())
                .build();
    }

    protected RequestSpecification given() {
        return RestAssured.given().spec(spec);
    }
}
