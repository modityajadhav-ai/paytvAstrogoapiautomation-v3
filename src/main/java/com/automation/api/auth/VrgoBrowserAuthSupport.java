package com.automation.api.auth;

import com.automation.api.config.EnvironmentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads Playwright browser recovery only when the optional dependency is present.
 */
final class VrgoBrowserAuthSupport {

    private static final Logger LOG = LoggerFactory.getLogger(VrgoBrowserAuthSupport.class);

    private VrgoBrowserAuthSupport() {
    }

    static String recoverRefreshToken(EnvironmentConfig config) {
        if (!isPlaywrightAvailable()) {
            LOG.warn(
                    "Browser auto-login unavailable. Add Playwright: run scripts\\install-playwright.bat "
                            + "and build with Playwright on classpath."
            );
            return null;
        }
        return VrgoBrowserAuthRecovery.recoverRefreshToken(config);
    }

    static boolean isConfigured() {
        return isPlaywrightAvailable() && VrgoBrowserAuthRecovery.isConfigured();
    }

    private static boolean isPlaywrightAvailable() {
        try {
            Class.forName("com.microsoft.playwright.Playwright");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
