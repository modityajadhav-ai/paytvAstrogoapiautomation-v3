package com.automation.api.constants;

/**
 * Central place for API paths (relative to base URI).
 */
public final class ApiEndpoints {

    public static final String USERS = "/users";
    public static final String USER_BY_ID = "/users/{id}";
    public static final String REGISTER = "/register";
    public static final String LOGIN = "/login";

    private ApiEndpoints() {
    }
}
