package com.automation.api.auth;

import com.automation.api.client.VrgoAuthApiClient;
import com.automation.api.config.EnvironmentConfig;
import com.automation.api.model.auth.VrgoTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Thread-safe bearer token manager for long TestNG suites (1000+ tests / 15+ minutes).
 * <p>
 * Credential sources (per active {@code env} profile):
 * <ol>
 *   <li>{@code secrets/vrgo-auth.<env>.local.properties} or {@code VRGO_REFRESH_TOKEN_<ENV>} when updated (wins over stale cache)</li>
 *   <li>Legacy {@code secrets/vrgo-auth.local.properties} when no env-specific file exists</li>
 *   <li>Rotated token in {@code vrgo-token-cache-<env>.json} from a previous successful run</li>
 * </ol>
 * Access tokens refresh automatically ~90s before JWT expiry. Rotated refresh tokens are written back to the cache.
 */
public final class VrgoTokenHolder {

    private static final Logger LOG = LoggerFactory.getLogger(VrgoTokenHolder.class);

    private static volatile VrgoTokenHolder instance;

    private final EnvironmentConfig config;
    private final VrgoAuthApiClient authClient;
    private final long refreshBufferSeconds;
    private final Path cacheFilePath;
    private final String profileId;
    private final String profileType;

    private String accessToken;
    private String refreshToken;

    private VrgoTokenHolder(EnvironmentConfig config) {
        this.config = config;
        this.authClient = new VrgoAuthApiClient(config);
        this.refreshBufferSeconds = parseLong(
                firstNonBlank(
                        System.getProperty("vrgo.auth.refresh.buffer.seconds"),
                        System.getenv("VRGO_REFRESH_BUFFER_SECONDS"),
                        config.getProperty("vrgo.auth.refresh.buffer.seconds")
                ),
                90L
        );
        this.cacheFilePath = resolveCacheFilePath(config);
        this.profileId = resolveProfileId(config);
        this.profileType = firstNonBlank(
                config.getProperty("vrgo.header.profiletype"),
                config.getProperty("vrgo.auth.profile.type"),
                "ADULT"
        );
        loadCredentials();
    }

    public static void initialize(EnvironmentConfig config) {
        if (config == null) {
            return;
        }
        String vrgoBase = config.getProperty("vrgo.base.url");
        if (vrgoBase == null || vrgoBase.isBlank()) {
            return;
        }
        synchronized (VrgoTokenHolder.class) {
            instance = new VrgoTokenHolder(config);
            instance.ensureValidAccessToken();
        }
    }

    public static VrgoTokenHolder getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "VrgoTokenHolder not initialized. Call VrgoTokenHolder.initialize(config) in @BeforeSuite."
            );
        }
        return instance;
    }

    public static boolean isInitialized() {
        return instance != null;
    }

    public static void reset() {
        synchronized (VrgoTokenHolder.class) {
            instance = null;
        }
    }

    public synchronized String getBearerToken() {
        ensureValidAccessToken();
        return accessToken;
    }

    public long getSecondsRemaining() {
        return VrgoJwtUtils.secondsRemaining(accessToken);
    }

    public Path getCacheFilePath() {
        return cacheFilePath;
    }

    public boolean hasRefreshCredential() {
        return refreshToken != null && !refreshToken.isBlank();
    }

    public boolean canProvideBearerToken() {
        if (accessToken != null && !accessToken.isBlank()
                && !VrgoJwtUtils.isExpiringSoon(accessToken, refreshBufferSeconds)) {
            return true;
        }
        return hasRefreshCredential();
    }

    private void loadCredentials() {
        String seedRefresh = resolveSeedRefreshToken();
        String cachedRefresh = loadRefreshTokenFromCache();
        String cachedAccess = loadAccessTokenFromCache();

        boolean forceSeed = "true".equalsIgnoreCase(
                firstNonBlank(
                        System.getProperty("vrgo.auth.force.env.seed"),
                        System.getenv("VRGO_FORCE_ENV_SEED")
                )
        );

        if (forceSeed && seedRefresh != null) {
            refreshToken = seedRefresh;
        } else if (seedRefresh != null && cachedRefresh != null && !seedRefresh.equals(cachedRefresh)) {
            // User updated secrets after a device change — prefer the new seed over stale cache.
            LOG.info("Using refresh token from secrets/env (differs from vrgo-token-cache.json)");
            refreshToken = seedRefresh;
            accessToken = null;
        } else if (cachedRefresh != null) {
            refreshToken = cachedRefresh;
            accessToken = cachedAccess;
        } else {
            refreshToken = seedRefresh;
        }

        String bootstrapAccess = firstNonBlank(
                accessToken,
                System.getProperty("vrgo.bearer.token"),
                System.getenv("VRGO_BEARER_TOKEN")
        );
        if (bootstrapAccess != null && !VrgoJwtUtils.isExpiringSoon(bootstrapAccess, refreshBufferSeconds)) {
            accessToken = bootstrapAccess.strip();
        }
    }

    private static String resolveSeedRefreshToken() {
        return firstNonBlank(
                VrgoAuthSecretsLoader.resolveEnvironmentVariable("VRGO_REFRESH_TOKEN"),
                System.getProperty("vrgo.refresh.token")
        );
    }

    private String loadRefreshTokenFromCache() {
        if (!Files.isRegularFile(cacheFilePath)) {
            return null;
        }
        try {
            VrgoTokenFile file = VrgoTokenFile.read(cacheFilePath);
            if (file == null || file.getRefreshToken() == null || file.getRefreshToken().isBlank()) {
                return null;
            }
            LOG.info("Loaded VRGO token cache: {}", cacheFilePath.toAbsolutePath());
            return file.getRefreshToken().strip();
        } catch (IOException e) {
            LOG.warn("Could not read VRGO token cache {}: {}", cacheFilePath, e.getMessage());
            return null;
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
            return file.getAccessToken().strip();
        } catch (IOException e) {
            return null;
        }
    }

    private void ensureValidAccessToken() {
        if (accessToken != null && !accessToken.isBlank()
                && !VrgoJwtUtils.isExpiringSoon(accessToken, refreshBufferSeconds)) {
            publishBearerToken(accessToken);
            return;
        }
        if (!hasRefreshCredential()) {
            throw new IllegalStateException(buildMissingCredentialMessage());
        }
        refreshAccessToken();
    }

    private void refreshAccessToken() {
        if (tryRefreshWithToken(refreshToken)) {
            return;
        }

        String seedRefresh = resolveSeedRefreshToken();
        if (seedRefresh != null && !seedRefresh.equals(refreshToken) && tryRefreshWithToken(seedRefresh)) {
            LOG.warn("Cache/session refresh failed; recovered using refresh token from secrets/env");
            return;
        }

        if (tryBrowserRecovery()) {
            return;
        }

        invalidateCache();
        String recoveryHint = VrgoBrowserAuthSupport.isConfigured()
                ? "Browser auto-login ran but did not capture a refresh_token. "
                + "Run scripts\\verify-browser-recovery.bat (or set VRGO_BROWSER_HEADED=true to debug)."
                : "Configure browser auto-recovery: vrgo.auth.username + vrgo.auth.password in secrets, "
                + "run scripts\\install-playwright.bat.";
        throw new IllegalStateException(
                "VRGO refresh failed (HTTP 401 Invalid session). Session was revoked (device removed / logout). "
                        + recoveryHint + " Or paste a fresh refresh_token manually."
        );
    }

    private boolean tryBrowserRecovery() {
        String recovered = VrgoBrowserAuthSupport.recoverRefreshToken(config);
        if (recovered == null || recovered.isBlank()) {
            return false;
        }
        this.refreshToken = recovered.strip();
        System.setProperty("vrgo.refresh.token", refreshToken);
        invalidateCache();
        if (tryRefreshWithToken(refreshToken)) {
            LOG.info("Recovered VRGO session via headless browser login");
            return true;
        }
        return false;
    }

    private boolean tryRefreshWithToken(String grantRefresh) {
        if (grantRefresh == null || grantRefresh.isBlank()) {
            return false;
        }
        VrgoTokenResponse refreshResponse = authClient.fetchAccessToken(grantRefresh.strip());
        if (refreshResponse == null) {
            return false;
        }

        String accountToken = refreshResponse.getAccessToken().strip();
        String newRefresh = firstNonBlank(refreshResponse.getRefreshToken(), grantRefresh);

        if (VrgoJwtUtils.hasProfileId(accountToken)) {
            applyTokens(accountToken, newRefresh);
            return true;
        }

        VrgoTokenResponse subjectResponse = authClient.fetchProfileAccessToken(
                accountToken, profileId, profileType.toUpperCase()
        );
        if (subjectResponse == null) {
            return false;
        }
        applyTokens(
                subjectResponse.getAccessToken().strip(),
                firstNonBlank(subjectResponse.getRefreshToken(), newRefresh)
        );
        return true;
    }

    private void invalidateCache() {
        try {
            Files.deleteIfExists(cacheFilePath);
            LOG.warn("Deleted stale VRGO token cache: {}", cacheFilePath.toAbsolutePath());
        } catch (IOException e) {
            LOG.warn("Could not delete VRGO token cache {}: {}", cacheFilePath, e.getMessage());
        }
    }

    private void applyTokens(String newAccessToken, String newRefreshToken) {
        this.accessToken = newAccessToken;
        this.refreshToken = newRefreshToken;
        publishBearerToken(newAccessToken);
        persistCacheFile();
        VrgoAuthSecretsWriter.persistRefreshToken(newRefreshToken);
        LOG.info(
                "VRGO bearer token refreshed; valid for ~{}s; device_id={}",
                VrgoJwtUtils.secondsRemaining(newAccessToken),
                VrgoJwtUtils.extractClaimString(newAccessToken, "deviceId")
        );
    }

    private void publishBearerToken(String token) {
        System.setProperty("vrgo.bearer.token", token);
        VrgoAuthHeaderSync.syncSessionHeaders(config, token);
    }

    private void persistCacheFile() {
        try {
            String deviceId = VrgoJwtUtils.extractClaimString(accessToken, "deviceId");
            if (deviceId == null || deviceId.isBlank()) {
                deviceId = config.getProperty("vrgo.header.device_id");
            }
            String profile = VrgoJwtUtils.extractClaimString(accessToken, "profileId");
            if (profile == null || profile.isBlank()) {
                profile = profileId != null ? profileId : config.getProperty("vrgo.header.profileid");
            }
            VrgoTokenFile.fromTokens(accessToken, refreshToken, deviceId, profile).write(cacheFilePath);
            LOG.debug("Updated VRGO token cache: {}", cacheFilePath.toAbsolutePath());
        } catch (IOException e) {
            LOG.warn("Could not write VRGO token cache {}: {}", cacheFilePath, e.getMessage());
        }
    }

    private static Path resolveCacheFilePath(EnvironmentConfig config) {
        String configured = firstNonBlank(
                System.getProperty("vrgo.token.cache.file"),
                System.getenv("VRGO_TOKEN_CACHE_FILE"),
                config.getProperty("vrgo.token.cache.file"),
                "vrgo-token-cache.json"
        );
        return Paths.get(configured.strip());
    }

    private static String resolveProfileId(EnvironmentConfig config) {
        return firstNonBlank(
                System.getProperty("vrgo.profile.id"),
                System.getenv("VRGO_PROFILE_ID"),
                config.getProperty("vrgo.header.profileid"),
                config.getProperty("vrgo.config.service.operator.profile.id")
        );
    }

    private static String buildMissingCredentialMessage() {
        com.automation.api.config.Environment env = com.automation.api.config.Environment.current();
        String envName = env.name();
        Path secrets = VrgoAuthSecretsLoader.resolveLocalSecretsPath();
        if (java.nio.file.Files.isRegularFile(secrets)) {
            return "No VRGO refresh token for " + envName + ". Edit " + secrets.toAbsolutePath()
                    + " and set vrgo.refresh.token=your_token (same line, no quotes). "
                    + "Or set VRGO_REFRESH_TOKEN_" + envName + " / VRGO_REFRESH_TOKEN env var.";
        }
        return "No VRGO refresh token for " + envName + ". Configure ONE of: "
                + "VRGO_REFRESH_TOKEN_" + envName + " or VRGO_REFRESH_TOKEN (CI), "
                + VrgoAuthSecretsLoader.environmentSecretsPath() + " (local), "
                + "legacy secrets/vrgo-auth.local.properties, "
                + "or the env-specific vrgo-token-cache-<env>.json from a previous successful run.";
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
