package com.automation.api.auth;

import com.automation.api.config.Environment;
import com.automation.api.config.EnvironmentConfig;

/**
 * Standalone Java token manager (replaces Python token_manager).
 * <p>
 * Refresh only (no TestNG):
 * <pre>mvnw.cmd -q exec:java -Dexec.mainClass=com.automation.api.auth.VrgoTokenManager</pre>
 * Check status without refresh:
 * <pre>mvnw.cmd -q exec:java -Dexec.mainClass=com.automation.api.auth.VrgoTokenManager -Dexec.args=--check-only</pre>
 */
public final class VrgoTokenManager {

    private VrgoTokenManager() {
    }

    public static void main(String[] args) {
        boolean checkOnly = args.length > 0 && "--check-only".equals(args[0]);

        if (System.getProperty("env") == null) {
            System.setProperty("env", Environment.TEST.name().toLowerCase());
        }

        VrgoAuthSecretsLoader.loadLocalSecretsIfPresent();
        EnvironmentConfig config = EnvironmentConfig.load();

        VrgoTokenHolder.reset();
        VrgoTokenHolder.initialize(config);

        if (!VrgoTokenHolder.isInitialized()) {
            System.err.println("VRGO auth is not configured for this environment (missing vrgo.base.url).");
            System.exit(1);
        }

        VrgoTokenHolder holder = VrgoTokenHolder.getInstance();

        if (checkOnly) {
            printStatus(holder, "check-only");
            return;
        }

        String token = holder.getBearerToken();
        printStatus(holder, "refreshed");
        System.out.println("access_token=" + token);
    }

    private static void printStatus(VrgoTokenHolder holder, String mode) {
        System.out.println("[VrgoTokenManager] mode=" + mode);
        System.out.println("[VrgoTokenManager] cache=" + holder.getCacheFilePath().toAbsolutePath());
        System.out.println("[VrgoTokenManager] seconds_remaining=" + holder.getSecondsRemaining());
        System.out.println("[VrgoTokenManager] has_refresh_credential=" + holder.hasRefreshCredential());
    }
}
