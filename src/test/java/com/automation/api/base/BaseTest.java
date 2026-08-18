package com.automation.api.base;

import com.automation.api.auth.VrgoAuthSecretsLoader;
import com.automation.api.auth.VrgoGuestTokenHolder;
import com.automation.api.auth.VrgoTokenHolder;
import com.automation.api.client.AuthApiClient;
import com.automation.api.client.ConfigServiceApiClient;
import com.automation.api.client.ContentDetailApiClient;
import com.automation.api.client.ContinueWatchApiClient;
import com.automation.api.client.FavouritesApiClient;
import com.automation.api.client.HomescreenApiClient;
import com.automation.api.client.HomescreenProxyApiClient;
import com.automation.api.client.LastTunedChannelApiClient;
import com.automation.api.client.LearnActionApiClient;
import com.automation.api.client.LockedChannelsApiClient;
import com.automation.api.client.RecommendationProxyApiClient;
import com.automation.api.client.SearchHistoryApiClient;
import com.automation.api.client.TokenGeneratorApiClient;
import com.automation.api.client.VRSearchProxyApiClient;
import com.automation.api.client.UserApiClient;
import com.automation.api.config.EnvironmentConfig;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Listeners;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Bootstraps configuration and API clients once per suite.
 * <p>
 * VRGO bearer tokens are managed automatically by {@link VrgoTokenHolder} — no manual paste required.
 * Configure per-environment secrets in {@code secrets/vrgo-auth.<env>.local.properties}, or
 * {@code VRGO_REFRESH_TOKEN_<ENV>} / {@code VRGO_REFRESH_TOKEN} in Jenkins/GitLab CI.
 */
@Listeners(com.automation.api.listeners.TestListener.class)
public abstract class BaseTest {

    /**
     * Optional override for {@code vrgo.x.api.key}. Test profile already sets a static key in environment files.
     */
    public static String VRGO_MANUAL_X_API_KEY = "";

    /**
     * Optional bootstrap guest access JWT (expires ~300s). Auto-renewed via guest browser recovery when Playwright is installed.
     */
    public static String VRGO_MANUAL_GUEST_BEARER_TOKEN = "";

    protected static EnvironmentConfig config;

    protected static UserApiClient userApi;
    protected static AuthApiClient authApi;
    protected static ContinueWatchApiClient continueWatchApi;
    protected static FavouritesApiClient favouritesApi;
    protected static HomescreenApiClient homescreenApi;
    protected static ContentDetailApiClient contentDetailApi;
    protected static ConfigServiceApiClient configServiceApi;
    protected static SearchHistoryApiClient searchHistoryApi;
    protected static LockedChannelsApiClient lockedChannelsApi;
    protected static LastTunedChannelApiClient lastTunedChannelApi;
    protected static VRSearchProxyApiClient vrSearchProxyApi;
    protected static RecommendationProxyApiClient recommendationProxyApi;
    protected static HomescreenProxyApiClient homescreenProxyApi;
    protected static LearnActionApiClient learnActionApi;
    protected static TokenGeneratorApiClient tokenGeneratorApi;

    protected static final String VRGO_AUTH_SKIP_MESSAGE =
            "Configure VRGO_REFRESH_TOKEN_<ENV> or secrets/vrgo-auth.<env>.local.properties for the active profile.";

    protected static boolean isVrgoAuthConfigured() {
        return config != null && com.automation.api.auth.VrgoAuthSupport.hasBearerCredential(config);
    }

    @BeforeSuite(alwaysRun = true)
    public static void suiteSetup() {
        RestAssured.filters(new AllureRestAssured());

        VrgoAuthSecretsLoader.loadLocalSecretsIfPresent();
        applyOptionalVrgoOverrides();

        config = EnvironmentConfig.load();
        VrgoTokenHolder.initialize(config);
        if (isGuestTestsEnabled()) {
            VrgoGuestTokenHolder.initialize(config);
        }

        userApi = new UserApiClient(config);
        authApi = new AuthApiClient(config);

        String vrgoBase = config.getProperty("vrgo.base.url");
        if (vrgoBase != null && !vrgoBase.isBlank()) {
            continueWatchApi = new ContinueWatchApiClient(config);
            favouritesApi = new FavouritesApiClient(config);
            homescreenApi = new HomescreenApiClient(config);
            contentDetailApi = new ContentDetailApiClient(config);
            configServiceApi = new ConfigServiceApiClient(config);
            searchHistoryApi = new SearchHistoryApiClient(config);
            lockedChannelsApi = new LockedChannelsApiClient(config);
            lastTunedChannelApi = new LastTunedChannelApiClient(config);
            vrSearchProxyApi = new VRSearchProxyApiClient(config);
            recommendationProxyApi = new RecommendationProxyApiClient(config);
            homescreenProxyApi = new HomescreenProxyApiClient(config);
            learnActionApi = new LearnActionApiClient(config);
            tokenGeneratorApi = new TokenGeneratorApiClient(config);
        }

        writeAllureEnvironment();
    }

    private static void writeAllureEnvironment() {
        String resultsDir = System.getProperty("allure.results.directory", "target/allure-results");
        File dir = new File(resultsDir);
        dir.mkdirs();
        File envFile = new File(dir, "environment.properties");
        try (PrintWriter pw = new PrintWriter(envFile, StandardCharsets.UTF_8)) {
            pw.println("Environment=" + System.getProperty("env", "test").toUpperCase());
            pw.println("Base.URL=" + config.getBaseUrl());
            String vrgoBase = config.getProperty("vrgo.base.url");
            if (vrgoBase != null && !vrgoBase.isBlank()) {
                pw.println("VRGO.Base.URL=" + vrgoBase);
            }
            pw.println("Java.Version=" + System.getProperty("java.version"));
            pw.println("OS=" + System.getProperty("os.name"));
        } catch (Exception e) {
            LoggerFactory.getLogger(BaseTest.class).warn("Could not write allure environment.properties", e);
        }
    }

    private static void applyOptionalVrgoOverrides() {
        if (VRGO_MANUAL_X_API_KEY != null && !VRGO_MANUAL_X_API_KEY.isBlank()) {
            System.setProperty("vrgo.x.api.key", VRGO_MANUAL_X_API_KEY.strip());
        }
        if (VRGO_MANUAL_GUEST_BEARER_TOKEN != null && !VRGO_MANUAL_GUEST_BEARER_TOKEN.isBlank()) {
            System.setProperty("vrgo.search.proxy.guest.bearer.token", VRGO_MANUAL_GUEST_BEARER_TOKEN.strip());
        }
    }

    private static boolean isGuestTestsEnabled() {
        String flag = firstNonBlank(
                System.getProperty("vrgo.guest.tests.enabled"),
                System.getenv("VRGO_GUEST_TESTS_ENABLED"),
                "true"
        );
        return !"false".equalsIgnoreCase(flag);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return null;
    }
}
