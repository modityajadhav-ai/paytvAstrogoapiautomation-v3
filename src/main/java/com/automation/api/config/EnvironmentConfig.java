package com.automation.api.config;

import com.automation.api.util.PropertyLoader;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Loads classpath resource {@code environments/<env>.properties} where {@code <env>} is the
 * lower-case name of {@link Environment} ({@code test}, {@code dev}, {@code stage}, {@code stage2}, {@code load}, {@code prod}).
 */
public final class EnvironmentConfig {

    private final String baseUrl;
    private final int connectionTimeoutMs;
    private final int readTimeoutMs;
    private final Properties properties;

    private EnvironmentConfig(String baseUrl, int connectionTimeoutMs, int readTimeoutMs, Properties properties) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "base.url");
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.properties = new Properties();
        this.properties.putAll(properties);
    }

    public static EnvironmentConfig load() {
        Environment env = Environment.current();
        String path = "environments/" + env.name().toLowerCase() + ".properties";
        var props = PropertyLoader.loadClasspath(path);
        String base = props.getProperty("base.url");
        int connect = Integer.parseInt(props.getProperty("http.connect.timeout.ms", "10000"));
        int read = Integer.parseInt(props.getProperty("http.read.timeout.ms", "30000"));
        return new EnvironmentConfig(base, connect, read, props);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    /**
     * Raw property from the active environment file (never use for secrets; prefer env vars).
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    /**
     * Updates a property value in-memory for the duration of the current test run.
     * Does not persist to disk — callers that need persistence must write the file themselves.
     */
    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Keys starting with {@code prefix} become map entries with the prefix stripped from the key.
     */
    public Map<String, String> propertiesWithPrefix(String prefix) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith(prefix)) {
                String value = properties.getProperty(name);
                if (value != null && !value.isBlank()) {
                    map.put(name.substring(prefix.length()), value);
                }
            }
        }
        return map;
    }
}
