package com.automation.api.auth;

import com.automation.api.config.Environment;
import com.automation.api.config.EnvironmentConfig;

/**
 * Standalone guest browser recovery test — headless "browse as guest" and cache capture.
 * <pre>
 *   scripts\verify-guest-browser-recovery.bat
 *   scripts\verify-guest-browser-recovery.bat stage
 *   scripts\verify-guest-browser-recovery.bat stage headed
 * </pre>
 */
public final class VrgoGuestBrowserRecoveryVerifier {

    private VrgoGuestBrowserRecoveryVerifier() {
    }

    public static void main(String[] args) {
        String env = resolveEnvironment(args);
        System.setProperty("env", env);

        VrgoAuthSecretsLoader.loadLocalSecretsIfPresent();
        EnvironmentConfig config = EnvironmentConfig.load();

        System.out.println("[VrgoGuestBrowserRecoveryVerifier] env=" + env);
        System.out.println("[VrgoGuestBrowserRecoveryVerifier] playwright=" + isPlaywrightOnClasspath());
        System.out.println("[VrgoGuestBrowserRecoveryVerifier] guestRecovery="
                + VrgoGuestBrowserAuthSupport.isConfigured());
        System.out.println("[VrgoGuestBrowserRecoveryVerifier] headed=" + isHeadedMode());
        System.out.println("[VrgoGuestBrowserRecoveryVerifier] guestCache="
                + config.getProperty("vrgo.guest.token.cache.file", "vrgo-guest-token-cache.json"));

        if (!VrgoGuestBrowserAuthSupport.isConfigured()) {
            System.err.println("Install Playwright (scripts\\install-playwright.bat) and ensure "
                    + "vrgo.guest.browser.recovery.enabled is not false.");
            System.exit(1);
        }

        VrgoGuestTokenHolder.reset();
        VrgoGuestTokenHolder.initialize(config);

        String cached = null;
        try {
            cached = VrgoGuestTokenHolder.getInstance().getBearerToken();
        } catch (IllegalStateException e) {
            System.err.println("Guest browser recovery FAILED — " + e.getMessage());
            System.err.println("Try: scripts\\verify-guest-browser-recovery.bat " + env + " headed");
            System.exit(2);
        }
        if (cached == null || cached.isBlank()) {
            System.err.println("Guest browser recovery FAILED — holder returned no token.");
            System.exit(2);
        }

        System.out.println("[VrgoGuestBrowserRecoveryVerifier] SUCCESS — guest access token ready (~"
                + VrgoJwtUtils.secondsRemaining(cached) + "s remaining)");
    }

    private static String resolveEnvironment(String[] args) {
        if (args != null) {
            for (String arg : args) {
                if (arg == null || arg.isBlank()) {
                    continue;
                }
                String normalized = arg.strip().toLowerCase();
                if ("headed".equals(normalized)) {
                    continue;
                }
                return normalized;
            }
        }
        if (System.getProperty("env") != null && !System.getProperty("env").isBlank()) {
            return System.getProperty("env").strip().toLowerCase();
        }
        return Environment.TEST.name().toLowerCase();
    }

    private static boolean isHeadedMode() {
        String flag = System.getProperty("vrgo.auth.browser.headed");
        if (flag == null) {
            flag = System.getenv("VRGO_BROWSER_HEADED");
        }
        return "true".equalsIgnoreCase(flag);
    }

    private static boolean isPlaywrightOnClasspath() {
        try {
            Class.forName("com.microsoft.playwright.Playwright");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
