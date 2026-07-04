package com.automation.api.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON token cache ({@code access_token}, {@code refresh_token}, {@code device_id}, {@code profile_id}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VrgoTokenFile {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("device_id")
    private String deviceId;

    @JsonProperty("profile_id")
    private String profileId;

    @JsonProperty("seconds_remaining")
    private Double secondsRemaining;

    @JsonProperty("updated_at")
    private String updatedAt;

    public static VrgoTokenFile read(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        return MAPPER.readValue(path.toFile(), VrgoTokenFile.class);
    }

    public void write(Path path) throws IOException {
        if (path == null) {
            return;
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        MAPPER.writeValue(path.toFile(), this);
    }

    public static VrgoTokenFile fromTokens(String accessToken, String refreshToken, String deviceId, String profileId) {
        VrgoTokenFile file = new VrgoTokenFile();
        file.accessToken = accessToken;
        file.refreshToken = refreshToken;
        file.deviceId = deviceId;
        file.profileId = profileId;
        file.secondsRemaining = (double) VrgoJwtUtils.secondsRemaining(accessToken);
        file.updatedAt = Instant.now().toString();
        return file;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("access_token", accessToken);
        map.put("refresh_token", refreshToken);
        map.put("device_id", deviceId);
        map.put("profile_id", profileId);
        map.put("seconds_remaining", VrgoJwtUtils.secondsRemaining(accessToken));
        map.put("updated_at", Instant.now().toString());
        return map;
    }
}
