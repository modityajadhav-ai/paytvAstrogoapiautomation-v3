package com.automation.api.dataprovider;

import com.automation.api.model.user.CreateUserRequest;
import org.testng.annotations.DataProvider;

public final class UserDataProvider {

    private UserDataProvider() {
    }

    @DataProvider(name = "validCreateUser")
    public static Object[][] validCreateUser() {
        return new Object[][]{
                {new CreateUserRequest("Neo", "The One")},
                {new CreateUserRequest("Trinity", "Pilot")}
        };
    }

    @DataProvider(name = "userPages")
    public static Object[][] userPages() {
        return new Object[][]{{1}, {2}};
    }
}
