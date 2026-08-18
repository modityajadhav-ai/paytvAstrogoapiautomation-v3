package com.automation.api.auth;

import com.automation.api.config.EnvironmentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Thread-safe guest bearer token manager.
 * <p>
 * Guest sessions do <strong>not</strong> use {@code POST /v1/auth/token} refresh (that is subscriber login only).
 * Guest access JWTs are short-lived (~300s) and are renewed by:
 * <ol>
 *   <li>Reusing a valid cached access token ({@code vrgo-guest-token-cache-<env>.json})</li>
 *   <li>Bootstrap {@code vrgo.search.proxy.guest.bearer.token} / {@code VRGO_GUEST_BEARER_TOKEN}</li>
 *   <li>Headless browser "browse as guest" — captures the Bearer JWT from outgoing API requests</li>
 * </ol>
 */
public final class VrgoGuestTokenHolder {

    private static final Logger LOG = LoggerFactory.getLogger(VrgoGuestTokenHolder.class);
    private static final String GUEST_ACCESS_PROPERTY = "vrgo.search.proxy.guest.bearer.token";

    private static volatile VrgoGuestTokenHolder instance;
    private static final Object GUEST_RECOVERY_LOCK = new Object();

    private final EnvironmentConfig config;
    private final long refreshBufferSeconds;
    private final Path cacheFilePath;

    private String accessToken;

    private VrgoGuestTokenHolder(EnvironmentConfig config) {
        this.config = config;
        this.refreshBufferSeconds = parseLong(
                firstNonBlank(
                        System.getProperty("vrgo.auth.refresh.buffer.seconds"),
                        System.getenv("VRGO_REFRESH_BUFFER_SECONDS"),
                        config.getProperty("vrgo.auth.refresh.buffer.seconds")
                ),
                90L
        );
        this.cacheFilePath = resolveCacheFilePath(config);
        loadCredentials();
    }

    public static void initialize(EnvironmentConfig config) {
        if (config == null || !shouldInitialize(config)) {
            return;
        }
        synchronized (VrgoGuestTokenHolder.class) {
            instance = new VrgoGuestTokenHolder(config);
            if (instance.accessToken != null && !instance.accessToken.isBlank()) {
                LOG.info("VRGO guest token ready (~{}s remaining)",
                        VrgoJwtUtils.secondsRemaining(instance.accessToken));
            } else {
                LOG.info(
                        "VRGO guest token not cached; browser recovery will run only when the first guest test needs it"
                );
            }
        }
    }

    public static VrgoGuestTokenHolder getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "VrgoGuestTokenHolder not initialized. Enable guest browser recovery or set "
                            + "vrgo.search.proxy.guest.bearer.token, then call VrgoGuestTokenHolder.initialize(config)."
            );
        }
        return instance;
    }

    public static boolean isInitialized() {
        return instance != null;
    }

    public static void reset() {
        synchronized (VrgoGuestTokenHolder.class) {
            instance = null;
        }
    }

    public synchronized String getBearerToken() {
        ensureValidAccessToken();
        return accessToken;
    }

    public boolean canProvideBearerToken() {
        if (isAccessTokenValid(accessToken)) {
            return true;
        }
        return isAccessTokenValid(resolveBootstrapGuestAccessToken())
                || VrgoGuestBrowserAuthSupport.isConfigured();
    }

    private static boolean shouldInitialize(EnvironmentConfig config) {
        String vrgoBase = config.getProperty("vrgo.base.url");
        if (vrgoBase == null || vrgoBase.isBlank()) {
            return false;
        }
        return resolveBootstrapGuestAccessToken() != null
                || VrgoGuestBrowserAuthSupport.isConfigured();
    }

    private void loadCredentials() {
        String cachedAccess = loadAccessTokenFromCache();
        if (cachedAccess != null && !VrgoJwtUtils.isExpiringSoon(cachedAccess, refreshBufferSeconds)) {
            accessToken = cachedAccess;
            return;
        }
        String bootstrap = resolveBootstrapGuestAccessToken();
        if (bootstrap != null && !VrgoJwtUtils.isExpiringSoon(bootstrap, refreshBufferSeconds)) {
            accessToken = bootstrap.strip();
        }
    }

    private void ensureValidAccessToken() {
        if (isAccessTokenValid(accessToken)) {
            publishGuestBearerToken(accessToken);
            return;
        }

        String bootstrap = resolveBootstrapGuestAccessToken();
        if (isAccessTokenValid(bootstrap)) {
            applyAccessToken(bootstrap.strip());
            return;
        }

        String cachedAccess = loadAccessTokenFromCache();
        if (isAccessTokenValid(cachedAccess)) {
            applyAccessToken(cachedAccess);
            return;
        }

        if (tryGuestBrowserRecovery()) {
            return;
        }

        invalidateCache();
        throw new IllegalStateException(buildMissingCredentialMessage());
    }

    private boolean tryGuestBrowserRecovery() {
        synchronized (GUEST_RECOVERY_LOCK) {
            if (isAccessTokenValid(accessToken)) {
                return true;
            }

            String bootstrap = resolveBootstrapGuestAccessToken();
            if (isAccessTokenValid(bootstrap)) {
                applyAccessToken(bootstrap.strip());
                return true;
            }

            String cachedAccess = loadAccessTokenFromCache();
            if (isAccessTokenValid(cachedAccess)) {
                applyAccessToken(cachedAccess);
                return true;
            }

            LOG.info("VRGO guest browser recovery starting (single attempt per suite when token is missing or expired)");
            String recovered = VrgoGuestBrowserAuthSupport.recoverGuestAccessToken(config);
            if (recovered == null || recovered.isBlank()) {
                return false;
            }
            applyAccessToken(recovered.strip());
            LOG.info("Recovered VRGO guest access token via headless browser (valid ~{}s)",
                    VrgoJwtUtils.secondsRemaining(accessToken));
            return true;
        }
    }

    private boolean isAccessTokenValid(String token) {
        return token != null && !token.isBlank()
                && !VrgoJwtUtils.isExpiringSoon(token.strip(), refreshBufferSeconds);
    }

    private void applyAccessToken(String newAccessToken) {
        this.accessToken = newAccessToken;
        publishGuestBearerToken(newAccessToken);
        persistCacheFile();
    }

    private void publishGuestBearerToken(String token) {
        System.setProperty(GUEST_ACCESS_PROPERTY, token);
        VrgoGuestAuthHeaderSync.syncGuestSessionHeaders(config, token);
    }

    private void persistCacheFile() {
        try {
            String deviceId = VrgoJwtUtils.extractClaimString(accessToken, "deviceId");
            if (deviceId == null || deviceId.isBlank()) {
                deviceId = config.getProperty("vrgo.search.proxy.guest.header.device_id");
            }
            String profile = VrgoJwtUtils.extractClaimString(accessToken, "profileId");
            if (profile == null || profile.isBlank()) {
                profile = config.getProperty("vrgo.search.proxy.guest.header.profileid");
            }
            VrgoTokenFile.fromTokens(accessToken, null, deviceId, profile).write(cacheFilePath);
            LOG.debug("Updated VRGO guest token cache: {}", cacheFilePath.toAbsolutePath());
        } catch (IOException e) {
            LOG.warn("Could not write VRGO guest token cache {}: {}", cacheFilePath, e.getMessage());
        }
    }

    private void invalidateCache() {
        try {
            Files.deleteIfExists(cacheFilePath);
            LOG.warn("Deleted stale VRGO guest token cache: {}", cacheFilePath.toAbsolutePath());
        } catch (IOException e) {
            LOG.warn("Could not delete VRGO guest token cache {}: {}", cacheFilePath, e.getMessage());
        }
    }

    private String loadAccessTokenFromCache() {
        if (!Files.isRegularFile(cacheFilePath)) {
            return null;
        }
        try {
            VrgoTokenFile file = VrgoTokenFile.read(cacheFilePath);
            if (file == null || file.getAccessToken() == null || file.getAccessToken().isBlank()) {
                return null;
            }
            if (VrgoJwtUtils.isExpiringSoon(file.getAccessToken(), refreshBufferSeconds)) {
                return null;
            }
            LOG.info("Loaded VRGO guest token cache: {}", cacheFilePath.toAbsolutePath());
            return file.getAccessToken().strip();
        } catch (IOException e) {
            LOG.warn("Could not read VRGO guest token cache {}: {}", cacheFilePath, e.getMessage());
            return null;
        }
    }

    private static Path resolveCacheFilePath(EnvironmentConfig config) {
        String configured = firstNonBlank(
                System.getProperty("vrgo.guest.token.cache.file"),
                System.getenv("VRGO_GUEST_TOKEN_CACHE_FILE"),
                config.getProperty("vrgo.guest.token.cache.file"),
                "vrgo-guest-token-cache.json"
        );
        return Paths.get(configured.strip());
    }

    private static String resolveBootstrapGuestAccessToken() {
        String token = firstNonBlank(
                System.getProperty(GUEST_ACCESS_PROPERTY),
                VrgoAuthSecretsLoader.resolveEnvironmentVariable("VRGO_GUEST_BEARER_TOKEN"),
                System.getenv("VRGO_GUEST_BEARER_TOKEN")
        );
        if (token == null || token.isBlank()) {
            return null;
        }
        String normalized = token.strip();
        if (VrgoJwtUtils.isExpiringSoon(normalized, 0L)) {
            return null;
        }
        return normalized;
    }

    private static boolean hasBootstrapAccessCredential() {
        String bootstrap = resolveBootstrapGuestAccessToken();
        return bootstrap != null && !bootstrap.isBlank();
    }

    private static String buildMissingCredentialMessage() {
        com.automation.api.config.Environment env = com.automation.api.config.Environment.current();
        String envName = env.name().toLowerCase();
        Path secrets = VrgoAuthSecretsLoader.resolveLocalSecretsPath();
        if (Files.isRegularFile(secrets)) {
            return "No valid VRGO guest access token for " + envName + ". Guest users do not use /v1/auth/token refresh. "
                    + "Either set vrgo.search.proxy.guest.bearer.token in " + secrets.toAbsolutePath()
                    + " (paste Authorization Bearer from browser guest session), "
                    + "or enable guest browser recovery (vrgo.guest.browser.recovery.enabled=true).";
        }
        return "No valid VRGO guest access token for " + envName + ". "
                + "Set VRGO_GUEST_BEARER_TOKEN or enable guest browser recovery.";
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
}
