package com.automation.api.auth;

import com.automation.api.config.EnvironmentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads Playwright guest browser recovery when the optional dependency is present.
 */
final class VrgoGuestBrowserAuthSupport {

    private static final Logger LOG = LoggerFactory.getLogger(VrgoGuestBrowserAuthSupport.class);

    private VrgoGuestBrowserAuthSupport() {
    }

    static String recoverGuestAccessToken(EnvironmentConfig config) {
        if (!isPlaywrightAvailable()) {
            LOG.warn(
                    "Guest browser recovery unavailable. Run scripts\\install-playwright.bat "
                            + "and build with Playwright on classpath."
            );
            return null;
        }
        return VrgoBrowserAuthRecovery.recoverGuestAccessToken(config);
    }

    static boolean isConfigured() {
        return isPlaywrightAvailable() && guestBrowserRecoveryEnabled();
    }

    private static boolean guestBrowserRecoveryEnabled() {
        String flag = firstNonBlank(
                System.getProperty("vrgo.guest.browser.recovery.enabled"),
                System.getenv("VRGO_GUEST_BROWSER_RECOVERY_ENABLED"),
                System.getProperty("vrgo.auth.browser.recovery.enabled"),
                System.getenv("VRGO_BROWSER_AUTH_RECOVERY_ENABLED"),
                "true"
        );
        return !"false".equalsIgnoreCase(flag);
    }

    private static boolean isPlaywrightAvailable() {
        try {
            Class.forName("com.microsoft.playwright.Playwright");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
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
}
