package com.automation.api.auth;

import com.automation.api.config.Environment;
import com.automation.api.config.EnvironmentConfig;

/**
 * Standalone browser recovery test — forces refresh failure then headless re-login.
 * <pre>
 *   scripts\verify-browser-recovery.bat
 *   mvnw.cmd -q test-compile exec:java -Dexec.mainClass=com.automation.api.auth.VrgoBrowserRecoveryVerifier -Dexec.classpathScope=test
 * </pre>
 */
public final class VrgoBrowserRecoveryVerifier {

    private VrgoBrowserRecoveryVerifier() {
    }

    public static void main(String[] args) {
        if (System.getProperty("env") == null) {
            System.setProperty("env", Environment.TEST.name().toLowerCase());
        }

        VrgoAuthSecretsLoader.loadLocalSecretsIfPresent();
        EnvironmentConfig config = EnvironmentConfig.load();

        System.out.println("[VrgoBrowserRecoveryVerifier] playwright=" + isPlaywrightOnClasspath());
        System.out.println("[VrgoBrowserRecoveryVerifier] credentials=" + VrgoBrowserAuthRecovery.isConfigured());
        System.out.println("[VrgoBrowserRecoveryVerifier] headed=" + isHeadedMode());

        if (!VrgoBrowserAuthRecovery.isConfigured()) {
            System.err.println("Set vrgo.auth.username and vrgo.auth.password in "
                    + VrgoAuthSecretsLoader.environmentSecretsPath());
            System.exit(1);
        }

        String token = VrgoBrowserAuthSupport.recoverRefreshToken(config);
        if (token == null || token.isBlank()) {
            System.err.println("Browser recovery FAILED — see logs above. Try: scripts\\verify-browser-recovery.bat headed");
            System.exit(2);
        }

        System.out.println("[VrgoBrowserRecoveryVerifier] SUCCESS — captured refresh_token (length=" + token.length() + ")");
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
