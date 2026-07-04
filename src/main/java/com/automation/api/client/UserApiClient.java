package com.automation.api.client;

import com.automation.api.config.EnvironmentConfig;
import com.automation.api.constants.ApiEndpoints;
import com.automation.api.model.user.CreateUserRequest;
import com.automation.api.model.user.CreateUserResponse;
import com.automation.api.model.user.UsersListResponse;
import io.restassured.response.Response;

/**
 * Example user API wrapper (ReqRes-compatible routes).
 */
public class UserApiClient extends BaseApiClient {

    public UserApiClient(EnvironmentConfig config) {
        super(config);
    }

    public Response getUsersRaw(int page) {
        return given()
                .spec(spec)
                .queryParam("page", page)
                .when()
                .get(ApiEndpoints.USERS);
    }

    public UsersListResponse getUsers(int page) {
        return getUsersRaw(page).then().statusCode(200).extract().as(UsersListResponse.class);
    }

    public Response getUserByIdRaw(int id) {
        return given()
                .spec(spec)
                .pathParam("id", id)
                .when()
                .get(ApiEndpoints.USER_BY_ID);
    }

    public Response createUserRaw(CreateUserRequest body) {
        return given()
                .spec(spec)
                .body(body)
                .when()
                .post(ApiEndpoints.USERS);
    }

    public CreateUserResponse createUser(CreateUserRequest body) {
        return createUserRaw(body).then().statusCode(201).extract().as(CreateUserResponse.class);
    }
}
