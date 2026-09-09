package com.automation.api.auth;

import com.automation.api.config.Environment;
import com.automation.api.config.EnvironmentConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.Geolocation;
import com.microsoft.playwright.options.HttpCredentials;
import com.microsoft.playwright.options.LoadState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Headless browser re-login when refresh tokens are revoked (device removed, logout, etc.).
 */
public final class VrgoBrowserAuthRecovery {

    private static final Logger LOG = LoggerFactory.getLogger(VrgoBrowserAuthRecovery.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_DEVICE_EVICTIONS = 1;

    private VrgoBrowserAuthRecovery() {
    }

    public static boolean isConfigured() {
        return credentials() != null && isEnabled();
    }

    public static boolean isEnabled() {
        String flag = firstNonBlank(
                System.getProperty("vrgo.auth.browser.recovery.enabled"),
                System.getenv("VRGO_BROWSER_AUTH_RECOVERY_ENABLED"),
                "true"
        );
        return !"false".equalsIgnoreCase(flag);
    }

    public static String recoverRefreshToken(EnvironmentConfig config) {
        Credentials credentials = credentials();
        if (credentials == null) {
            LOG.warn("Browser auth recovery skipped: set vrgo.auth.username and vrgo.auth.password in secrets");
            return null;
        }
        if (!isEnabled()) {
            LOG.warn("Browser auth recovery disabled (vrgo.auth.browser.recovery.enabled=false)");
            return null;
        }

        String browserUrl = firstNonBlank(
                config.getProperty("vrgo.auth.browser.url"),
                config.getProperty("vrgo.header.origin"),
                "https://web.vrgo.test.xp.irdeto.com/hubHome"
        );
        String webOrigin = extractOrigin(browserUrl);
        String authTokenPath = "/v1/auth/token";
        boolean evictOnLimit = evictDeviceOnLimitEnabled();
        boolean headed = isHeadedMode();
        long timeoutMs = parseLong(
                firstNonBlank(
                        System.getProperty("vrgo.auth.browser.timeout.ms"),
                        System.getenv("VRGO_BROWSER_AUTH_TIMEOUT_MS")
                ),
                180_000L
        );

        AtomicReference<String> capturedRefresh = new AtomicReference<>();
        AtomicInteger evictionAttempts = new AtomicInteger();
        AtomicBoolean allowAuthTokenResponses = new AtomicBoolean(false);

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(!headed)
                            .setArgs(Arrays.asList(
                                    "--disable-notifications",
                                    "--disable-popup-blocking"
                            ))
            );
            BrowserContext context = newBrowserContext(browser, config, false);
            context.addInitScript(
                    "(() => {"
                            + "if (navigator.geolocation) {"
                            + "  navigator.geolocation.getCurrentPosition = (ok) => ok({"
                            + "    coords: { latitude: 3.139, longitude: 101.687, accuracy: 50 },"
                            + "    timestamp: Date.now()"
                            + "  });"
                            + "}"
                            + "})();"
            );
            context.grantPermissions(
                    List.of("geolocation"),
                    new BrowserContext.GrantPermissionsOptions().setOrigin(webOrigin)
            );
            Page page = context.newPage();
            page.route("**/v1/auth/token", route -> {
                if (allowAuthTokenResponses.get()) {
                    route.resume();
                } else {
                    route.abort();
                }
            });
            page.onResponse(response -> captureRefreshFromResponse(
                    response, authTokenPath, capturedRefresh, allowAuthTokenResponses
            ));

            LOG.info("Browser auth recovery: opening {} (headed={})", browserUrl, headed);
            context.clearCookies();
            page.navigate(browserUrl, new Page.NavigateOptions().setTimeout(timeoutMs));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            dismissAllLandingPopups(page);
            beginSubscriberLoginFlow(page, allowAuthTokenResponses);

            runLoginFlow(
                    page,
                    context,
                    browserUrl,
                    credentials,
                    evictOnLimit,
                    capturedRefresh,
                    evictionAttempts,
                    allowAuthTokenResponses,
                    timeoutMs
            );

            if (capturedRefresh.get() == null) {
                LOG.warn("No token yet — waiting up to {}s for /v1/auth/token response", timeoutMs / 1000);
                waitForAuthTokenResponse(page, authTokenPath, timeoutMs, capturedRefresh, allowAuthTokenResponses);
            }

            String lastUrl = page.url();
            String lastTitle = page.title();
            if (capturedRefresh.get() == null) {
                saveDebugScreenshot(page, "vrgo-browser-recovery-failed.png");
            }

            browser.close();

            String token = capturedRefresh.get();
            if (token != null) {
                LOG.info(
                        "Browser auth recovery captured refresh_token from /v1/auth/token — "
                                + "persisting to secrets and token cache"
                );
                VrgoAuthSecretsWriter.persistRefreshToken(token);
            } else {
                LOG.error(
                        "Browser auth recovery finished but no refresh_token was captured. "
                                + "Last URL: {}. Title: {}. "
                                + "Run scripts\\verify-browser-recovery.bat with VRGO_BROWSER_HEADED=true to debug.",
                        lastUrl,
                        lastTitle
                );
            }
            return token;
        } catch (Throwable e) {
            LOG.error("Browser auth recovery failed: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Headless "browse as guest" — captures the short-lived guest Bearer JWT from outgoing VRGO API
     * requests (guest sessions do not call {@code POST /v1/auth/token}).
     */
    public static String recoverGuestAccessToken(EnvironmentConfig config) {
        if (!isEnabled()) {
            LOG.warn("Guest browser recovery disabled (vrgo.auth.browser.recovery.enabled=false)");
            return null;
        }

        String[] browserUrls = resolveGuestBrowserUrls(config);
        boolean headed = isHeadedMode();
        long timeoutMs = parseLong(
                firstNonBlank(
                        System.getProperty("vrgo.auth.browser.timeout.ms"),
                        System.getenv("VRGO_BROWSER_AUTH_TIMEOUT_MS")
                ),
                180_000L
        );

        AtomicReference<String> capturedAccess = new AtomicReference<>();

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(!headed)
                            .setArgs(Arrays.asList(
                                    "--disable-notifications",
                                    "--disable-popup-blocking"
                            ))
            );
            BrowserContext context = newBrowserContext(browser, config, true);
            context.addInitScript(
                    "(() => {"
                            + "if (navigator.geolocation) {"
                            + "  navigator.geolocation.getCurrentPosition = (ok) => ok({"
                            + "    coords: { latitude: 3.139, longitude: 101.687, accuracy: 50 },"
                            + "    timestamp: Date.now()"
                            + "  });"
                            + "}"
                            + "})();"
            );
            for (String browserUrl : browserUrls) {
                String webOrigin = extractOrigin(browserUrl);
                if (webOrigin != null && !webOrigin.isBlank()) {
                    context.grantPermissions(
                            List.of("geolocation"),
                            new BrowserContext.GrantPermissionsOptions().setOrigin(webOrigin)
                    );
                }
            }

            Page page = context.newPage();
            page.onRequest(request -> captureGuestAccessFromRequest(request, config, capturedAccess));
            page.onResponse(response -> captureGuestAccessFromResponse(response, config, capturedAccess));

            long deadline = System.currentTimeMillis() + timeoutMs;
            for (String browserUrl : browserUrls) {
                if (capturedAccess.get() != null || System.currentTimeMillis() >= deadline) {
                    break;
                }
                long remainingMs = Math.max(5_000L, deadline - System.currentTimeMillis());
                LOG.info("Guest browser recovery: opening {} (headed={}, captureHosts={})",
                        browserUrl, headed, describeGuestCaptureHosts(config));
                try {
                    page.navigate(browserUrl, new Page.NavigateOptions().setTimeout(remainingMs));
                } catch (Exception e) {
                    LOG.warn("Guest browser recovery could not open {}: {}", browserUrl, e.getMessage());
                    continue;
                }
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                waitForPageContent(page, Math.min(remainingMs, 30_000L));
                page.waitForTimeout(2_000);
                try {
                    page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(30_000));
                } catch (Exception e) {
                    LOG.debug("Guest landing network idle wait skipped: {}", e.getMessage());
                }
                dismissAllLandingPopups(page);

                while (capturedAccess.get() == null && System.currentTimeMillis() < deadline) {
                    dismissAllLandingPopups(page);
                    if (clickGuestEntryPointIfPresent(page)) {
                        LOG.info("Clicked guest entry on {}", page.url());
                    } else {
                        clickLoginEntryPointIfPresent(page);
                        dismissAllLandingPopups(page);
                        clickGuestEntryPointIfPresent(page);
                    }
                    page.waitForTimeout(2_000);
                    try {
                        page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10_000));
                    } catch (Exception e) {
                        LOG.debug("Guest flow network idle wait skipped: {}", e.getMessage());
                    }
                }
                if (capturedAccess.get() != null) {
                    break;
                }
            }

            if (capturedAccess.get() == null) {
                saveDebugScreenshot(page, "vrgo-guest-browser-recovery-failed.png");
            }

            String lastUrl = page.url();
            String lastTitle = page.title();
            browser.close();

            String token = capturedAccess.get();
            if (token != null) {
                LOG.info("Guest browser recovery captured guest access token");
            } else {
                LOG.error(
                        "Guest browser recovery finished but no guest Bearer token was captured from API traffic. "
                                + "Tried URLs: {}. Last URL: {}. Title: {}. "
                                + "Set VRGO_BROWSER_HEADED=true to debug guest entry selectors.",
                        String.join(", ", browserUrls),
                        lastUrl,
                        lastTitle
                );
            }
            return token;
        } catch (Throwable e) {
            LOG.error("Guest browser recovery failed: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Guest browse URLs to try. Test stack uses {@code /hubMovies}; Astro stacks often expose guest on
     * {@code /hubMovies} even when subscriber login uses {@code /hubHome}.
     */
    private static String[] resolveGuestBrowserUrls(EnvironmentConfig config) {
        Set<String> urls = new LinkedHashSet<>();
        addGuestBrowserUrl(urls, config.getProperty("vrgo.guest.browser.url"));
        addGuestBrowserUrl(urls, config.getProperty("vrgo.auth.browser.url"));
        String authUrl = config.getProperty("vrgo.auth.browser.url");
        if (authUrl != null && authUrl.contains("/hubHome")) {
            addGuestBrowserUrl(urls, authUrl.replace("/hubHome", "/hubMovies"));
        }
        addGuestBrowserUrl(urls, config.getProperty("vrgo.header.origin"));
        if (Environment.current() == Environment.TEST) {
            addGuestBrowserUrl(urls, "https://web.vrgo.test.xp.irdeto.com/hubMovies");
        }
        return urls.toArray(String[]::new);
    }

    private static void addGuestBrowserUrl(Set<String> urls, String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        urls.add(url.strip());
    }

    private static void waitForPageContent(Page page, long timeoutMs) {
        try {
            page.waitForFunction(
                    "() => document.body && document.body.innerText.trim().length > 20",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(timeoutMs)
            );
        } catch (Exception e) {
            LOG.debug("Guest page content wait skipped: {}", e.getMessage());
        }
    }

    private static String resolveApiHost(EnvironmentConfig config) {
        String base = config.getProperty("vrgo.base.url", "https://api.vrgo.test.xp.irdeto.com");
        try {
            return java.net.URI.create(base.strip()).getHost();
        } catch (Exception e) {
            return "api.vrgo.test.xp.irdeto.com";
        }
    }

    private static void captureGuestAccessFromRequest(
            com.microsoft.playwright.Request request,
            EnvironmentConfig config,
            AtomicReference<String> capturedAccess
    ) {
        if (!matchesGuestCaptureUrl(request.url(), config)) {
            return;
        }
        String auth = request.headerValue("authorization");
        if (auth == null || auth.isBlank()) {
            auth = request.headerValue("Authorization");
        }
        storeGuestAccessIfValid(auth, capturedAccess, request.url());
    }

    private static void captureGuestAccessFromResponse(
            Response response,
            EnvironmentConfig config,
            AtomicReference<String> capturedAccess
    ) {
        if (!matchesGuestCaptureUrl(response.url(), config) || response.status() < 200 || response.status() >= 300) {
            return;
        }
        try {
            JsonNode json = MAPPER.readTree(response.text());
            JsonNode access = json.get("access_token");
            if (access != null && !access.asText().isBlank()) {
                storeGuestAccessIfValid(access.asText(), capturedAccess, response.url());
            }
        } catch (Exception e) {
            LOG.debug("Could not parse guest API response from {}: {}", response.url(), e.getMessage());
        }
    }

    private static boolean matchesGuestCaptureUrl(String url, EnvironmentConfig config) {
        if (url == null || url.isBlank()) {
            return false;
        }
        for (String host : resolveGuestCaptureHosts(config)) {
            if (url.contains(host)) {
                return true;
            }
        }
        return false;
    }

    private static String[] resolveGuestCaptureHosts(EnvironmentConfig config) {
        String configured = config.getProperty("vrgo.guest.api.capture.hosts");
        if (configured != null && !configured.isBlank()) {
            return Arrays.stream(configured.split(","))
                    .map(String::strip)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
        }
        String apiHost = resolveApiHost(config);
        String tokenHost = extractHost(config.getProperty("vrgo.auth.token.url"));
        String baseHost = extractHost(config.getProperty("vrgo.base.url"));
        return Arrays.stream(new String[] {apiHost, tokenHost, baseHost})
                .filter(h -> h != null && !h.isBlank())
                .distinct()
                .toArray(String[]::new);
    }

    private static String describeGuestCaptureHosts(EnvironmentConfig config) {
        return String.join(", ", resolveGuestCaptureHosts(config));
    }

    private static String extractHost(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            return java.net.URI.create(url.strip()).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private static void storeGuestAccessIfValid(String rawToken, AtomicReference<String> capturedAccess, String sourceUrl) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String token = rawToken.strip();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).strip();
        }
        if (!VrgoJwtUtils.isGuestToken(token)) {
            return;
        }
        capturedAccess.set(token);
        LOG.info("Captured guest access token from {}", sourceUrl);
    }

    private static boolean clickGuestEntryPointIfPresent(Page page) {
        String[] guestLabels = {
                "Continue As Guest",
                "Browse as Guest",
                "Continue as Guest",
                "Browse As Guest",
                "Browse as guest",
                "Guest",
                "Continue without signing in",
                "Continue without login",
                "Skip login",
                "Teruskan sebagai Tetamu",
                "Layari sebagai Tetamu"
        };
        for (String label : guestLabels) {
            Locator button = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(label)
            );
            if (button.count() > 0 && button.first().isVisible()) {
                LOG.info("Clicking guest entry: '{}'", label);
                button.first().click(new Locator.ClickOptions().setTimeout(15_000));
                page.waitForTimeout(1_500);
                return true;
            }
            Locator link = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.LINK,
                    new Page.GetByRoleOptions().setName(label)
            );
            if (link.count() > 0 && link.first().isVisible()) {
                LOG.info("Clicking guest link: '{}'", label);
                link.first().click();
                page.waitForTimeout(1_500);
                return true;
            }
        }
        Locator guestText = page.locator("button:has-text('Guest'), a:has-text('Guest')");
        if (guestText.count() > 0 && guestText.first().isVisible()) {
            LOG.info("Clicking guest element via text selector");
            guestText.first().click(new Locator.ClickOptions().setTimeout(15_000));
            page.waitForTimeout(1_500);
            return true;
        }
        return false;
    }

    private static void runLoginFlow(
            Page page,
            BrowserContext context,
            String browserUrl,
            Credentials credentials,
            boolean evictOnLimit,
            AtomicReference<String> capturedRefresh,
            AtomicInteger evictionAttempts,
            AtomicBoolean allowAuthTokenResponses,
            long timeoutMs
    ) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        int sessionResetAttempts = 0;

        while (capturedRefresh.get() == null && System.currentTimeMillis() < deadline) {
            if (page.isClosed()) {
                LOG.error(
                        "Browser window was closed before subscriber refresh_token was captured. "
                                + "Keep the Playwright browser open until Astro ID login completes."
                );
                break;
            }

            try {
                if (isDeviceLimitPage(page)) {
                    allowAuthTokenResponses.set(true);
                    if (evictionAttempts.get() >= MAX_DEVICE_EVICTIONS) {
                        LOG.warn("Already evicted one device — waiting for login to complete");
                        handlePostLoginSteps(page);
                        page.waitForTimeout(5_000);
                    } else if (!evictOneDeviceOnLimitPage(page, evictOnLimit, evictionAttempts)) {
                        break;
                    } else {
                        handlePostLoginSteps(page);
                    }
                    continue;
                }

                if (isOnAuthProvider(page)) {
                    waitForManualCaptchaIfPresent(page, credentials, allowAuthTokenResponses, capturedRefresh, deadline);
                }

                dismissAllLandingPopups(page);

                if (!allowAuthTokenResponses.get()) {
                    if (beginSubscriberLoginFlow(page, allowAuthTokenResponses)) {
                        continue;
                    }
                    if (sessionResetAttempts < 2 && isGuestBrowsingPage(page)) {
                        resetWebSession(page, context, browserUrl, allowAuthTokenResponses);
                        sessionResetAttempts++;
                        continue;
                    }
                }

                if (!isOnAuthProvider(page)) {
                    if (clickLoginEntryPointIfPresent(page)) {
                        allowAuthTokenResponses.set(true);
                    }
                }

                if (trySubmitCredentials(page, credentials)) {
                    allowAuthTokenResponses.set(true);
                    LOG.info("Submitted credentials on {}", page.url());
                }

                handlePostLoginSteps(page);

                page.waitForTimeout(2_000);
                try {
                    page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10_000));
                } catch (Exception e) {
                    LOG.debug("Network idle wait skipped: {}", e.getMessage());
                }
            } catch (com.microsoft.playwright.PlaywrightException e) {
                if (page.isClosed() || String.valueOf(e.getMessage()).contains("closed")) {
                    LOG.error(
                            "Browser closed during subscriber login flow. Do not close the window manually; "
                                    + "click Login With Astro ID and complete sign-in."
                    );
                    break;
                }
                throw e;
            }
        }
    }

    /**
     * Blocks guest auto-login by aborting {@code /v1/auth/token} until the Astro ID entry point is clicked.
     */
    private static boolean beginSubscriberLoginFlow(Page page, AtomicBoolean allowAuthTokenResponses) {
        if (allowAuthTokenResponses.get()) {
            return false;
        }
        dismissAllLandingPopups(page);
        if (isOnAuthProvider(page)) {
            allowAuthTokenResponses.set(true);
            LOG.info("Auth provider page detected — allowing /v1/auth/token responses");
            return true;
        }
        if (clickLoginEntryPointIfPresent(page)) {
            allowAuthTokenResponses.set(true);
            LOG.info("Subscriber login entry clicked — allowing /v1/auth/token responses");
            return true;
        }
        return false;
    }

    private static boolean isGuestBrowsingPage(Page page) {
        String url = page.url().toLowerCase();
        if (url.contains("/hubmovies") || url.contains("/hubtv") || url.contains("/hubkids")) {
            return true;
        }
        Locator guestBrowse = page.locator(
                "button:has-text('Browse as Guest'), a:has-text('Browse as Guest'), "
                        + "button:has-text('Continue As Guest'), a:has-text('Continue As Guest')"
        );
        return guestBrowse.count() == 0 && !isOnAuthProvider(page) && !clickableLoginEntryExists(page);
    }

    private static boolean clickableLoginEntryExists(Page page) {
        String[] loginLabels = {
                "Login With Astro ID",
                "Login with Astro ID",
                "Log in",
                "Sign in",
                "Login"
        };
        for (String label : loginLabels) {
            Locator button = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(label)
            );
            if (button.count() > 0 && button.first().isVisible()) {
                return true;
            }
            Locator link = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.LINK,
                    new Page.GetByRoleOptions().setName(label)
            );
            if (link.count() > 0 && link.first().isVisible()) {
                return true;
            }
        }
        Locator astroLogin = page.locator("button:has-text('Astro ID'), a:has-text('Astro ID')");
        return astroLogin.count() > 0 && astroLogin.first().isVisible();
    }

    private static void resetWebSession(
            Page page,
            BrowserContext context,
            String browserUrl,
            AtomicBoolean allowAuthTokenResponses
    ) {
        LOG.warn(
                "Guest session detected on {} — clearing cookies/storage and reloading {}",
                page.url(),
                browserUrl
        );
        allowAuthTokenResponses.set(false);
        context.clearCookies();
        try {
            page.evaluate("() => { localStorage.clear(); sessionStorage.clear(); }");
        } catch (Exception e) {
            LOG.debug("Could not clear web storage: {}", e.getMessage());
        }
        page.navigate(browserUrl, new Page.NavigateOptions().setTimeout(60_000));
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        dismissAllLandingPopups(page);
        beginSubscriberLoginFlow(page, allowAuthTokenResponses);
    }

    private static boolean signOutGuestSessionIfPresent(Page page) {
        openAccountMenuIfPresent(page);
        String[] signOutLabels = {"Sign Out", "Sign out", "Log Out", "Log out", "Logout"};
        for (String label : signOutLabels) {
            Locator button = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(label)
            );
            if (button.count() > 0 && button.first().isVisible()) {
                LOG.info("Signing out guest session via '{}'", label);
                button.first().click(new Locator.ClickOptions().setTimeout(10_000));
                page.waitForTimeout(1_500);
                return true;
            }
            Locator link = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.LINK,
                    new Page.GetByRoleOptions().setName(label)
            );
            if (link.count() > 0 && link.first().isVisible()) {
                LOG.info("Signing out guest session via link '{}'", label);
                link.first().click(new Locator.ClickOptions().setTimeout(10_000));
                page.waitForTimeout(1_500);
                return true;
            }
        }
        return false;
    }

    private static void openAccountMenuIfPresent(Page page) {
        Locator userMenu = page.locator("button[aria-label='User action menu']:visible");
        if (userMenu.count() > 0 && userMenu.first().isVisible()) {
            LOG.debug("Opening user action menu");
            userMenu.first().click(new Locator.ClickOptions().setForce(true).setTimeout(5_000));
            page.waitForTimeout(500);
            return;
        }
        String[] menuLabels = {"Account", "Profile", "My Account"};
        for (String label : menuLabels) {
            Locator button = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(label)
            );
            if (button.count() > 0 && button.first().isVisible()) {
                LOG.debug("Opening account/menu control: '{}'", label);
                button.first().click(new Locator.ClickOptions().setForce(true).setTimeout(5_000));
                page.waitForTimeout(500);
                return;
            }
        }
        Locator hamburger = page.locator("button[aria-label*='Menu' i]:visible, [class*='hamburger']:visible");
        if (hamburger.count() > 0 && hamburger.first().isVisible()) {
            LOG.debug("Opening navigation menu");
            hamburger.first().click(new Locator.ClickOptions().setForce(true).setTimeout(5_000));
            page.waitForTimeout(500);
        }
    }

    private static boolean isDeviceLimitPage(Page page) {
        for (Page openPage : page.context().pages()) {
            if (isDeviceLimitPageOn(openPage)) {
                return true;
            }
        }
        return isDeviceLimitPageOn(page);
    }

    private static boolean isDeviceLimitPageOn(Page page) {
        Locator limitHeading = page.locator("text=Device limit reached");
        return limitHeading.count() > 0 && limitHeading.first().isVisible();
    }

    private static Page resolveDeviceLimitPage(Page page) {
        for (Page openPage : page.context().pages()) {
            if (isDeviceLimitPageOn(openPage)) {
                return openPage;
            }
        }
        return page;
    }

    private static Locator findDeviceLimitLogoutButtons(Page page) {
        Page target = resolveDeviceLimitPage(page);
        Locator byRole = target.getByRole(
                com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Log Out")
        );
        if (byRole.count() > 0) {
            return byRole;
        }
        return target.locator(
                "button[class*='logoutButton'], button[name='Log Out'], button:has-text('Log Out')"
        );
    }

    private static boolean isRecaptchaVisible(Page page) {
        Page target = resolveLoginPage(page);
        return target.locator("iframe[title*='recaptcha' i], iframe[src*='recaptcha']").count() > 0;
    }

    private static void waitForManualCaptchaIfPresent(
            Page page,
            Credentials credentials,
            AtomicBoolean allowAuthTokenResponses,
            AtomicReference<String> capturedRefresh,
            long deadline
    ) {
        if (!isRecaptchaVisible(page)) {
            return;
        }
        if (!isHeadedMode()) {
            LOG.error(
                    "reCAPTCHA on Astro ID login requires a visible browser. "
                            + "Set vrgo.auth.browser.headed=true in secrets/vrgo-auth.test.local.properties "
                            + "or VRGO_BROWSER_HEADED=true, then complete the CAPTCHA manually."
            );
            return;
        }
        LOG.warn(
                "reCAPTCHA detected on Astro ID login — complete the challenge in the browser window, "
                        + "then click Log In. Waiting up to {}s…",
                Math.max(1L, (deadline - System.currentTimeMillis()) / 1000L)
        );
        allowAuthTokenResponses.set(true);
        while (System.currentTimeMillis() < deadline
                && capturedRefresh.get() == null
                && isRecaptchaVisible(page)
                && !isDeviceLimitPage(page)) {
            page.waitForTimeout(2_000);
            if (!isRecaptchaVisible(page)) {
                trySubmitCredentials(page, credentials);
            }
        }
    }

    private static boolean isOnAuthProvider(Page page) {
        String url = page.url().toLowerCase();
        return url.contains("pink.cat") || url.contains("consumer-am") || url.contains("/login");
    }

    private static void handlePostLoginSteps(Page page) {
        String[] profileLabels = {"Continue", "Adult", "ADULT", "Default Profile", "Select Profile"};
        for (String label : profileLabels) {
            Locator btn = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(label)
            );
            if (btn.count() > 0 && btn.first().isVisible()) {
                LOG.info("Clicking post-login button: '{}'", label);
                btn.first().click();
                page.waitForTimeout(1_500);
                return;
            }
        }
    }

    private static boolean clickLoginEntryPointIfPresent(Page page) {
        if (clickVisibleLoginLocator(page, page.locator(
                "[role='menuitem']:has-text('Astro ID'), "
                        + "[role='menu'] :text('Login With Astro ID'), "
                        + "[role='menu'] :text('Login with Astro ID'), "
                        + ".headerActionMenu-module__guestDropdown :text('Astro ID')"
        ))) {
            return true;
        }

        String[] loginLabels = {
                "Login With Astro ID",
                "Login with Astro ID",
                "Log in with Astro ID",
                "Log in",
                "Sign in",
                "Login"
        };
        for (String label : loginLabels) {
            Locator button = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(label)
            );
            if (clickVisibleLoginLocator(page, button)) {
                LOG.info("Clicking login entry: '{}'", label);
                return true;
            }
            Locator link = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.LINK,
                    new Page.GetByRoleOptions().setName(label)
            );
            if (clickVisibleLoginLocator(page, link)) {
                LOG.info("Clicking login link: '{}'", label);
                return true;
            }
            Locator menuItem = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.MENUITEM,
                    new Page.GetByRoleOptions().setName(label)
            );
            if (clickVisibleLoginLocator(page, menuItem)) {
                LOG.info("Clicking login menu item: '{}'", label);
                return true;
            }
        }

        Locator astroLogin = page.locator(
                "button:has-text('Astro ID'):visible, a:has-text('Astro ID'):visible, "
                        + "[role='button']:has-text('Astro ID'):visible"
        );
        if (clickVisibleLoginLocator(page, astroLogin)) {
            LOG.info("Clicking login entry via Astro ID text selector");
            return true;
        }

        Locator userMenu = page.locator("button[aria-label='User action menu']:visible");
        if (userMenu.count() > 0 && userMenu.first().isVisible()) {
            LOG.info("Opening user action menu to reach Login With Astro ID");
            userMenu.first().click(new Locator.ClickOptions().setForce(true).setTimeout(10_000));
            page.waitForTimeout(800);
            if (clickVisibleLoginLocator(page, page.locator(
                    "[role='menuitem']:has-text('Astro ID'), "
                            + "[role='menu'] :text('Login With Astro ID'), "
                            + "[role='menu'] :text('Login with Astro ID')"
            ))) {
                LOG.info("Clicked Login With Astro ID from user action menu");
                return true;
            }
        }
        return false;
    }

    private static boolean clickVisibleLoginLocator(Page page, Locator locator) {
        if (locator.count() == 0 || !locator.first().isVisible()) {
            return false;
        }
        locator.first().click(new Locator.ClickOptions().setForce(true).setTimeout(15_000));
        page.waitForTimeout(1_500);
        return true;
    }

    private static boolean trySubmitCredentials(Page page, Credentials credentials) {
        Page target = resolveLoginPage(page);
        if (!isOnAuthProvider(target)) {
            return false;
        }

        waitForAstroIdLoginForm(target);

        Locator identifier = findIdentifierField(target);
        Locator password = findPasswordField(target);

        if (identifier == null && password == null) {
            LOG.debug("Astro ID login fields not found on {}", target.url());
            return false;
        }

        boolean entered = false;

        if (identifier != null && identifier.isVisible()) {
            String current = safeInputValue(identifier);
            if (current == null || current.isBlank()) {
                fillLoginField(identifier, credentials.username());
                LOG.info("Entered Astro ID username on {}", target.url());
                entered = true;
            }
        }

        if (password != null && password.isVisible()) {
            String currentPassword = safeInputValue(password);
            if (currentPassword == null || currentPassword.isBlank()) {
                fillLoginField(password, credentials.password());
                LOG.info("Entered Astro ID password on {}", target.url());
                entered = true;
                if (clickLoginSubmitButton(target)) {
                    LOG.info("Clicked LOG IN on {}", target.url());
                    return true;
                }
                return entered;
            }
            LOG.debug("Astro ID password already entered on {} — waiting for redirect", target.url());
            return false;
        }

        if (entered && identifier != null) {
            return clickLoginSubmitButton(target);
        }
        return false;
    }

    private static void fillLoginField(Locator field, String value) {
        field.click(new Locator.ClickOptions().setTimeout(10_000));
        field.fill("");
        field.fill(value);
    }

    private static String safeInputValue(Locator field) {
        try {
            return field.inputValue();
        } catch (Exception e) {
            return "";
        }
    }

    private static Locator findPasswordField(Page page) {
        Locator[] candidates = {
                page.getByLabel("Password", new Page.GetByLabelOptions().setExact(false)),
                page.locator("input[type='password']:visible"),
                page.locator("input[name='password']:visible"),
                page.locator("input[autocomplete='current-password']:visible")
        };
        for (Locator candidate : candidates) {
            if (candidate.count() > 0 && candidate.first().isVisible()) {
                return candidate.first();
            }
        }
        return null;
    }

    private static boolean clickLoginSubmitButton(Page target) {
        String[] submitLabels = {"LOG IN", "Log In", "Log in", "Sign in", "Continue", "Next"};
        for (String label : submitLabels) {
            Locator button = target.getByRole(
                    com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(label)
            );
            if (button.count() > 0 && button.first().isVisible()) {
                button.first().click(new Locator.ClickOptions().setForce(true).setTimeout(10_000));
                return true;
            }
        }

        Locator submit = target.locator(
                "button[type='submit']:visible, input[type='submit']:visible, "
                        + "button:has-text('LOG IN'):visible, button:has-text('Log In'):visible"
        ).first();
        if (submit.count() > 0 && submit.isVisible()) {
            submit.click(new Locator.ClickOptions().setForce(true).setTimeout(10_000));
            return true;
        }
        target.keyboard().press("Enter");
        return true;
    }

    private static Page resolveLoginPage(Page page) {
        for (Page openPage : page.context().pages()) {
            String url = openPage.url().toLowerCase();
            if (url.contains("pink.cat") || url.contains("consumer-am") || url.contains("/login")) {
                return openPage;
            }
        }
        for (Page openPage : page.context().pages()) {
            if (openPage.locator("input[type='password']:visible").count() > 0) {
                return openPage;
            }
        }
        return page;
    }

    private static Locator findIdentifierField(Page page) {
        Locator[] candidates = {
                page.getByLabel("Email / Mobile Number", new Page.GetByLabelOptions().setExact(false)),
                page.getByLabel("Email", new Page.GetByLabelOptions().setExact(false)),
                page.getByLabel("Mobile Number", new Page.GetByLabelOptions().setExact(false)),
                page.getByPlaceholder("Email", new Page.GetByPlaceholderOptions().setExact(false)),
                page.locator("input[type='email']:visible"),
                page.locator("input[name='identifier']:visible"),
                page.locator("input[name='email']:visible"),
                page.locator("input[name='username']:visible"),
                page.locator("input[autocomplete='username']:visible"),
                page.locator("input[id*='email' i]:visible"),
                page.locator("input[id*='identifier' i]:visible")
        };
        for (Locator candidate : candidates) {
            if (candidate.count() > 0 && candidate.first().isVisible()) {
                return candidate.first();
            }
        }
        Locator textInputs = page.locator("input[type='text']:visible");
        if (textInputs.count() > 0) {
            return textInputs.first();
        }
        return null;
    }

    private static void waitForAstroIdLoginForm(Page target) {
        try {
            target.getByLabel("Email / Mobile Number", new Page.GetByLabelOptions().setExact(false))
                    .first()
                    .waitFor(new Locator.WaitForOptions().setTimeout(15_000));
        } catch (Exception e) {
            try {
                target.locator("input[type='password']:visible, input[type='email']:visible")
                        .first()
                        .waitFor(new Locator.WaitForOptions().setTimeout(5_000));
            } catch (Exception ignored) {
                LOG.debug("Astro ID login form wait: {}", e.getMessage());
            }
        }
    }

    /**
     * Removes exactly one device: the last row on the device-limit page, then confirms "Remove Device".
     */
    private static boolean evictOneDeviceOnLimitPage(
            Page page,
            boolean evictOnLimit,
            AtomicInteger evictionAttempts
    ) {
        Page limitPage = resolveDeviceLimitPage(page);
        if (!isDeviceLimitPageOn(limitPage)) {
            return false;
        }
        if (!evictOnLimit) {
            throw new IllegalStateException(
                    "Device limit reached during browser recovery. "
                            + "Set vrgo.auth.browser.evict.device.on.limit=true to remove one device automatically."
            );
        }
        if (evictionAttempts.get() >= MAX_DEVICE_EVICTIONS) {
            return false;
        }

        if (isModalVisible(limitPage)) {
            if (confirmRemoveDeviceModal(limitPage)) {
                evictionAttempts.set(1);
                waitForModalToClose(limitPage);
                LOG.info("Confirmed Remove Device on open modal (single eviction)");
                return true;
            }
            LOG.error("Device-limit modal is open but 'Remove Device' could not be clicked");
            return false;
        }

        Locator logoutButtons = findDeviceLimitLogoutButtons(limitPage);
        int count = logoutButtons.count();
        if (count == 0) {
            throw new IllegalStateException("Device limit page shown but no Log Out buttons found");
        }

        int lastIndex = count - 1;
        LOG.warn("Device limit reached — clicking Log Out on last registered device (index {} of {})", lastIndex, count);
        Locator lastDeviceLogout = logoutButtons.nth(lastIndex);
        lastDeviceLogout.scrollIntoViewIfNeeded();
        lastDeviceLogout.click(new Locator.ClickOptions().setForce(true).setTimeout(15_000));

        page.waitForTimeout(2_000);
        if (!confirmRemoveDeviceModal(limitPage)) {
            LOG.error("Log Out clicked on last device but 'Remove Device' confirm was not found");
            return false;
        }

        evictionAttempts.set(1);
        waitForModalToClose(limitPage);
        limitPage.waitForTimeout(5_000);
        LOG.info("Removed one device from registered devices list — continuing login");
        return true;
    }

    private static void waitForModalToClose(Page page) {
        try {
            page.locator(".modals-main.display-block").first().waitFor(
                    new Locator.WaitForOptions().setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN)
                            .setTimeout(15_000)
            );
        } catch (Exception e) {
            LOG.debug("Modal close wait: {}", e.getMessage());
        }
    }

    private static boolean isModalVisible(Page page) {
        Locator modal = page.locator(".modals-main.display-block");
        return modal.count() > 0 && modal.first().isVisible();
    }

    /**
     * VRGO modal buttons are typically: [0] Remove Device, [1] Cancel — never click Cancel.
     */
    private static boolean confirmRemoveDeviceModal(Page page) {
        Locator modal = page.locator(".modals-main.display-block, .modals-main");
        if (modal.count() == 0 || !modal.first().isVisible()) {
            return false;
        }

        Locator removeDeviceBtn = modal.first().getByRole(
                com.microsoft.playwright.options.AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Remove Device")
        );
        if (removeDeviceBtn.count() > 0 && removeDeviceBtn.first().isVisible()) {
            removeDeviceBtn.first().click(new Locator.ClickOptions().setForce(true).setTimeout(15_000));
            LOG.info("Clicked 'Remove Device' on confirmation modal");
            return true;
        }

        Locator buttons = modal.first().locator("button");
        int count = buttons.count();
        for (int i = 0; i < count; i++) {
            String label = buttons.nth(i).innerText().trim();
            LOG.info("Device-limit modal button[{}]: '{}'", i, label);
        }

        for (int i = 0; i < count; i++) {
            String lower = buttons.nth(i).innerText().toLowerCase();
            if (isCancelLabel(lower)) {
                continue;
            }
            if (lower.contains("remove device") || lower.contains("log out")
                    || lower.contains("confirm") || lower.contains("yes")) {
                buttons.nth(i).click(new Locator.ClickOptions().setForce(true).setTimeout(15_000));
                LOG.info("Clicked modal confirm: '{}'", buttons.nth(i).innerText().trim());
                return true;
            }
        }

        return clickRemoveDeviceViaJs(page);
    }

    private static boolean isCancelLabel(String lower) {
        return lower.contains("cancel") || lower.equals("close") || lower.equals("no");
    }

    private static boolean clickRemoveDeviceViaJs(Page page) {
        Object clicked = page.evaluate(
                "() => {"
                        + "const modal = document.querySelector('.modals-main.display-block')"
                        + "  || document.querySelector('.modals-main');"
                        + "if (!modal) return false;"
                        + "const buttons = [...modal.querySelectorAll('button')];"
                        + "const confirm = buttons.find(b => /remove device|log out|confirm|yes/i.test(b.textContent || ''));"
                        + "if (!confirm) return false;"
                        + "confirm.click();"
                        + "return true;"
                        + "}"
        );
        if (Boolean.TRUE.equals(clicked)) {
            LOG.info("Clicked 'Remove Device' via JavaScript");
            return true;
        }
        return false;
    }

    private static void dismissAllLandingPopups(Page page) {
        dismissSitePromptsIfPresent(page);
        dismissCookieBannerIfPresent(page);
    }

    /** Astro GO landing page: notification prompt ("Nah, pass") and similar overlays. */
    private static void dismissSitePromptsIfPresent(Page page) {
        String[] dismissLabels = {
                "Nah, pass",
                "Nah pass",
                "No thanks",
                "Not now",
                "Maybe later",
                "Skip",
                "Dismiss"
        };
        for (String label : dismissLabels) {
            Locator btn = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(label)
            );
            if (btn.count() > 0 && btn.first().isVisible()) {
                LOG.info("Dismissing site prompt: '{}'", label);
                btn.first().click(new Locator.ClickOptions().setTimeout(5_000));
                page.waitForTimeout(500);
                return;
            }
        }
        Locator nahBtns = page.locator("button:has-text('Nah')");
        if (nahBtns.count() > 0 && nahBtns.first().isVisible()) {
            LOG.info("Dismissing site prompt via Nah button");
            nahBtns.first().click(new Locator.ClickOptions().setTimeout(5_000));
        }
    }

    private static void dismissCookieBannerIfPresent(Page page) {
        String[] dismissLabels = {"Accept", "Accept All", "Got it", "OK", "Close"};
        for (String label : dismissLabels) {
            Locator btn = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(label)
            );
            if (btn.count() > 0 && btn.first().isVisible()) {
                btn.first().click(new Locator.ClickOptions().setTimeout(3_000));
                return;
            }
        }
    }

    private static void waitForAuthTokenResponse(
            Page page,
            String authTokenPath,
            long timeoutMs,
            AtomicReference<String> capturedRefresh,
            AtomicBoolean allowAuthTokenResponses
    ) {
        try {
            Response response = page.waitForResponse(
                    r -> r.url().contains(authTokenPath) && r.status() == 200,
                    new Page.WaitForResponseOptions().setTimeout(timeoutMs),
                    () -> page.waitForTimeout(500)
            );
            captureRefreshFromResponse(response, authTokenPath, capturedRefresh, allowAuthTokenResponses);
        } catch (Exception e) {
            LOG.debug("Timed out waiting for auth token response: {}", e.getMessage());
        }
    }

    private static void captureRefreshFromResponse(
            Response response,
            String authTokenPath,
            AtomicReference<String> capturedRefresh,
            AtomicBoolean allowAuthTokenResponses
    ) {
        if (!response.url().contains(authTokenPath) || response.status() != 200) {
            return;
        }
        if (!allowAuthTokenResponses.get()) {
            LOG.debug("Ignoring /v1/auth/token response until Astro ID login starts");
            return;
        }
        try {
            JsonNode json = MAPPER.readTree(response.text());
            JsonNode refresh = json.get("refresh_token");
            if (refresh != null && !refresh.asText().isBlank()) {
                String token = refresh.asText().strip();
                if (VrgoJwtUtils.isGuestToken(token)) {
                    LOG.warn(
                            "Ignoring guest refresh_token from {} — complete Login With Astro ID "
                                    + "(not Browse as Guest). Subscriber recovery uses vrgo.auth.browser.url (hubHome).",
                            response.url()
                    );
                    return;
                }
                capturedRefresh.set(token);
                LOG.info("Captured refresh_token from {}", response.url());
            }
        } catch (Exception e) {
            LOG.debug("Could not parse auth token response: {}", e.getMessage());
        }
    }

    private static void saveDebugScreenshot(Page page, String fileName) {
        try {
            Path path = Paths.get(fileName);
            page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(true));
            LOG.warn("Saved browser recovery debug screenshot: {}", path.toAbsolutePath());
        } catch (Exception e) {
            LOG.debug("Could not save screenshot: {}", e.getMessage());
        }
    }

    private static boolean isHeadedMode() {
        String flag = firstNonBlank(
                System.getProperty("vrgo.auth.browser.headed"),
                System.getenv("VRGO_BROWSER_HEADED")
        );
        if (flag == null || flag.isBlank()) {
            return false;
        }
        return "true".equalsIgnoreCase(flag);
    }

    private static boolean evictDeviceOnLimitEnabled() {
        String flag = firstNonBlank(
                System.getProperty("vrgo.auth.browser.evict.device.on.limit"),
                System.getenv("VRGO_BROWSER_EVICT_DEVICE_ON_LIMIT"),
                "true"
        );
        return !"false".equalsIgnoreCase(flag);
    }

    private static Credentials credentials() {
        String username = firstNonBlank(
                VrgoAuthSecretsLoader.resolveEnvironmentVariable("VRGO_AUTH_USERNAME"),
                VrgoAuthSecretsLoader.resolveEnvironmentVariable("VRGO_TEST_USERNAME"),
                System.getProperty("vrgo.auth.username")
        );
        String password = firstNonBlank(
                VrgoAuthSecretsLoader.resolveEnvironmentVariable("VRGO_AUTH_PASSWORD"),
                VrgoAuthSecretsLoader.resolveEnvironmentVariable("VRGO_TEST_PASSWORD"),
                System.getProperty("vrgo.auth.password")
        );
        if (username == null || password == null) {
            return null;
        }
        return new Credentials(username, password);
    }

    private static BrowserContext newBrowserContext(Browser browser, EnvironmentConfig config, boolean includeViewport) {
        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setUserAgent(config.getProperty(
                        "vrgo.header.user-agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                ))
                .setGeolocation(new Geolocation(3.1390, 101.6869))
                .setPermissions(List.of("geolocation"));
        if (includeViewport) {
            options.setViewportSize(1280, 720);
        }
        WebBasicAuthCredentials basicAuth = resolveWebBasicAuthCredentials();
        if (basicAuth != null) {
            options.setHttpCredentials(new HttpCredentials(basicAuth.username(), basicAuth.password()));
            LOG.info("HTTP basic auth configured for VRGO web portal (user={})", basicAuth.username());
        }
        return browser.newContext(options);
    }

    private static WebBasicAuthCredentials resolveWebBasicAuthCredentials() {
        String username = firstNonBlank(
                System.getProperty("vrgo.web.basic.auth.username"),
                VrgoAuthSecretsLoader.resolveEnvironmentVariable("VRGO_WEB_BASIC_AUTH_USERNAME"),
                System.getenv("VRGO_WEB_BASIC_AUTH_USERNAME")
        );
        String password = firstNonBlank(
                System.getProperty("vrgo.web.basic.auth.password"),
                VrgoAuthSecretsLoader.resolveEnvironmentVariable("VRGO_WEB_BASIC_AUTH_PASSWORD"),
                System.getenv("VRGO_WEB_BASIC_AUTH_PASSWORD")
        );
        if (username == null || password == null) {
            return null;
        }
        return new WebBasicAuthCredentials(username, password);
    }

    private record WebBasicAuthCredentials(String username, String password) {
    }

    private static String extractOrigin(String url) {
        if (url == null || url.isBlank()) {
            return "https://web.vrgo.test.xp.irdeto.com";
        }
        String trimmed = url.strip();
        int schemeEnd = trimmed.indexOf("://");
        if (schemeEnd < 0) {
            return trimmed;
        }
        int pathStart = trimmed.indexOf('/', schemeEnd + 3);
        return pathStart < 0 ? trimmed : trimmed.substring(0, pathStart);
    }

    private static long parseLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
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

    private record Credentials(String username, String password) {
    }
}
