package com.automation.api.client;

import com.automation.api.config.EnvironmentConfig;
import com.automation.api.constants.ApiEndpoints;
import com.automation.api.model.auth.RegisterRequest;
import com.automation.api.model.auth.RegisterResponse;
import com.automation.api.model.common.ErrorResponse;
import io.restassured.response.Response;

import static io.restassured.RestAssured.given;

public class AuthApiClient extends BaseApiClient {

    public AuthApiClient(EnvironmentConfig config) {
        super(config);
    }

    public Response registerRaw(RegisterRequest body) {
        return given()
                .spec(spec)
                .body(body)
                .when()
                .post(ApiEndpoints.REGISTER);
    }

    public RegisterResponse registerSuccess(RegisterRequest body) {
        return registerRaw(body).then().statusCode(200).extract().as(RegisterResponse.class);
    }

    public ErrorResponse registerExpectError(RegisterRequest body, int status) {
        return registerRaw(body).then().statusCode(status).extract().as(ErrorResponse.class);
    }
}
